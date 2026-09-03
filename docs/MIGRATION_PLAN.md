# Phased Migration Plan: Legacy CDW → Normalized Schema

Execution plan for migrating `loan-service` from the legacy `CDW_*` tables to the normalized schema approved in the design documents. Inputs:

- `docs/MIGRATION_ANALYSIS.md` — current state and risks
- `docs/proposed-target-schema.sql` — approved target DDL
- `docs/proposed-column-mappings.md` — field-level transformation rules (T-DATE, T-AMT, T-CODE, T-ID, …) and risk ratings
- `docs/DESIGN_DECISIONS.md` — rationale and open questions Q1–Q11

Guiding rules:

1. **Safety net first.** No migration code is written until golden-file baselines of every current API response exist and pass in CI.
2. **Every phase is a separate PR into `develop`** that is independently reviewable and testable. Each phase below lists its deliverables, verification command, and exit criteria a reviewer can tick off on the PR.
3. **API contract is frozen** for the duration of the migration (Q1/Q2 in `DESIGN_DECISIONS.md` are answered "no change" unless a human decides otherwise — see Phase 0).
4. **Legacy stays untouched** until Phase 7. Both data sources coexist behind a feature flag (Phase 5).

> Tooling note: the repo contains `.mvn/wrapper/maven-wrapper.properties` but not the `mvnw` launcher script, so `./mvnw test` does not currently work. Phase 0 restores it; until then use `mvn test`.

---

## Phase overview and dependency diagram

| Phase | Name | Depends on | Parallelisable with |
|---|---|---|---|
| 0 | Test harness + golden-file baselines | — | — (must land first) |
| 1 | Characterisation tests for legacy translation logic | 0 | 2, 3 |
| 2 | Target schema DDL + reference data as runtime resources | 0 | 1, 3 |
| 3 | Modern JPA entities + repositories | 0 | 1, 2 (entities compile against the DDL *document*; repository tests need Phase 2 merged) |
| 4 | Migration loader (legacy → modern) with quarantine | 2, 3 | — |
| 5 | Modern read path + dual-read feature flag | 3, 4 | — |
| 6 | Reconciliation + golden-file parity on modern source | 4, 5 | — |
| 7 | Cut-over default, legacy deprecation, docs | 6 | — |

```
            ┌──────────────────────────────────────────────┐
            │ Phase 0  Harness + golden baselines (FIRST)  │
            └──────────────┬───────────────┬───────────────┘
                           │               │
        ┌──────────────────┼───────────────┼──────────────────┐
        ▼                  ▼               ▼                  │
  ┌───────────┐     ┌───────────┐    ┌───────────┐            │  ← Phases 1, 2, 3 are
  │ Phase 1   │     │ Phase 2   │    │ Phase 3   │            │    independent: run as
  │ Legacy    │     │ Target    │    │ Modern    │            │    three parallel
  │ char.     │     │ DDL +     │    │ entities +│            │    Devin sessions
  │ tests     │     │ ref data  │    │ repos     │            │
  └─────┬─────┘     └─────┬─────┘    └─────┬─────┘            │
        │                 └────────┬───────┘                  │
        │                          ▼                          │
        │                   ┌───────────┐                     │
        │                   │ Phase 4   │                     │
        │                   │ Loader +  │                     │
        │                   │ quarantine│                     │
        │                   └─────┬─────┘                     │
        │                         ▼                           │
        │                   ┌───────────┐                     │
        │                   │ Phase 5   │                     │
        │                   │ Modern    │                     │
        │                   │ read path │                     │
        │                   │ + flag    │                     │
        │                   └─────┬─────┘                     │
        │                         ▼                           │
        └───────────────────►┌───────────┐                    │
                             │ Phase 6   │ (uses Phase 1 tests│
                             │ Reconcile │  and Phase 0 goldens│
                             │ + parity  │  as the oracle)    │
                             └─────┬─────┘                    │
                                   ▼                          │
                             ┌───────────┐                    │
                             │ Phase 7   │                    │
                             │ Cut-over  │                    │
                             └───────────┘                    │
```

**Parallel sessions:** after Phase 0 merges, Phases 1, 2 and 3 can be started simultaneously as three separate Devin sessions on three branches. Phase 4 onward is strictly sequential.

---

## Phase 0 — Test harness and golden-file baselines

**Goal:** Capture the exact current behaviour of every endpoint against the legacy seed data before any migration code exists.

**Depends on:** nothing. **Blocks:** everything.

