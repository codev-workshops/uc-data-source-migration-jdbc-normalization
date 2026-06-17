# Migration Implementation Plan

> Detailed, file-level implementation plan for migrating `loan-service` from the
> legacy CDW data source to the modern normalized schema. Companion to
> `MIGRATION_ANALYSIS.md`. **No code changes have been made** — this is planning only.

## How to read this document

The plan is organized around the five tasks in `docs/MIGRATION_TASKS.md`, in the
recommended execution order. For each task:

1. **Files to create / modify** — exact paths and what goes in them.
2. **Why** — the rationale for the change.
3. **Complexity** — Low / Medium / High with a short justification.
4. **Risks** — what could break and why.
5. **Validation** — how to prove the step is correct.

### Guiding constraints (apply to every task)

- **The REST API contract must not change.** Same endpoints, same JSON keys, same
  values (including string formatting). The DTOs (`BorrowerDto`, `LoanSummaryDto`,
  `PaymentDto`) are the contract and should **not** be modified.
- **Keep the app runnable at every step.** Add the modern path alongside the legacy
  path first, validate, then switch — never a big-bang cutover.
- **Use a feature flag** (`loanservice.datasource=legacy|modern`) so legacy and
  modern can run side by side for validation and instant rollback.

### Critical behavior the migration MUST preserve

These were confirmed by reading the current code and seed data; they are the
highest-risk details and are referenced throughout the plan:

1. **Dates are returned as `MM/DD/YYYY` strings.** `LoanSummaryDto.originationDate`
   and `PaymentDto.paymentDate` are `String` and today are passed through verbatim
   from the legacy `MM/DD/YYYY` values (`LoanService` does no date parsing for
   these). Modern entities store `LocalDate`, so the modern translation **must
   format `LocalDate` back to `MM/DD/YYYY`** to keep output identical.
2. **Status/type strings are title-case in the API.** `LoanService` expands codes
   to `"Active"`, `"Closed"`, `"Single Family Residence"`, `"Regular"`, `"Posted"`,
   `"Non-Sufficient Funds"`, etc. The column-mapping doc stores **upper-case**
   canonical values (`ACTIVE`, `POSTED`, …). The API layer must still emit the
   **title-case display strings**, so a code→display mapping is still required even
   after the data is "clean".
3. **Borrower API id = legacy `BORR_ID` (e.g. `B-10001`).** `BorrowerDto.id` and the
   `/api/borrowers/{id}` path use this value. In the modern schema this is
   `borrowers.external_id` (not the new `BIGINT` PK). Lookups by path id must resolve
   via `external_id`.
4. **Payment API id = legacy `PMT_SEQ_NBR` (e.g. `PMT-2025120001`).**
   `PaymentDto.paymentId` uses it. The modern `payments` table uses an
   auto-increment `BIGINT` PK, so the **legacy payment reference must be preserved**
   in a column (e.g. `external_id`) to keep the API value stable.
5. **`borrowerName` on a loan comes from denormalized fields today** (the loan row's
   `BORR_FST_NM`/`BORR_LST_NM`). After normalization it must be derived from the
   borrower FK. Seed data is consistent between the two, so output is unchanged, but
   the source changes.
6. **`productDescription` falls back to the product code** when the product is
   missing. Preserve this fallback.
7. **`fullName` formatting**: `first [+ " " + middleInitial + "."] + " " + last`.
   Middle initial is omitted when null. Must be reproduced exactly.
8. **Null/zero amount handling**: `parseLegacyAmount` returns `BigDecimal.ZERO` for
   null/blank; `BigDecimal` scale comes from the source string (e.g. `"0.00"` →
   scale 2). Modern `DECIMAL` columns must yield equivalent serialized values.

---

## Task 0 (prerequisite): Establish a baseline + build verification

