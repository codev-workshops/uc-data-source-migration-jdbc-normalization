# Data Source Migration Notes: Legacy CDW → Modern Schema

This document records the decisions, transformation patterns, and caveats for
migrating the loan-service application from the legacy denormalized CDW data
warehouse (`CDW_*` tables, all-`VARCHAR`) to the modern normalized schema
(`borrowers`, `loan_products`, `loan_accounts`, `payments`).

**Primary goal:** every REST endpoint returns JSON that is **byte-for-byte
identical** to the legacy-backed responses. This is enforced by golden-file
regression tests (see [Validation](#validation)).

## Architecture decisions

- **Single H2 instance, two schemas.** Rather than configure a second
  `DataSource`, both the legacy `CDW_*` tables and the modern tables live in the
  same in-memory H2 database. On startup:
  1. `schema-legacy.sql` creates the legacy tables, `data-legacy.sql` seeds them.
  2. `schema-modern.sql` (a copy of `data/modern-schema/modern_tables.sql`)
     creates the empty modern tables.
  3. `DataMigrationService` copies legacy rows → modern tables (see below).

  The service layer then reads **exclusively** from the modern tables.
  Configured in `src/main/resources/application.properties`
  (`spring.sql.init.schema-locations`).

- **Explicit DDL over Hibernate auto-DDL.** `spring.jpa.hibernate.ddl-auto=none`
  is kept and the modern schema is created from SQL so the constraints, foreign
  keys, and indexes defined in `data/modern-schema/modern_tables.sql` are
  preserved verbatim.

- **Modern entities use proper Java types.** `Long` auto-increment IDs
  (`@GeneratedValue(IDENTITY)`), `LocalDate`, `LocalDateTime`, `BigDecimal`,
  `Integer`, `Boolean`. Relationships are modelled with `@ManyToOne`
  (`LoanAccount → Borrower`, `LoanAccount → LoanProduct`, `Payment → LoanAccount`)
  and `@OneToMany` inverse sides.

- **Legacy entities/repositories retained but `@Deprecated`.** They are no longer
  used by `LoanService`; the only remaining consumer is `DataMigrationService`,
  which reads them as the migration source.

## Migration job

`com.workshop.loanservice.migration.DataMigrationService` implements
`ApplicationRunner`. It is:

- **Guarded by a config flag** — `@ConditionalOnProperty("migration.enabled",
  matchIfMissing = true)`. Tests can disable/control it.
- **Idempotent** — it does nothing if any modern table is already populated.
- **FK-ordered** — borrowers → products → loan accounts → payments, building
  `externalId/code/accountNumber → entity` maps to resolve foreign keys.

Row counts migrated: **5 borrowers, 5 loan products, 5 loan accounts, 10 payments**.

### Transformation patterns (per `data/mappings/column_mappings.md`)

| Pattern | Legacy | Modern | Example |
|---------|--------|--------|---------|
| Date parse | `MM/DD/YYYY` string | `LocalDate` / `LocalDateTime` | `"02/15/2019"` → `2019-02-15` |
| Amount parse | string with commas | `BigDecimal` | `"285,000"` → `285000` |
| Integer parse | string | `Integer` | `"745"` → `745` |
| Loan status | `ACT/CLO/DFT/FRB` | `ACTIVE/CLOSED/DEFAULT/FORBEARANCE` | |
| Borrower status | `ACT/INA` | `ACTIVE/INACTIVE` | |
| Product active | `ACT/INA` | `is_active` `true/false` | |
| Payment type | `REG/EXT/PRT/PRE` | `REGULAR/EXTRA/PARTIAL/PREPAYMENT` | |
| Payment status | `PST/REV/NSF/PND` | `POSTED/REVERSED/NSF/PENDING` | |
| Denormalization removal | `CDW_LN_ACCT.BORR_FST_NM` etc. | dropped; use `borrower_id` FK | |
| ID resolution | legacy string IDs | auto-increment `BIGINT` + FK | |

## API invariance — the enum display-mapping caveat

The modern DB stores **UPPERCASE enum values** (`ACTIVE`, `REGULAR`, `POSTED`,
…), but the API has always returned **Title Case display labels**. The rewired
`LoanService` therefore maps the modern enum values *back* to the exact labels
the legacy service produced:

| Modern value | API label |
|--------------|-----------|
| `ACTIVE` / `CLOSED` / `DEFAULT` / `FORBEARANCE` | `Active` / `Closed` / `Default` / `Forbearance` |
| `REGULAR` / `EXTRA` / `PARTIAL` / `PREPAYMENT` | `Regular` / `Extra` / `Partial` / `Prepayment` |
| `POSTED` / `REVERSED` / `NSF` / `PENDING` | `Posted` / `Reversed` / `Non-Sufficient Funds` / `Pending` |

### Other invariance details

- **Derived strings are reproduced in the service, not stored:**
  - `LoanSummaryDto.borrowerName` = `firstName + " " + lastName`
  - `BorrowerDto.fullName` = `firstName [+ " " + middleInitial + "."] + " " + lastName`
    (the middle-initial segment is omitted when null, e.g. borrower `B-10005`)
  - `LoanSummaryDto.propertyAddress` = `address + ", " + city + ", " + state + " " + zip`

- **Dates → strings.** DB `LocalDate` fields (`originationDate`, `paymentDate`)
  are formatted back to `MM/DD/YYYY` strings, since the DTOs expose them as
  `String`.

- **`originalAmount` numeric scale.** Legacy stored whole original amounts as
  strings without decimals (`"285,000"`), so the JSON rendered `285000`
  (scale 0). The modern column is `DECIMAL(12,2)`, which yields scale-2
  (`285000.00`). `LoanService.formatOriginalAmount` strips trailing zeros for
  integral amounts to keep the JSON identical. All other amount fields already
  match because their legacy source strings carried the same number of decimals
  as the modern column scale (e.g. `271432.56`, `142567.90`, rate `4.750`).

- **Ordering.** `findAll()` on the legacy string-PK tables returned rows in
  insertion order. Modern queries order explicitly by the auto-increment `id`
  (`findAllByOrderByIdAsc`, `findByBorrowerExternalIdOrderByIdAsc`) so the list
  order is preserved. Payments keep `OrderByPaymentDateDesc`.

### Intentional schema addition

`PaymentDto.paymentId` exposes the legacy sequence number (e.g.
`"PMT-2025120001"`). The modern `payments` table originally had no column for
it, so an `external_id VARCHAR(20) UNIQUE` column was **added** (both to
`data/modern-schema/modern_tables.sql` and the runtime `schema-modern.sql`) to
preserve the legacy identifier — as anticipated by the mapping note
"*Auto-generated; legacy ID stored if needed*". Without this, `paymentId` would
have changed from `"PMT-2025120001"` to a numeric surrogate key, breaking API
invariance.

## Validation

Golden JSON captured from the **legacy-backed** app before rewiring lives in
`src/test/resources/golden/`. Tests:

- `LoanApiGoldenTest` — `@SpringBootTest` + MockMvc; asserts each endpoint
  (`/api/loans`, `/api/loans/{id}`, `/api/loans/{loanId}/payments`,
  `/api/borrowers`, `/api/borrowers/{id}`) returns bytes identical to the golden
  files (including the null-middle-initial and late-fee edge cases).
- `DataMigrationTest` — verifies row counts, type conversions, FK resolution,
  expanded codes, ordering, and preserved legacy payment IDs.

Each `@SpringBootTest` uses a unique in-memory DB
(`jdbc:h2:mem:loansvc-${random.uuid}`) so the multiple Spring contexts in one
test JVM do not collide on the shared named in-mem database.

Run: `mvn test` (15 tests). Manual check: `mvn spring-boot:run`, then hit the
endpoints and diff against the legacy baseline.

## Build note

`pom.xml` had a malformed `<relativeTo/>` tag under `<parent>` (invalid; rejected
by strict Maven). It was corrected to the intended `<relativePath/>`.