**Deliverables**
- Restore the Maven wrapper launcher (`mvnw`, `mvnw.cmd`) matching `.mvn/wrapper/maven-wrapper.properties` (Maven 3.9.6, wrapper 3.2.0) so `./mvnw test` works.
- `src/test/java/com/workshop/loanservice/api/GoldenFileApiTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc`; for each request below, loads the golden JSON from the classpath and asserts the response body is JSON-equal (strict field set, lenient key order).
- Golden files under `src/test/resources/golden/legacy/`:
  - `GET_api_loans.json`
  - `GET_api_loans_LN-2019-00142.json` … one per seeded loan (5 files)
  - `GET_api_loans_LN-2019-00142_payments.json` … one per seeded loan (5 files)
  - `GET_api_borrowers.json`
  - `GET_api_borrowers_B-10001.json` … one per seeded borrower (5 files)
  - `GET_api_loans_UNKNOWN.json` / `GET_api_borrowers_UNKNOWN.json` — capture the current not-found behaviour (status code + body) so it is not accidentally changed.
- A small regeneration switch (system property `-Dgolden.update=true`) that rewrites the files from live responses, documented in the test's Javadoc.
- `.github/workflows/ci.yml` running `./mvnw -B test` on every PR to `develop` (if CI does not already exist).

**Verification command**
```bash
./mvnw -B test
```

**Exit criteria (reviewer checklist)**
- [ ] `./mvnw test` passes on the PR with **zero application code changes** under `src/main/` (diff touches only `mvnw*`, `src/test/**`, `.github/**`).
- [ ] 18 golden files present; each is pretty-printed JSON and matches the live response byte-for-byte after normalisation.
- [ ] Golden test fails if a DTO field is renamed or a value changes (reviewer can verify by temporarily editing one golden file locally).
- [ ] Not-found responses for `/api/loans/NOPE` and `/api/borrowers/NOPE` are captured, including the HTTP status.
- [ ] Human answers recorded in the PR description for **Q1** (date format stays `MM/DD/YYYY`) and **Q2** (URLs keep legacy identifiers) — default "no change" if unanswered.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop`, implement Phase 0 of docs/MIGRATION_PLAN.md ONLY.

Do NOT modify anything under src/main/. Do NOT start any migration work.

1. Restore the Maven wrapper launcher scripts (mvnw, mvnw.cmd) consistent with .mvn/wrapper/maven-wrapper.properties so `./mvnw test` works.
2. Create src/test/java/com/workshop/loanservice/api/GoldenFileApiTest.java (@SpringBootTest + @AutoConfigureMockMvc) that captures and asserts golden JSON responses for every endpoint in LoanController and BorrowerController against the legacy seed data in src/main/resources/data-legacy.sql:
   GET /api/loans, GET /api/loans/{id} for all 5 loans, GET /api/loans/{id}/payments for all 5 loans, GET /api/borrowers, GET /api/borrowers/{id} for all 5 borrowers, plus the not-found behaviour for an unknown loan id and borrower id (assert status + body).
3. Store goldens under src/test/resources/golden/legacy/ using the file naming in the plan. Support -Dgolden.update=true to regenerate them.
4. Add .github/workflows/ci.yml running `./mvnw -B test` on PRs to develop, if no CI exists.
5. Open a PR into develop titled "Phase 0: test harness and golden-file baselines" whose description contains the exit-criteria checklist from the plan, ticked.
```

---

## Phase 1 — Characterisation tests for the legacy translation logic

**Goal:** Pin down the exact semantics of every translation method in `LoanService` (including its bugs) so Phase 5/6 can prove parity or document intentional differences.

**Depends on:** 0. **Parallel with:** 2, 3.

**Deliverables**
- `src/test/java/com/workshop/loanservice/service/LoanServiceTranslationTest.java` — unit tests (Mockito-mocked repositories) covering:
  - `parseLegacyAmount`: `'285,000'`, `'271,432.56'`, `'0.00'`, `''`, `null` (documents the blank→`ZERO` behaviour), `'abc'` (documents the `NumberFormatException`).
  - `parseLegacyDecimal` / `parseLegacyInteger`: happy path, whitespace, non-numeric.
  - `expandStatusCode`, `expandPropertyType`, `expandPaymentType`, `expandPaymentStatus`: every mapped code plus the `default -> code` fallback and `null`.
  - Name / property-address concatenation, including `null` middle initial (`B-10005`).
  - `originationDate` / `paymentDate` are passed through unparsed.