**Files to create / modify**
- `src/test/java/com/workshop/loanservice/ApiGoldenTest.java` *(new)* — a
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` test using `TestRestTemplate` (or
  `MockMvc`) that hits all five endpoints and asserts the response body against
  checked-in golden JSON.
- `src/test/resources/golden/*.json` *(new)* — captured baseline responses for
  `/api/loans`, `/api/loans/{id}`, `/api/borrowers`, `/api/borrowers/{id}`,
  `/api/loans/{loanId}/payments`.

**Why**
We need a regression oracle captured from the *current* legacy behavior before any
change, so every later step can be proven output-identical. This directly enables
Task 4 and de-risks Tasks 2–3.

**Complexity:** Low. Mechanical; only the JSON-comparison harness needs care
(ignore ordering only where the API order is itself unspecified).

**Risks**
- Capturing golden files *after* an accidental change would bake in a regression —
  capture must happen first, on a clean checkout.
- JSON field ordering / `BigDecimal` serialization (`0.00` vs `0`) must be compared
  the same way Jackson serializes at runtime; compare parsed JSON trees, not raw
  strings, to avoid false diffs.

**Validation**
- `./mvnw test` passes against the current legacy code.
- Confirm the app boots: `./mvnw spring-boot:run` and manually curl one endpoint to
  sanity-check the golden capture.

---

## Task 1: Create Modern Schema and Entities

**Files to create**
- `src/main/resources/schema-modern.sql` — H2-compatible DDL derived from
  `data/modern-schema/modern_tables.sql` (4 tables + indexes). Add an `external_id`
  column to `payments` to preserve the legacy `PMT_SEQ_NBR` (see critical behavior
  #4).
- `src/main/java/com/workshop/loanservice/entity/modern/Borrower.java`
- `src/main/java/com/workshop/loanservice/entity/modern/LoanProduct.java`
- `src/main/java/com/workshop/loanservice/entity/modern/LoanAccount.java`
- `src/main/java/com/workshop/loanservice/entity/modern/Payment.java`
  - Proper types: `Long` id (`@GeneratedValue`), `LocalDate`/`LocalDateTime`,
    `BigDecimal`, `Integer`, `Boolean`.
  - Relationships: `LoanAccount` → `@ManyToOne Borrower`, `@ManyToOne LoanProduct`;
    `Payment` → `@ManyToOne LoanAccount`. Add inverse `@OneToMany` only if needed.
- `src/main/java/com/workshop/loanservice/repository/modern/BorrowerRepository.java`
  (+ `findByExternalId`)
- `.../repository/modern/LoanProductRepository.java` (+ `findByCode`)
- `.../repository/modern/LoanAccountRepository.java` (+ `findByAccountNumber`,
  `findByBorrower_ExternalId`)
- `.../repository/modern/PaymentRepository.java`
  (+ `findByLoanAccount_AccountNumberOrderByPaymentDateDesc`)

**Files to modify**
- `src/main/java/com/workshop/loanservice/config/DataSourceConfig.java` *(new, or
  modify `application.properties`)* — see the datasource decision below.
- `src/main/resources/application.properties` — register the modern schema init
  and/or second datasource.

**Datasource approach — two options (pick one):**
- **Option A — Single H2 database, two schemas of tables (simpler).** Keep one
  datasource; add the modern tables alongside the legacy ones via an extra
  `spring.sql.init.schema-locations` entry. Legacy and modern entities live in the
  same persistence unit. *Pros:* minimal config, no multi-datasource wiring.
  *Cons:* less faithful to a "two data sources" story.
- **Option B — Two distinct datasources (faithful to the workshop).** Define a
  primary (`legacyDataSource`) and secondary (`modernDataSource`,
  `jdbc:h2:mem:moderndw`) with separate `@Configuration` classes, each with its own
  `LocalContainerEntityManagerFactoryBean`, `TransactionManager`, and
  `@EnableJpaRepositories(basePackages=…)` scoped by package. *Pros:* matches the
  README architecture diagram; clean separation. *Cons:* more boilerplate; need
  `@Primary` and per-package entity/repository scanning.

> Recommendation: **Option B**, because the README and tasks explicitly frame this
> as legacy vs. modern *data sources*, and it makes the dual-read bonus trivial.

**Why**
The modern schema currently exists only as documentation. The app needs real
runtime tables, typed entities, and repositories before any data can be migrated or
read.

**Complexity:** Medium-High. The entities themselves are Low; the
**multi-datasource JPA configuration (Option B)** is the main source of difficulty
(entity/repository package scoping, `@Primary`, transaction managers).

**Risks**
- Multi-datasource misconfiguration: Spring Data scanning both entity sets into the
  wrong persistence unit, or ambiguous `@Primary` beans → startup failures.
- H2 dialect quirks: `AUTO_INCREMENT` vs `GENERATED BY DEFAULT AS IDENTITY`,
  `BOOLEAN` handling, `CHECK` constraints. Keep DDL H2-compatible.
- `ddl-auto` interaction: must stay `none` so Hibernate doesn't try to manage the
  schema; rely on `schema-modern.sql`.
- Forgetting `external_id` on `payments` would make it impossible to preserve the
  legacy `paymentId` later (critical behavior #4).

**Validation**
- App starts cleanly with both datasources wired; `/h2-console` (or logs) shows the
  modern tables created and empty.
- A tiny repository smoke test (save + `findByExternalId`) passes.
- `./mvnw compile` and the existing `contextLoads()` test still pass.

---

## Task 2: Write the Data Migration

**Files to create**
- `src/main/java/com/workshop/loanservice/migration/MigrationService.java` — reads
  all legacy rows via the `Legacy*Repository` beans, transforms per
  `data/mappings/column_mappings.md`, resolves FKs, and persists modern entities.
- `src/main/java/com/workshop/loanservice/migration/LegacyValueConverters.java` —
  reusable helpers: `parseAmount` (strip commas → `BigDecimal`), `parseDecimal`,
  `parseInteger`, `parseDate` (`MM/DD/YYYY` → `LocalDate`),
  `parseTimestamp`, and code-expansion maps (`ACT→ACTIVE`, product `ACT/INA→boolean`,
  payment type/status). Centralizing avoids duplication with the read path.
- `src/main/java/com/workshop/loanservice/migration/MigrationRunner.java`
  *(optional)* — a `CommandLineRunner`/`ApplicationRunner` guarded by a flag
  (`loanservice.migrate-on-startup=true`) that triggers migration once at boot.
- `src/test/java/com/workshop/loanservice/migration/MigrationServiceTest.java` —
  asserts counts and reconciliation (below).

**Files to modify**
- `src/main/resources/application.properties` — add `loanservice.migrate-on-startup`
  flag (default behavior chosen so tests/runtime are deterministic).

**Migration ordering (FK dependency):** borrowers → loan_products →
loan_accounts (resolve `borrower_id` via `external_id`, `product_id` via `code`) →
payments (resolve `loan_account_id` via `account_number`; store `PMT_SEQ_NBR` as
`external_id`).

**Why**
The modern tables start empty. A deterministic, validated transformation is required
to populate them from the legacy source with correct types and FK relationships
(Task 2 success criteria: 5/5/5/10 rows, FKs correct, amounts reconcile).

**Complexity:** Medium. Transformations are well specified by the mapping doc; the
care points are FK resolution, ordering, and edge-case handling (nulls like
`BORR_MID_INIT`/`address_line2`, blank amounts → consistent scale).

**Risks**
- **FK resolution failures**: a legacy `BORR_ID`/`PROD_CD`/`LN_ACCT_NBR` with no
  match would orphan a row or violate the NOT NULL FK. Need fail-fast + clear error.
- **Type/scale drift**: `BigDecimal` scale differences (e.g. `"0.00"` → `0.00` vs
  `0`) can change serialized JSON later; preserve scale to match golden files.
- **Date parsing**: malformed or unexpected formats; `MM/DD/YYYY` must use a strict
  formatter and a fixed locale.
- **Idempotency**: running migration twice (e.g. on restart) could duplicate rows;
  guard with a "already migrated" check or run once.
- **Status canonicalization choice**: storing `ACTIVE` (mapping doc) vs `Active`
  (current display) — store canonical upper-case and convert at read time (keeps
  data clean and API stable). Decide and document.

**Validation**
- Row counts: `borrowers=5`, `loan_products=5`, `loan_accounts=5`, `payments=10`.
- Reconciliation: `SUM(current_balance)`, `SUM(payments.total_amount)`, etc., match
  the legacy values after comma-stripping.
- Spot-check FK integrity: every `loan_accounts.borrower_id`/`product_id` and
  `payments.loan_account_id` resolves; no nulls.
- `MigrationServiceTest` green.

---

## Task 3: Rewire the Application to the Modern Schema

**Files to create**
- `src/main/java/com/workshop/loanservice/service/ModernLoanService.java`
  *(or refactor — see below)* — builds the **same DTOs** from modern entities,
  applying display formatting (date → `MM/DD/YYYY`, code → title-case strings).
- `src/main/java/com/workshop/loanservice/service/LoanQueryService.java` *(optional
  interface)* — a common interface implemented by both legacy and modern services so
  controllers depend on the abstraction and the flag selects the implementation.

**Files to modify**
- `src/main/java/com/workshop/loanservice/service/LoanService.java` — either keep as
  the legacy implementation behind the flag, or extract the shared
  display-formatting (`expand*`) into a helper reused by both. Do **not** delete yet.
- `src/main/java/com/workshop/loanservice/controller/LoanController.java` and
  `BorrowerController.java` — change the injected type to the interface (if Option
  with `LoanQueryService`); otherwise no change if using `@Primary`/`@Qualifier`.
- `src/main/resources/application.properties` — add
  `loanservice.datasource=legacy|modern` and select the active service via
  `@ConditionalOnProperty` / `@Profile` / a `@Configuration` selector.

**Why**
This is the actual cutover of the read path. Modern entities are already typed, so
the string-parsing disappears, but the DTO-shaping (names, address concatenation,
status display strings, date formatting) must be reproduced exactly.

**Complexity:** Medium. Straightforward mapping, but the **output-fidelity details**
(critical behaviors #1–#8) are easy to get subtly wrong.

**Risks**
- **Date format regression** (#1): returning ISO `2019-02-15` instead of
  `02/15/2019` would break the contract. Must reformat.
- **Status display regression** (#2): emitting `ACTIVE` instead of `Active`.
- **Lookup-by-id regression** (#3, #4): borrower/payment ids must remain the legacy
  string values via `external_id`.
- **Missing-product fallback** (#6) and **null middle initial** (#7) must be
  preserved.
- **Ordering**: `getPaymentsByLoan` orders by payment date desc — modern repository
  query must reproduce the same ordering (and tie-breaking) as today.

**Validation**
- The Task 0 golden tests pass with `loanservice.datasource=modern`.
- Run the suite in **both** modes (`legacy` and `modern`) — both green.
- Manual diff of one borrower-with-loans payload (the richest shape) between modes.

---

## Task 4: Add Validation Tests

**Files to create / modify**
- Extend `ApiGoldenTest` (from Task 0) to run **parameterized over both datasource
  modes**, asserting identical bodies.
- `src/test/java/com/workshop/loanservice/ReconciliationTest.java` *(new)* — asserts
  legacy vs. modern aggregates (counts, sums) match.
- `DATA_SOURCE_MIGRATION_NOTES.md` — record any intentional, documented differences
  (expected: none for values; possibly internal-only like numeric `id`s that aren't
  exposed).

**Why**
Provides objective proof that the modern source produces identical business results,
satisfying the Task 4 success criteria and protecting against future regressions.

**Complexity:** Low-Medium. Mostly harness wiring once Tasks 0/3 exist; parameterizing
across datasource modes is the only fiddly part.

**Risks**
- Hidden non-determinism (map iteration order, unspecified list ordering) producing
  flaky diffs — compare as parsed JSON and sort where order is not contractually
  defined.
- Over-asserting on internal fields not part of the API (don't compare DB ids).

**Validation**
- Golden comparison passes for all five endpoints in both modes.
- Reconciliation test green.
- CI (if added) runs the full suite.

---

## Task 5: Document and Clean Up

**Files to create / modify**
- `DATA_SOURCE_MIGRATION_NOTES.md` *(new)* — decisions (Option A/B, status storage,
  date formatting policy), mapping summary, and any documented diffs.
- `src/main/resources/application.properties` — default
  `loanservice.datasource=modern` and make the modern datasource `@Primary` once
  validated.
- `README.md` — fix the endpoint discrepancy (README says
  `GET /api/payments/loan/{loanId}`; actual is `GET /api/loans/{loanId}/payments`)
  and update the architecture/"current state" wording to reflect the modern default.
- Legacy classes (`entity/Legacy*`, `repository/Legacy*`, `LoanService` legacy
  path) — annotate `@Deprecated` and/or move under a `legacy` package; remove only
  after the modern path is the confirmed default and dual-read is no longer needed.

**Why**
Locks in the migration as the default, records the rationale for future maintainers,
and removes/flags now-dead legacy code.

**Complexity:** Low. Documentation and configuration; deletion is optional and should
be conservative.

**Risks**
- Removing legacy code prematurely eliminates the rollback path and breaks the
  dual-read bonus; prefer `@Deprecated` first.
- Forgetting to flip `@Primary`/default flag would leave the app silently on legacy.

**Validation**
- Fresh clone → `./mvnw spring-boot:run` serves from the modern source by default;
  all golden tests pass; README instructions match actual behavior.

---

## Bonus Tasks (optional, after Task 5)

- **Dual-read feature flag** — already enabled by the `loanservice.datasource` flag
  introduced in Task 3; document and test runtime switching.
  *Complexity: Low* once the interface exists.
- **Reconciliation SQL** — checked-in queries comparing legacy vs. modern aggregates
  for ops sign-off. *Complexity: Low.*
- **Performance comparison** — micro-benchmark typed vs. VARCHAR queries. *Complexity:
  Medium; results are illustrative given the tiny in-memory dataset.*

---

## Summary Table

| Task | Primary new files | Key modified files | Complexity | Top risk |
|---|---|---|---|---|
| 0. Baseline | `ApiGoldenTest`, `golden/*.json` | — | Low | Capturing after an accidental change |
| 1. Modern schema/entities | `schema-modern.sql`, `entity/modern/*`, `repository/modern/*`, `config/DataSourceConfig` | `application.properties` | Med-High | Multi-datasource JPA wiring |
| 2. Migration | `migration/MigrationService`, `LegacyValueConverters`, `MigrationRunner`, test | `application.properties` | Medium | FK resolution + BigDecimal scale drift |
| 3. Rewire | `ModernLoanService`, `LoanQueryService` | controllers, `LoanService`, `application.properties` | Medium | Date/status display fidelity (#1, #2) |
| 4. Validation | `ReconciliationTest`, extend `ApiGoldenTest` | `DATA_SOURCE_MIGRATION_NOTES.md` | Low-Med | Flaky ordering diffs |
| 5. Docs/cleanup | `DATA_SOURCE_MIGRATION_NOTES.md` | `README.md`, `application.properties`, legacy classes | Low | Premature legacy removal |

## Recommended execution order

`Task 0 → Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → (bonus)`

Rationale: capture a baseline first, build the modern target empty, fill it via a
validated migration, switch the read path behind a flag, prove equivalence against
the baseline in both modes, then make modern the default and clean up — every step
keeps the app runnable with a working rollback.
