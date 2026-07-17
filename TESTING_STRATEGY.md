# Testing Strategy — Data-Source Migration

This document describes how the loan-service test suite guards the REST API
contract across the migration from the legacy CDW data source to the modern
normalized schema, and how the newer code (dual data sources, the ETL, the
providers, the feature-flag) is validated. The architecture that is being tested
is described in [`DATA_SOURCE_MIGRATION_NOTES.md`](DATA_SOURCE_MIGRATION_NOTES.md).

The suite has two layers:

1. a **golden-master contract suite** that pins the exact API responses and runs
   the *same* assertions against **both** data sources; and
2. **focused correctness tests** for the migration and the new components, run
   against real H2 databases (no mocking).

## 1. One parameterized contract suite, two data sources

There is **one** set of assertions and **one** set of golden fixtures, driven by a
`dataSource` parameter — so "the same suite runs against both data sources" is
literal, not by convention.

- `ApiContractTest` (`src/test/java/.../contract/`) is a JUnit 5
  `@ParameterizedTest` class; every test is annotated
  `@ValueSource(strings = {"legacy", "modern"})`.
- Both data sources are wired into a **single** `@SpringBootTest` context
  simultaneously (the production dual-datasource design), so there is no longer
  any per-profile context juggling. Each test flips the runtime feature flag with
  `DataSourceSelector.setActive(dataSource)` and then asserts.
- Each contract is verified at **both** levels against the same golden file:
  - **endpoint level** — via `MockMvc` against the running controllers, and
  - **service level** — by re-serializing `LoanService` output with the
    application's `ObjectMapper`.
- **Both parameters are enabled.** `modern` is no longer skipped — the migration
  is complete, so the identical assertions must pass for `modern` too. This is the
  acceptance gate for the migration (Task 4).

### Shared golden fixtures

`src/test/resources/golden/` holds the six fixtures that **are** the contract
(shared across both data-source parameters, never duplicated per source):

| File | Endpoint | Notes |
|------|----------|-------|
| `loans.json` | `GET /api/loans` | 5 loan accounts |
| `loan-LN-2019-00142.json` | `GET /api/loans/{id}` | single loan |
| `payments-LN-2019-00142.json` | `GET /api/loans/{loanId}/payments` | ordering pinned |
| `borrowers.json` | `GET /api/borrowers` | 5 borrowers, `loans: null` |
| `borrower-B-10001.json` | `GET /api/borrowers/{id}` | nested loans; dotted middle initial |
| `borrower-B-10005.json` | `GET /api/borrowers/{id}` | **null** middle initial |

They are captured from the **legacy** data source by `GoldenFileGenerator`
(`@Disabled`; run manually to regenerate). Regeneration should only be needed when
a change intentionally alters an endpoint. Dataset size is pinned to Task 2's
success criteria: **5 borrowers, 5 loan products, 5 loan accounts, 10 payments**.

### Comparison policy: strict, numeric-aware, empty allow-list

`ContractDifferences` compares live output to the golden fixtures with
**`JSONCompareMode.STRICT`** JSONAssert — exact values, exact array ordering, no
missing/extra fields. JSONAssert compares numbers by value, so
`285000` and `285000.00` (the modern `DECIMAL` scale) are correctly treated as
equal; only genuine contract drift fails.

`docs/MIGRATION_TASKS.md` Task 4 Step 4 permits *documented, intentional
differences* between the data sources. `ACCEPTED_DIFFERENCES` is the hook for that:
each entry would relax the strict match for a single JSON path. **It is EMPTY** —
every field must match — but the mechanism exists so a future, justified
difference can be admitted deliberately rather than by loosening the whole suite.

## 2. Correctness tests for the migrated code (no mocks)

These run against real H2 databases and the real Spring beans. The deliberate
choice **not to mock** the repositories/providers is what gives confidence the
migration is actually correct: a wrong transform or a wrong FK resolution shows up
because real legacy data is really migrated and really read back.

- **`DualDataSourceIntegrationTest`** — boots the real dual-datasource context,
  lets the startup ETL populate the modern H2 database, then asserts:
  - modern row counts are `5/5/5/10` and equal the legacy counts;
  - the ETL resolved foreign keys and produced typed values
    (`BigDecimal`, `LocalDate`, expanded status/property);
  - the legacy `PMT_SEQ_NBR` is preserved as `paymentId`, ordered `payment_date DESC`;
  - **both providers produce identical DTO JSON** for every endpoint (legacy vs
    modern parity), using the same strict/numeric JSON policy as the contract suite;
  - `LoanService` routes to whichever provider the feature flag selects;
  - the `/api/admin/datasource` endpoint reports and switches the active source,
    and rejects unknown values with `400`;
  - re-running `migrate()` is idempotent (no duplicate rows).