- `src/test/java/com/workshop/loanservice/service/LoanServiceQueryTest.java` — `@DataJpaTest`-backed or full-context tests for `getAllLoans`, `getLoanById`, `getAllBorrowers`, `getBorrowerById`, `getPaymentsByLoan`, including the `RuntimeException` not-found paths and the manual product join.
- `src/test/java/com/workshop/loanservice/repository/LegacyRepositoryTest.java` — `findByBorrowerId`, `findByLoanAccountNumberOrderByPaymentDateDesc` (assert the current *string* sort order).

Because the translation methods are `private`, tests should exercise them through the public service methods with crafted entity fixtures; do **not** change their visibility (no `src/main/` changes).

**Verification command**
```bash
./mvnw -B test
```

**Exit criteria**
- [ ] No changes under `src/main/`.
- [ ] Every `case` branch and every `default` branch in the four `expand*` methods is covered (reviewer greps the test for each code literal).
- [ ] A test named to the effect of `blankAmountCurrentlyBecomesZero` exists and passes — this is the documented bug, not an assertion of correct behaviour.
- [ ] Not-found `RuntimeException` paths for `getLoanById` and `getBorrowerById` are asserted.
- [ ] Phase 0 golden tests still pass.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop`, implement Phase 1 of docs/MIGRATION_PLAN.md ONLY.

Do NOT modify anything under src/main/ (do not change method visibility in LoanService).

Write characterisation tests that pin the CURRENT behaviour of src/main/java/com/workshop/loanservice/service/LoanService.java, including known bugs listed in docs/MIGRATION_ANALYSIS.md section 4:
- src/test/java/com/workshop/loanservice/service/LoanServiceTranslationTest.java — via public methods with crafted Legacy* entity fixtures, cover parseLegacyAmount (incl. blank -> BigDecimal.ZERO and 'abc' -> NumberFormatException), parseLegacyDecimal, parseLegacyInteger, all four expand* mappers including default fallback and null, name/address concatenation incl. null middle initial, and that originationDate/paymentDate are passed through unparsed.
- src/test/java/com/workshop/loanservice/service/LoanServiceQueryTest.java — getAllLoans, getLoanById, getAllBorrowers, getBorrowerById, getPaymentsByLoan incl. not-found RuntimeException paths.
- src/test/java/com/workshop/loanservice/repository/LegacyRepositoryTest.java — findByBorrowerId and findByLoanAccountNumberOrderByPaymentDateDesc (assert current string ordering).
Run ./mvnw -B test. Open a PR into develop titled "Phase 1: characterisation tests for legacy translation logic" with the plan's exit-criteria checklist ticked.
```

---

## Phase 2 — Target schema DDL and reference data as runtime resources

**Goal:** Make the approved DDL executable by the application against H2 (and verified by a test), with the reference tables seeded.

**Depends on:** 0. **Parallel with:** 1, 3.

**Deliverables**
- `src/main/resources/schema-modern.sql` — the DDL from `docs/proposed-target-schema.sql`, adjusted only as needed for H2 compatibility (identity syntax, CHECK expressions). Any deviation from the design doc must be listed in the PR description.
- `src/main/resources/data-modern-reference.sql` — the reference-table INSERTs (`loan_status`, `property_type`, `payment_type`, `payment_status`, `employment_status`, `product_type`, `rate_type`, `borrower_status`, `borrower_record_type`).
- `src/test/java/com/workshop/loanservice/schema/ModernSchemaTest.java` — spins up an isolated H2 datasource (`jdbc:h2:mem:modern-test`), runs both scripts, and asserts: all 6 core tables + 9 reference tables exist; FK constraints exist for `loan_account.borrower_id/product_id/property_id` and `payment.loan_account_id`; an INSERT with an unknown `status_code` fails; an INSERT with `credit_score = 900` fails; a `payment` whose split ≠ total fails.
- Scripts are **not yet** wired into `application.properties` (the app still boots against legacy only).

**Verification command**
```bash
./mvnw -B test -Dtest=ModernSchemaTest
./mvnw -B test
```

