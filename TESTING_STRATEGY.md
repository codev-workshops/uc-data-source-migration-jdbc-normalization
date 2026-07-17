# Testing Strategy — Data-Source Migration Safety Net

This document describes the **golden-master contract suite** that guards the
loan-service REST API while it is migrated from the legacy CDW data source to the
modern normalized schema. It implements **Task 4, Step 1** of
[`docs/MIGRATION_TASKS.md`](docs/MIGRATION_TASKS.md) ("capture the current API
responses as golden files — before migration") and doubles as the acceptance gate
for Tasks 1–3.

> Scope of this iteration: build the safety net and get it **green against the
> legacy data source**. No production code, schema DDL, mapping docs, legacy
> entities/repositories, or `LoanService` behavior were changed. (The only source
> edit outside `src/test` is a one-character `pom.xml` fix — `<relativeTo/>` →
> `<relativePath/>` — without which Maven cannot parse the project.)

## One parameterized suite, two data sources

There is **one** set of assertions and **one** set of golden fixtures, driven by a
`dataSource` parameter — so "the same suite runs against both data sources" is
literal, not by convention.

- `ApiContractTest` (`src/test/java/.../contract/`) is a JUnit 5
  `@ParameterizedTest` class. Every test is annotated
  `@ValueSource(strings = {"legacy", "modern"})`.
- `@SpringBootTest` binds the data source to the active Spring profile, and a
  single context cannot swap profiles mid-test. So each `dataSource` value is
  backed by its **own per-profile Spring context**, booted once and **cached** by
  `DataSourceContexts` (`legacy` → profile `legacy`, `modern` → profile `modern`).
  Each context also runs on its own isolated in-memory H2 database so the contexts
  (and the default-profile `contextLoads` test) don't collide in one JVM.
- `DataSourceContexts.forDataSource(name)` maps the parameter → profile → context,
  which keeps the assertion code fully datasource-agnostic.
- Each contract is verified at **both** levels against the same golden file:
  - **endpoint level** — via `MockMvc` against the running controllers, and
  - **service level** — by re-serializing `LoanService` output with the
    application's `ObjectMapper`.

### The modern parameter is the disabled acceptance gate

The `modern` parameter is **switched off** (via `DISABLED_DATASOURCES` in
`ApiContractTest`, reason: *"enable after data-source migration Tasks 1-3"*), so
the build stays green today. Enabling it (remove `"modern"` from that set) turns
the suite into the migration acceptance gate: after Tasks 1–3 rewire
`LoanService` onto modern entities, the identical assertions must pass for
`modern` too.

## Shared golden fixtures

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
a change intentionally alters an endpoint.

Dataset size is pinned to Task 2's success criteria: **5 borrowers, 5 loan
products, 5 loan accounts, 10 payments**.

## Comparison policy: strict by default, with a documented allow-list

`ContractDifferences` compares live output to the golden fixtures with
**`JSONCompareMode.STRICT`** JSONAssert — exact values, exact array ordering, no
missing/extra fields.

`docs/MIGRATION_TASKS.md` Task 4 Step 4 explicitly permits *documented, intentional
differences* between the data sources (e.g. date-format changes), with a softer
"business-meaningful results" success criterion. The `ACCEPTED_DIFFERENCES`
allow-list is the hook for exactly that: each entry relaxes the strict match for a
single JSON path. **Per the mandatory contract-stability requirement it is EMPTY**
— every field must match byte-for-byte — but the mechanism exists so a future,
justified difference can be admitted deliberately rather than by loosening the
whole suite.

## How to run

```bash
# Whole suite (legacy passes; modern skipped):
./mvnw test

# Just the contract suite:
./mvnw test -Dtest=ApiContractTest
```

- **Legacy** runs today and must pass.
- **Modern** cases are skipped until enabled (see above). The `modern` Spring
  profile is wired via `src/test/resources/application-modern.properties`
  (schema `schema-modern.sql`, data `data-modern.sql`); when the modern
  parameter is enabled the suite exercises it automatically.
- To regenerate golden fixtures, enable `GoldenFileGenerator` and run it, e.g.
  `./mvnw test -Dtest=GoldenFileGenerator -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'`.

> Note: the repository declares a `./mvnw` wrapper in the blueprint but the
> wrapper script/jar are not committed; if `./mvnw` is unavailable use a local
> Maven 3.9.x (`mvn test`).

## Test-scoped data-source profiles

To keep the suite independent of the main `application.properties`, each profile
carries its own init under `src/test/resources/`:

- `application-legacy.properties` → reuses `schema-legacy.sql` + `data-legacy.sql`
  (the existing seed; not duplicated).
- `application-modern.properties` → `schema-modern.sql` (a copy of
  `data/modern-schema/modern_tables.sql`) + `data-modern.sql`.

`data-modern.sql` is the **migrated equivalent** of `data-legacy.sql`: the *same*
5/5/5/10 records with the `data/mappings/column_mappings.md` transformations
applied (type parsing, comma removal, `MM/DD/YYYY` → `DATE`/`TIMESTAMP`, code
expansion, `external_id` preservation, FK resolution). It is **not** an
independent dataset — treat it as the ETL's *expected output*, to be regenerated
by the real migration (Task 2) later. It is not executed today (the modern
parameter is disabled) but it loads cleanly into H2 and is ready for the gate.

## Parity items the suite pins (cross-ref: Task 4 Step 4 "documented differences")

These are the parity/contract-stability decisions this suite locks in. They feed
`DATA_SOURCE_MIGRATION_NOTES.md` (Task 5).

1. **Dates are returned as `MM/DD/YYYY` strings** even though the modern schema
   stores real `DATE`/`TIMESTAMP` columns (`originationDate`, `paymentDate`, …).
   The rewired modern `LoanService` must re-serialize dates back to the legacy
   `MM/DD/YYYY` string form. The allow-list stays empty (no format drift admitted).
2. **`paymentId` must equal the legacy `PMT_SEQ_NBR`** (e.g. `PMT-2025120001`).
   This is elevated to a *preserved value* by the mandatory contract-stability
   rule, beyond what `column_mappings.md` strictly requires (which maps the payment
   PK to an auto-generated `BIGINT`). **Gap:** the modern `payments` table has **no
   column** for the legacy sequence number, so preserving `paymentId` through the
   migration will require a schema/entity change (a `legacy_id`/`external_id`
   column) in a future task. Until then the modern gate will fail this assertion by
   design — which is precisely what the gate is for. `data-modern.sql` records each
   row's legacy `PMT_SEQ_NBR` in a comment.
3. **Payment ordering is pinned** to the current observed order for
   `LN-2019-00142`: `PMT-2025120001` then `PMT-2025110001` (legacy
   `ORDER BY PMT_DT DESC`). STRICT array comparison enforces it; the modern
   repository must reproduce the same ordering (`payment_date DESC`).
4. **Display-string expansions.** `data-modern.sql` stores codes expanded per the
   mapping doc (`ACT`→`ACTIVE`, `REG`→`REGULAR`, `PST`→`POSTED`), but the API
   contract returns human-readable forms (`"Active"`, `"Regular"`, `"Posted"`).
   The rewired modern service must still translate these. `property_type` is stored
   in `data-modern.sql` in the full API display form (`"Single Family Residence"`,
   `"Condominium"`, `"Townhouse"`) — a deliberate, documented choice (the mapping
   doc's terse `"Single Family"` example would break the contract) so the modern
   service can pass it through.
5. **`borrowerName` / `fullName` formatting.** `LoanSummaryDto.borrowerName` is
   `first + " " + last` ("James Mitchell"); `BorrowerDto.fullName` inserts a dotted
   middle initial when present ("James R. Mitchell") and omits it when null
   ("Robert Williams", B-10005). Both are pinned by the fixtures.
6. **Numeric formatting.** Amounts are plain `BigDecimal` numbers with their source
   scale preserved (`interestRate` → `4.750`, `originalAmount` → `285000`,
   `lateFee` → `0.00`). STRICT comparison pins these exactly.