- **`DataMigrationEdgeCaseTest`** — runs the real ETL over an isolated edge-case
  data set (`data-legacy-edge.sql`) whose rows carry the status/type/property codes
  the production 5/5/5/10 seed never uses (`CLO`/`DFT`/`FRB`, `INA`, `MFR`,
  `EXT`/`PRT`/`PRE`, `REV`/`NSF`/`PND`) plus unknown codes that must pass through
  unchanged. Every code-expansion branch is exercised end to end and legacy/modern
  parity is proven for the alternate codes too.
- **`DataMigrationReferentialIntegrityTest`** — legacy CDW tables have no foreign
  keys, so the ETL must reject a loan that references a missing borrower/product
  and a payment that references a missing loan. Startup migration is disabled
  (`loanservice.migrate-on-startup=false`) so each test seeds a controlled broken
  data set and invokes `migrate()` on demand, asserting it fails loudly instead of
  writing orphaned rows.
- **`DataSourceSelectorTest`** — default selection, case/whitespace-insensitive
  parsing, runtime switching, and rejection of invalid values.
- **`DataSourceAdminControllerTest`** — the feature-flag HTTP endpoint in
  isolation (current / switch / invalid).
- **`PojoAccessorsTest`** — reflection-driven getter/setter round-trip over the
  legacy entities, modern entities, and DTOs (pure state holders).

## 3. Coverage

JaCoCo runs on every build. `mvn verify` enforces a **bundle line-coverage minimum
of 95%** (`jacoco:check`); the build fails below it. The current suite is at
**~99.5%**. The only uncovered lines are defensive `IllegalStateException` guards
in the ETL that are unreachable with valid input — duplicate-business-key
detection (legacy primary keys already prevent duplicates) and the post-migration
row-count mismatch check — kept as defence-in-depth.

Coverage report: `target/site/jacoco/index.html` after any test run.

## How to run

```bash
mvn clean test          # full suite
mvn clean verify        # full suite + JaCoCo 95% gate
mvn test -Dtest=ApiContractTest   # just the contract suite
```

To regenerate golden fixtures, enable `GoldenFileGenerator` and run it:

```bash
mvn test -Dtest=GoldenFileGenerator \
  -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
```

> Note: the repository declares a `./mvnw` wrapper in the blueprint but the
> wrapper script/jar are not committed; if `./mvnw` is unavailable use a local
> Maven 3.9.x (`mvn`).

## Parity items the suite pins (cross-ref: Task 4 Step 4 "documented differences")

These are the parity/contract-stability decisions the suite locks in. They are
described in full in `DATA_SOURCE_MIGRATION_NOTES.md`.

1. **Dates are returned as `MM/DD/YYYY` strings** even though the modern schema
   stores real `DATE`/`TIMESTAMP` columns. `ModernLoanDataProvider` re-serializes
   dates back to the legacy `MM/DD/YYYY` form. The allow-list stays empty (no
   format drift admitted).
2. **`paymentId` equals the legacy `PMT_SEQ_NBR`** (e.g. `PMT-2025120001`). This is
   now preserved by a dedicated `payments.legacy_sequence_number` column populated
   by the ETL (see the migration notes for the rationale and the
   `column_mappings.md` reference). The earlier "no column for it" gap is resolved.
3. **Payment ordering is pinned** to `payment_date DESC` for `LN-2019-00142`:
   `PMT-2025120001` then `PMT-2025110001`. STRICT array comparison enforces it; the
   modern repository reproduces it via
   `findByLoanAccount_AccountNumberOrderByPaymentDateDesc`.
4. **Display-string expansions.** The modern tables store codes expanded to their
   enum form (`ACT`→`ACTIVE`, `REG`→`REGULAR`, `PST`→`POSTED`); both providers
   translate to the human-readable API form (`"Active"`, `"Regular"`, `"Posted"`).
   `property_type` is stored in the full API display form
   (`"Single Family Residence"`, …) so the read path is a pass-through.
5. **`borrowerName` / `fullName` formatting.** `LoanSummaryDto.borrowerName` is
   `first + " " + last`; `BorrowerDto.fullName` inserts a dotted middle initial when
   present and omits it when null (B-10005). Both are pinned by the fixtures.
6. **Numeric formatting.** Amounts are `BigDecimal`; STRICT-but-numeric comparison
   pins their values (`interestRate` → `4.750`), tolerating only representational
   scale differences between the string-typed legacy source and the `DECIMAL`
   modern columns.