**Exit criteria**
- [ ] `schema-modern.sql` diff against `docs/proposed-target-schema.sql` is limited to dialect adjustments, each listed in the PR.
- [ ] `ModernSchemaTest` proves FK, CHECK and reference-table enforcement (negative-path inserts fail).
- [ ] `application.properties` unchanged; app still starts against legacy; Phase 0 goldens pass.
- [ ] Reference data covers every code that appears in `src/main/resources/data-legacy.sql` (`ACT`, `PRI`, `EMPLOYED`, `SELF-EMP`→`SELF_EMPLOYED`, `RETIRED`, `FXD/ARM/FHA/VA`, `FIXED/VARIABLE`, `SFR/CND/TWN`, `REG`, `PST`, and the remaining designed codes). Open question **Q6** (full code sets) is either answered in the PR or left as a TODO comment in the script.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop`, implement Phase 2 of docs/MIGRATION_PLAN.md ONLY.

Do NOT change application.properties, LoanService, controllers, or legacy entities. Do NOT read data/modern-schema/ or data/mappings/ — the approved design is docs/proposed-target-schema.sql.

1. Create src/main/resources/schema-modern.sql from docs/proposed-target-schema.sql, adjusting only what H2 (version from pom.xml) requires; list every adjustment in the PR description.
2. Create src/main/resources/data-modern-reference.sql with the reference-table seed rows from the design doc.
3. Create src/test/java/com/workshop/loanservice/schema/ModernSchemaTest.java that runs both scripts against an isolated in-memory H2 and asserts table existence, FK enforcement, CHECK enforcement (credit_score 900 rejected, unknown status code rejected, payment split != total rejected).
4. Run ./mvnw -B test. Open a PR into develop titled "Phase 2: modern schema DDL and reference data" with the plan's exit-criteria checklist ticked.
```

---

## Phase 3 — Modern JPA entities and repositories

**Goal:** Typed JPA model for the target schema, in a new package, with no changes to the legacy model or service.

**Depends on:** 0 (and Phase 2 merged before repository tests can run against the real DDL). **Parallel with:** 1, 2.

**Deliverables**
- Package `com.workshop.loanservice.modern.entity`: `Address`, `Borrower`, `LoanProduct`, `Property`, `LoanAccount`, `Payment`, plus reference entities `LoanStatus`, `PropertyType`, `PaymentType`, `PaymentStatus`, `EmploymentStatus`, `ProductType`, `RateType`, `BorrowerStatus`, `BorrowerRecordType`. Field types follow the DDL exactly: `Long` ids, `LocalDate`, `LocalDateTime`, `BigDecimal`, `Short`/`Integer`, `Boolean`. Relationships as `@ManyToOne(fetch = LAZY)` with `@JoinColumn` matching the FK columns.
- Package `com.workshop.loanservice.modern.repository`: `BorrowerRepository` (`findByLegacyBorrowerId`), `LoanProductRepository` (`findByProductCode`), `LoanAccountRepository` (`findByAccountNumber`, `findByBorrowerId`, `findAllWithBorrowerAndProduct()` using `JOIN FETCH`), `PaymentRepository` (`findByLoanAccountIdOrderByPaymentDateDesc`, `findByLegacyPaymentId`), `PropertyRepository`, `AddressRepository`.
- `src/test/java/com/workshop/loanservice/modern/ModernEntityMappingTest.java` — `@DataJpaTest` against `schema-modern.sql` + `data-modern-reference.sql` (from Phase 2): persists one full graph (address → borrower → product → property → loan → payment), reads it back, asserts types and relationships; asserts `findByLoanAccountIdOrderByPaymentDateDesc` sorts by real dates.
- `spring.jpa.hibernate.ddl-auto` stays `none`; Hibernate schema validation (`validate`) is used **only inside the test** to prove entities match the DDL.

**Verification command**
```bash
./mvnw -B test -Dtest=ModernEntityMappingTest
./mvnw -B test
```

**Exit criteria**
- [ ] No changes to `LoanService`, controllers, DTOs, legacy entities/repositories or `application.properties`.
- [ ] Every entity field type matches `schema-modern.sql`; `ModernEntityMappingTest` runs with `hibernate.hbm2ddl.auto=validate` and passes.
- [ ] No `String` field holds a date, amount, or count in the modern entities (reviewer greps for `private String` and checks each).
- [ ] Repository finders listed above exist and are covered.
- [ ] Phase 0 goldens pass.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop` (Phase 2 must already be merged), implement Phase 3 of docs/MIGRATION_PLAN.md ONLY.

Do NOT modify LoanService, controllers, DTOs, legacy entities/repositories, or application.properties. Do NOT read data/modern-schema/ or data/mappings/.

1. Create JPA entities in com.workshop.loanservice.modern.entity for every table in src/main/resources/schema-modern.sql (core + reference tables), with Java types matching the DDL exactly (Long, LocalDate, LocalDateTime, BigDecimal, Short/Integer, Boolean) and @ManyToOne(LAZY) relationships on the FK columns.
2. Create Spring Data repositories in com.workshop.loanservice.modern.repository with the finders listed in the plan (findByLegacyBorrowerId, findByProductCode, findByAccountNumber, findByBorrowerId, findAllWithBorrowerAndProduct with JOIN FETCH, findByLoanAccountIdOrderByPaymentDateDesc, findByLegacyPaymentId).
3. Create src/test/java/com/workshop/loanservice/modern/ModernEntityMappingTest.java (@DataJpaTest, hibernate.hbm2ddl.auto=validate, schema from schema-modern.sql + data-modern-reference.sql) that persists and reads back a full graph and checks date ordering of payments.
4. Run ./mvnw -B test. Open a PR into develop titled "Phase 3: modern JPA entities and repositories" with the plan's exit-criteria checklist ticked.
```

---

## Phase 4 — Migration loader with quarantine

**Goal:** Implement the legacy → modern load exactly as specified in `docs/proposed-column-mappings.md`, with strict parsing and a quarantine table per `DESIGN_DECISIONS.md` D6.

**Depends on:** 2, 3.

**Deliverables**
- `src/main/resources/schema-modern.sql` gains a `migration_quarantine` table (`id`, `source_table`, `source_key`, `source_row` (text/JSON), `reason_code`, `field`, `created_at`).
- Package `com.workshop.loanservice.migration`:
  - `LegacyValueParser` — pure, stateless implementations of the rule IDs: `parseDate` (T-DATE, strict `MM/dd/yyyy`), `parseTimestamp` (T-TS), `parseAmount` (T-AMT: strip `,`/`$`, scale 2 HALF_UP, blank → `null`), `parseDecimal`, `parseInteger`, `normaliseCode` (T-CODE, incl. `SELF-EMP`→`SELF_EMPLOYED`), `expirySentinelToNull` (T-SENTINEL). Each throws a typed `MalformedValueException(field, rawValue, reasonCode)`.
  - `LegacyToModernMigrator` — orchestrates load in FK order (reference → address → borrower → loan_product → property → loan_account → payment); dedups `address` on exact five-field match (T-ADDR); resolves IDs via the `legacy_*` / business-key columns (T-ID); relocates `BORR_SSN_LST4` to `borrower.ssn_last4`; drops `CDW_LN_ACCT.BORR_FST_NM/BORR_LST_NM` **after** writing a divergence report; writes any rejected row to `migration_quarantine` and continues; returns a `MigrationReport` (counts per table: read, loaded, quarantined; divergence list).
  - `MigrationRunner` — `ApplicationRunner` gated by property `migration.run-on-startup=false` (default off) so the app is unaffected.
- Tests:
  - `LegacyValueParserTest` — every rule with good, blank, malformed inputs; asserts blank amount → `null` (not zero).
  - `LegacyToModernMigratorTest` — runs the migrator against the real legacy seed → modern schema in one H2 instance with two schemas (or two datasources): asserts 5/5/5/5/10 rows loaded, 0 quarantined, 0 name divergences, exactly 5 `address` rows (property = mailing address for all seed loans), all FKs resolved, `PROD_EXP_DT '12/31/2099'` → `NULL`; then a second scenario with injected bad rows (orphan loan, `'abc'` balance, unknown status code, `13/40/2025` date) asserting each lands in quarantine with the right `reason_code` and the good rows still load.

**Verification command**
```bash
./mvnw -B test -Dtest='LegacyValueParserTest,LegacyToModernMigratorTest'
./mvnw -B test
```

**Exit criteria**
- [ ] Every rule ID in `docs/proposed-column-mappings.md` has a corresponding method in `LegacyValueParser` and at least one test (reviewer cross-checks the rule table).
- [ ] Seed-data load reports 5 borrowers, 5 products, 5 properties, 5 loans, 10 payments, 5 addresses, 0 quarantined.
- [ ] Malformed-row scenario proves row-level quarantine (not fail-fast, not coercion).
- [ ] `migration.run-on-startup` defaults to `false`; `application.properties` behaviour for the API is unchanged; Phase 0 goldens pass.
- [ ] `LoanService`, controllers and DTOs untouched.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop` (Phases 2 and 3 merged), implement Phase 4 of docs/MIGRATION_PLAN.md ONLY.

Do NOT modify LoanService, controllers, DTOs, or the legacy entities/repositories. Do NOT read data/modern-schema/ or data/mappings/; the spec is docs/proposed-column-mappings.md and docs/DESIGN_DECISIONS.md (D2, D4, D5, D6).

1. Add a migration_quarantine table to src/main/resources/schema-modern.sql.
2. In package com.workshop.loanservice.migration implement LegacyValueParser (one method per rule ID: T-DATE strict MM/dd/yyyy, T-TS, T-AMT strip commas/$ scale 2 HALF_UP with blank -> null, T-DEC, T-INT, T-CODE incl. SELF-EMP -> SELF_EMPLOYED, T-SENTINEL 12/31/2099 -> null), LegacyToModernMigrator (FK load order, address dedup, ID resolution, SSN last-4 relocation, name-divergence report, quarantine-and-continue, MigrationReport), and MigrationRunner gated by migration.run-on-startup=false.
3. Tests: LegacyValueParserTest for every rule with good/blank/malformed inputs; LegacyToModernMigratorTest loading the real legacy seed (expect 5/5/5/5/10 rows, 5 addresses, 0 quarantined) and a scenario with injected bad rows (orphan loan, 'abc' balance, unknown code, 13/40/2025) asserting quarantine reason codes.
4. Run ./mvnw -B test. Open a PR into develop titled "Phase 4: legacy-to-modern migration loader with quarantine" with the plan's exit-criteria checklist ticked and the MigrationReport output pasted.
```

---

## Phase 5 — Modern read path behind a dual-read feature flag

**Goal:** Serve every endpoint from the modern schema, selectable at runtime, while the legacy path remains the default.

**Depends on:** 3, 4.

**Deliverables**
- Extract interface `com.workshop.loanservice.service.LoanQueryService` with the five public methods of today's `LoanService` (same DTO return types).
- Rename/adapt the existing class to `LegacyLoanService implements LoanQueryService` (logic unchanged) and add `ModernLoanService implements LoanQueryService` reading from the Phase 3 repositories. The modern service formats `LocalDate` → `MM/dd/yyyy` strings for `originationDate`/`paymentDate` to preserve the frozen contract (per Q1 default), and builds `borrowerName` / `propertyAddress` from the joined entities.
- `DataSourceRouting` configuration: property `loan-service.data-source=legacy|modern|shadow` (default `legacy`).
  - `legacy` — only `LegacyLoanService` is invoked.
  - `modern` — only `ModernLoanService`.
  - `shadow` — legacy result is returned to the caller; modern is invoked too and any JSON difference is logged at WARN with endpoint + id (never affects the response). This is the dual-read mode.
- Controllers depend on `LoanQueryService`, resolved by a `@Bean` factory based on the property; controllers are otherwise unchanged.
- Startup wiring for the modern schema: `spring.sql.init.schema-locations` gains `classpath:schema-modern.sql`, data locations gain `classpath:data-modern-reference.sql`; `migration.run-on-startup=true` **only in the test profile** so the modern tables are populated for parity tests.
- Tests: `DataSourceRoutingTest` (flag selects the right bean; unknown value fails fast), `ShadowReadTest` (a deliberately diverging stub logs a diff and still returns legacy), `ModernLoanServiceTest` (each endpoint against migrated seed data).

**Verification command**
```bash
./mvnw -B test
./mvnw -B test -Dspring-boot.run.arguments=--loan-service.data-source=modern   # or via @TestPropertySource in GoldenFileApiTest
```

**Exit criteria**
- [ ] With the default flag (`legacy`) all Phase 0 goldens and Phase 1 characterisation tests pass **unchanged**.
- [ ] With `loan-service.data-source=modern`, the app boots and all endpoints return HTTP 200 (parity is proved in Phase 6, not here).
- [ ] `shadow` mode never changes the HTTP response; divergence is logged, covered by `ShadowReadTest`.
- [ ] No `parseLegacy*` / `expand*` string-translation code exists in `ModernLoanService`.
- [ ] Controllers' request mappings and DTO classes are byte-identical to `develop`.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop` (Phases 3 and 4 merged), implement Phase 5 of docs/MIGRATION_PLAN.md ONLY.

Keep the REST contract frozen: no changes to DTO classes or controller mappings; dates must still be returned as MM/dd/yyyy strings.

1. Extract interface LoanQueryService from src/main/java/com/workshop/loanservice/service/LoanService.java; rename the existing implementation to LegacyLoanService without changing its logic.
2. Implement ModernLoanService using the com.workshop.loanservice.modern.repository repositories (no string parsing or expand* code; labels come from reference entities; format LocalDate -> MM/dd/yyyy).
3. Add DataSourceRouting config keyed on property loan-service.data-source = legacy | modern | shadow (default legacy). shadow returns the legacy result and logs any JSON diff vs modern at WARN.
4. Wire schema-modern.sql and data-modern-reference.sql into spring.sql.init.*; enable migration.run-on-startup=true only in the test profile.
5. Tests: DataSourceRoutingTest, ShadowReadTest, ModernLoanServiceTest. Confirm the Phase 0 golden tests and Phase 1 tests pass unchanged with the default flag.
6. Run ./mvnw -B test. Open a PR into develop titled "Phase 5: modern read path behind dual-read feature flag" with the plan's exit-criteria checklist ticked.
```

---

## Phase 6 — Reconciliation and golden-file parity on the modern source

**Goal:** Prove, with the Phase 0 oracle, that the modern source produces identical API responses, and reconcile the data itself.

**Depends on:** 4, 5. Uses Phase 1 tests as a secondary oracle.

**Deliverables**
- `GoldenFileApiTest` parameterised to run twice: `@TestPropertySource(loan-service.data-source=legacy)` and `=modern`, both asserting against `src/test/resources/golden/legacy/`. Any intentional difference is expressed as an explicit, reviewed `JsonCompare` exclusion with a comment linking to the design decision — not by regenerating goldens.
- `ShadowParityTest` — boots in `shadow` mode, hits every endpoint, asserts **zero** WARN divergence logs.
- `src/test/resources/reconciliation/*.sql` + `ReconciliationQueryTest` — SQL that compares legacy vs modern for: row counts per table; sum of `LN_CURR_BAL` vs `SUM(current_balance)`; sum of `PMT_AMT` vs `SUM(total_amount)`; every `LN_ACCT_NBR` has a `loan_account`; every `PMT_SEQ_NBR` has a `payment`; per-loan payment counts. Test asserts all comparisons are equal.
- `docs/PARITY_REPORT.md` — generated table of endpoints × (legacy, modern, match), the reconciliation query results, and a list of any accepted differences with their justification.

**Verification command**
```bash
./mvnw -B test
```

**Exit criteria**
- [ ] All 18 goldens pass under `loan-service.data-source=modern` with **zero** exclusions, or every exclusion is documented in `docs/PARITY_REPORT.md` and approved by a human reviewer in the PR.
- [ ] `ShadowParityTest` reports zero divergences.
- [ ] Reconciliation: counts and sums equal for all four table pairs.
- [ ] Not-found behaviour for unknown ids is identical between sources (status + body).
- [ ] Phase 1 characterisation tests still pass against `LegacyLoanService` (legacy behaviour has not drifted).

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop` (Phases 4 and 5 merged), implement Phase 6 of docs/MIGRATION_PLAN.md ONLY.

Do NOT regenerate or edit files under src/test/resources/golden/legacy/. Do NOT change DTOs or controllers.

1. Parameterise src/test/java/com/workshop/loanservice/api/GoldenFileApiTest.java to run under both loan-service.data-source=legacy and =modern against the same goldens. If a difference is intentional, add an explicit, commented exclusion referencing docs/DESIGN_DECISIONS.md — do not change the goldens.
2. Add ShadowParityTest (shadow mode, all endpoints, assert zero divergence WARN logs).
3. Add reconciliation SQL under src/test/resources/reconciliation/ and ReconciliationQueryTest comparing legacy vs modern counts, balance sums, payment sums and orphan checks.
4. Write docs/PARITY_REPORT.md with endpoint parity results, reconciliation results, and any accepted differences.
5. Run ./mvnw -B test. Open a PR into develop titled "Phase 6: modern-source parity and reconciliation" with the plan's exit-criteria checklist ticked and any exclusions called out for human approval.
```

---

## Phase 7 — Cut-over, legacy deprecation, documentation

**Goal:** Make the modern source the default and retire the legacy read path safely.

**Depends on:** 6.

**Deliverables**
- `application.properties`: `loan-service.data-source=modern` becomes the default; legacy remains selectable.
- `@Deprecated` on `LegacyLoanService`, the four `Legacy*` entities and repositories, with Javadoc pointing to the removal criteria below. **Do not delete them in this phase.**
- `docs/DATA_SOURCE_MIGRATION_NOTES.md` — decisions taken during implementation, deviations from the design docs, how to run the migrator, how to flip the flag, and the removal criteria for legacy code (e.g. "N releases with zero shadow divergences in production").
- `docs/MIGRATION_TASKS.md` updated to reflect completion / link to this plan.
- CI runs the golden suite in **both** modes so a regression in either path is caught until legacy is removed.

**Verification command**
```bash
./mvnw -B test
./mvnw -B spring-boot:run   # boot with defaults; curl each endpoint and diff against goldens
```

**Exit criteria**
- [ ] Default boot serves from modern; all goldens pass with defaults.
- [ ] Setting `loan-service.data-source=legacy` still boots and passes goldens (rollback path proven).
- [ ] Legacy classes are `@Deprecated`, not deleted.
- [ ] `docs/DATA_SOURCE_MIGRATION_NOTES.md` exists and answers: how to run the load, how to roll back, what remains before legacy removal.
- [ ] Open questions Q3–Q11 from `docs/DESIGN_DECISIONS.md` each have a recorded answer or an owner.

**Ready-to-paste Devin prompt**
```
In repo codev-workshops/uc-data-source-migration-jdbc-normalization, branch off `develop` (Phase 6 merged), implement Phase 7 of docs/MIGRATION_PLAN.md ONLY.

1. Change the default in src/main/resources/application.properties to loan-service.data-source=modern; keep legacy selectable.
2. Mark LegacyLoanService and all Legacy* entities/repositories @Deprecated with Javadoc pointing to the removal criteria; do NOT delete them.
3. Write docs/DATA_SOURCE_MIGRATION_NOTES.md (implementation decisions, deviations from docs/proposed-*.md, how to run the migrator, how to flip/roll back the flag, legacy-removal criteria) and update docs/MIGRATION_TASKS.md.
4. Make CI run the golden suite in both legacy and modern modes.
5. Run ./mvnw -B test in both modes. Open a PR into develop titled "Phase 7: cut over to modern data source" with the plan's exit-criteria checklist ticked and answers/owners for design open questions Q3–Q11.
```

---

## Rollback and risk

### What the dual-read flag protects against

`loan-service.data-source` (Phase 5) is the single control point for the migration's blast radius.

| Failure mode | Without the flag | With the flag |
|---|---|---|
| Modern data has a load defect not caught by tests (e.g. an amount off by rounding for a production row) | Wrong financial figures served to clients until a hotfix deploy | `shadow` mode logs the divergence while clients still receive the legacy answer; fix the loader, re-run, re-verify, then flip |
| Modern query path has a bug (missing join, wrong sort) | 500s / wrong ordering in production | Flip to `legacy` at runtime — no code change, no redeploy if the property is externalised |
| Golden tests were incomplete (an endpoint/edge case not captured in Phase 0) | Silent contract break | `shadow` mode surfaces the divergence on real traffic before anyone depends on the modern path |
| Reference-table gap (a production code not in `data-modern-reference.sql`, cf. **Q6**) | Rows quarantined → loans missing from listings | Legacy path still serves the row; quarantine report identifies the missing code |
| Performance regression from the normalised joins | Latency spike on `/api/loans` | Measure in `shadow` mode (both paths execute) before committing to `modern` |

### When to flip

1. **`legacy` → `shadow`**: as soon as Phase 5 merges and the loader has run in the target environment. Stay here until Phase 6 exit criteria are met *and* shadow logs show zero divergence over a representative traffic window (for the workshop dataset, one pass over all 18 golden requests; for production, agree a window with the business — see **Q9**).
2. **`shadow` → `modern`**: Phase 7. Requires: Phase 6 PR merged with zero unapproved exclusions; reconciliation sums equal; quarantine table empty or every row dispositioned; a named person has signed off on Q4 (name divergence authority) and Q10 (late-fee arithmetic).
3. **`modern` → `legacy` (rollback)**: any of — a WARN-level divergence appears in production logs after cut-over; a client reports a value or ordering change; the quarantine table gains rows during a re-run. Rollback is a property change; legacy tables are untouched through Phase 7, so no data restore is needed.

### Residual risks not covered by the flag

| Risk | Mitigation |
|---|---|
| Legacy source keeps changing after the load (Q11) | Until cut-over, treat the modern tables as rebuildable: the loader is idempotent by `legacy_*` keys and is re-run before each flip. If the legacy source is written during dual-run, schedule a final delta load in a change freeze. |
| Real data has formats the seed lacks (Q3) | Run the Phase 4 loader in `report-only` mode against a production snapshot before Phase 5; the quarantine count is the go/no-go signal. |
| Blank amount semantic change (`0` → `NULL`, D6) | Phase 1 test documents the old behaviour; Phase 6 must decide whether the DTO exposes `null` or the service substitutes `0` for parity — record the decision in `PARITY_REPORT.md`. |
| Payment sort order changes from string to date sort (D8) | Seed data is single-year so goldens will not reveal it; Phase 6 adds a reconciliation check on multi-year fixtures. |
| Removing legacy code too early | Phase 7 deprecates only; removal is a separate, later PR gated on the criteria in `DATA_SOURCE_MIGRATION_NOTES.md`. |
