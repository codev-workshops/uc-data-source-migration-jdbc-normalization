# Legacy-to-Modern Data Source Refactor Plan

## Approval Status

**Approved on 2026-07-15.**

This plan was prepared from `refactor-scope.md` in PR #41:

```text
origin/devin/1752634500-refactor-scope:refactor-scope.md
```

The plan and recommendations in `SCOPE-QUESTIONS.md` were explicitly approved before implementation began.

## Session Contract

Migrate the Spring Boot loan service from the legacy `CDW_*` H2 tables to the normalized `borrowers`, `loan_products`, `loan_accounts`, and `payments` tables while preserving the current REST API and all observable business behavior.

The implementation will:

1. Repair only the build defects that currently prevent a baseline from running.
2. Capture the legacy API as an executable compatibility baseline before changing application behavior.
3. Add an explicit modern schema, typed JPA entities, relationships, and repositories.
4. Implement and test a transactional, repeatable legacy-to-modern migration.
5. Preserve external borrower, loan, product, and payment identifiers.
6. Rewire `LoanService` to read only from the modern repositories.
7. Make the modern schema/data the default runtime.
8. Prove row-count, relationship, transformation, and API parity.
9. Retain legacy artifacts only where needed to test or execute migration, clearly isolated from the normal application read path.
10. Document the migration and avoid every optional bonus item.

No endpoint, JSON field, identifier, status label, date representation, decimal representation, or not-found behavior will be intentionally changed without renewed approval.

## Scope Authority

The implementation will follow these scope lines from PR #41:

- Existing runtime behavior wins over conflicting documentation: lines 13-20.
- API baseline and route preservation: lines 46-61.
- Modern schema and default runtime: lines 63-69.
- Typed entities, relationships, and repository queries: lines 71-98.
- Migration order, transformations, and transaction/integrity requirements: lines 100-135.
- External identifier preservation: lines 137-145.
- Service rewiring and response compatibility: lines 147-162.
- Regression and integrity tests: lines 164-179.
- Cutover, cleanup, and documentation: lines 181-193.
- Explicit exclusions: lines 195-207.
- Definition of done: lines 222-237.

## Baseline Results

Baseline commit:

```text
51dad16f87ee4527b77f5e74f1674613952bc708
```

Environment used:

- Eclipse Temurin Java 17.0.17
- Apache Maven 3.9.16
- Windows Server 2022

Command attempted:

```text
mvn test
```

Result:

- **Failed before compilation or test discovery.**
- Maven reports `pom.xml:11` as malformed because `<relativeTo/>` is not a valid parent element. The intended Maven element is `<relativePath/>`.
- Therefore, 0 tests ran and the legacy application could not be started to capture API responses.
- The repository contains `.mvn/wrapper/maven-wrapper.properties` but does not contain `mvnw` or `mvnw.cmd`.
- No Checkstyle, SpotBugs, PMD, formatter, pre-commit, or other lint/type-check configuration is present.
- The only committed test is `LoanServiceApplicationTests.contextLoads()`.

These are pre-existing baseline defects. They will be handled as the minimum Blocking fixes described in Plan Step 1; they are not migration regressions.

## Current Dependency and Contract Map

### Public HTTP contracts

| Contract | Current implementation |
|---|---|
| List loans | `GET /api/loans` |
| Loan detail | `GET /api/loans/{id}` where `id` is the account number |
| Loan payments | `GET /api/loans/{loanId}/payments` |
| List borrowers | `GET /api/borrowers` |
| Borrower detail | `GET /api/borrowers/{id}` where `id` is the legacy borrower ID |

`README.md` incorrectly documents the payment route as `GET /api/payments/loan/{loanId}`. The controller route is the runtime contract and will be preserved.

### Data flow

```text
Controllers
  -> LoanService
    -> LegacyBorrowerRepository
    -> LegacyLoanAccountRepository
    -> LegacyLoanProductRepository
    -> LegacyPaymentRepository
      -> CDW_* tables
```

`LoanService` currently owns:

- legacy joins between loans, borrowers, and products;
- comma removal and decimal parsing;
- integer parsing;
- status, property type, payment type, and payment status expansion;
- borrower full-name composition;
- property address composition;
- DTO mapping.

### Observable response rules to preserve

- Borrower IDs are `B-*` external IDs.
- Loan IDs are `LN-*` account numbers.
- Payment IDs are `PMT-*` legacy sequence numbers.
- Loan and payment dates are currently exposed as `MM/DD/YYYY` strings.
- Loan statuses are title-cased (`Active`, `Closed`, `Default`, `Forbearance`).
- Property types are expanded to current display labels.
- Payment types and statuses are expanded to current display labels.
- `BorrowerDto.loans` is absent/null for the list endpoint and populated for borrower detail.
- Missing borrower/loan records currently result from an uncaught `RuntimeException`; exact HTTP behavior must be captured before it is preserved.
- Payment ordering is currently requested by a string-backed `paymentDate` descending query.
- Collection ordering from repository `findAll()` is not explicitly defined and must be captured before golden assertions are finalized.

## Proposed Target Architecture

```text
Controllers (unchanged)
  -> LoanService
    -> BorrowerRepository
    -> LoanAccountRepository
    -> LoanProductRepository
    -> PaymentRepository
      -> modern typed tables

Migration utility/test path
  -> reads legacy CDW rows
  -> validates and transforms values
  -> resolves modern foreign keys
  -> transactionally writes modern rows
```

The normal application read path will not inject or query legacy repositories after cutover.

Recommended runtime strategy:

- Keep legacy schema/data available only to migration-focused tests or an explicit migration execution path.
- Make the default application initialize `schema-modern.sql` and verified `data-modern.sql`.
- Use the Java migration utility in integration tests to prove that `data-legacy.sql` transforms into the same modern records represented by `data-modern.sql`.
- Do not add a runtime dual-read feature flag.

This strategy gives the in-memory workshop application a clean modern default while retaining executable proof that legacy data can be migrated.

## Ordered Implementation Plan

### Step 1 - Repair the build and capture the true legacy baseline

Changes:

- Correct the invalid Maven parent element in `pom.xml`.
- Generate/restore the missing Maven wrapper launchers required by the scope.
- Mark the POM correction as the protocol-required minimum Blocking fix.
- Run the full committed test suite.
- Start the unchanged legacy application.
- Capture status codes and JSON bodies for every scoped endpoint and every supplied borrower/loan.
- Capture not-found behavior.
- Store the results in one deterministic golden fixture keyed by request path, or a similarly compact test resource.
- Add a baseline test that proves the captured fixture represents the unchanged legacy service.

Verification:

- `mvnw.cmd test` passes.
- The legacy application starts.
- Golden responses cover all paths required by scope lines 50-57.
- No production behavior changes are introduced in this step.

Commit boundary:

```text
build: repair Maven baseline and capture legacy API
```

### Step 2 - Add the executable modern schema and typed persistence model

Changes:

- Create `src/main/resources/schema-modern.sql` from the target DDL.
- Add an additive unique `external_id`/`source_id` column to `payments` if the golden baseline confirms the exposed `PMT-*` identifier.
- Keep `data/modern-schema/modern_tables.sql` synchronized with the executable schema.
- Add typed entities:
  - `Borrower`
  - `LoanProduct`
  - `LoanAccount`
  - `Payment`
- Add required `@ManyToOne` relationships.
- Avoid inverse collections unless a measured query path needs them.
- Add modern repositories with business-key and relationship queries.
- Add repository/schema tests for constraints, relationships, lookups, and indexes where H2 metadata makes this practical.

Verification:

- Modern-schema context starts with `ddl-auto=none`.
- Repository tests prove all required lookups.
- No controllers or external DTO contracts change.

Commit boundary:

```text
refactor: add modern schema entities and repositories
```

### Step 3 - Implement transactional migration and reconciliation

Changes:

- Add focused migration components for:
  - typed date/timestamp parsing;
  - decimal/integer parsing;
  - code expansion;
  - row-level validation and source-context errors;
  - dependency-ordered migration;
  - foreign-key resolution;
  - duplicate/partial-target detection.
- Use one transaction for the complete migration.
- Use a repeatability policy of:
  - no-op only when the target is already complete and reconciled;
  - fail before writes when the target is partially populated or conflicting.
- Add migration integration tests using the committed legacy schema/data.
- Reconcile exact counts: 5 borrowers, 5 products, 5 loans, 10 payments.
- Verify representative values and every foreign key.
- Produce `data-modern.sql` only from verified transformed values; do not hand-wave or silently normalize source values.

Verification:

- Transformation unit tests cover valid, null, malformed, unknown-code, and duplicate cases.
- A forced failure proves transaction rollback leaves no partial modern rows.
- Running migration twice satisfies the approved repeatability policy.
- Migrated database rows match `data-modern.sql`.

Commit boundary:

```text
refactor: migrate and reconcile legacy loan data
```

### Step 4 - Rewire the service to modern repositories

Changes:

- Replace all four legacy repository dependencies in `LoanService`.
- Use entity relationships for borrower/product/loan association.
- Remove parsing helpers from the normal read path.
- Retain only presentation mapping needed to preserve the current DTO contract:
  - external IDs;
  - full-name punctuation;
  - display labels/capitalization;
  - `MM/DD/YYYY` strings;
  - property-address formatting;
  - existing decimal JSON values.
- Preserve actual route mappings and controller signatures.
- Use explicit queries/orderings where the baseline shows observable ordering.
- Avoid N+1 behavior with repository fetch queries/entity graphs only if needed, verifying identical result sets.

Verification:

- Modern API responses match the golden fixture.
- Modern service tests cover list, detail, nested loans, payments, and not-found behavior.
- No normal service method injects or calls a legacy repository.

Commit boundary:

```text
refactor: switch loan service reads to modern data
```

### Step 5 - Cut over default configuration and isolate legacy support

Changes:

- Update `application.properties` so the default application initializes only the modern schema and modern data.
- Keep `spring.jpa.hibernate.ddl-auto=none`.
- Isolate legacy schema/data and migration-only components from normal application reads.
- Remove legacy entities/repositories if migration tests no longer require them; otherwise retain them only with documented purpose.
- Confirm no default API path queries a `CDW_*` table.

Verification:

- Default application startup uses modern resources.
- Legacy-to-modern migration integration tests still run in their isolated configuration.
- Modern application tests pass without creating or querying legacy tables.

Commit boundary:

```text
refactor: cut over default runtime to modern schema
```

### Step 6 - Complete regression coverage and documentation

Changes:

- Add `DATA_SOURCE_MIGRATION_NOTES.md` with decisions, execution order, duplicate/error policy, reconciliation evidence, retained legacy artifacts, and any approved differences.
- Correct README startup instructions and the payment route without changing the actual route.
- Document the modern default configuration.
- Remove comments made stale by the refactor.
- Update `SCOPE-QUESTIONS.md` with final resolutions.
- Create `REFACTOR-BACKLOG.md` only if non-blocking out-of-scope findings arise during implementation.

Verification:

- Full Maven suite passes.
- No configured lint/type-check command exists beyond Maven compilation; record that fact in the PR.
- `git diff --check` passes.
- Final checks match or beat the repaired baseline.

Commit boundary:

```text
docs: record migration decisions and verification
```

## Expected File Set

### Existing files expected to change

- `pom.xml`
- `.mvn/wrapper/maven-wrapper.properties` only if wrapper generation requires it
- `README.md`
- `data/modern-schema/modern_tables.sql`
- `src/main/java/com/workshop/loanservice/service/LoanService.java`
- `src/main/resources/application.properties`
- `src/test/java/com/workshop/loanservice/LoanServiceApplicationTests.java`

Legacy entities/repositories/resources may be removed or isolated only as described in Step 5.

### New files expected

- `mvnw`
- `mvnw.cmd`
- `src/main/resources/schema-modern.sql`
- `src/main/resources/data-modern.sql`
- four modern entity classes
- four modern repository interfaces
- migration service/parser/validation classes
- migration, repository, and API parity tests
- compact golden response fixture(s)
- migration-specific test configuration/resources
- `DATA_SOURCE_MIGRATION_NOTES.md`
- `SCOPE-QUESTIONS.md`
- this `REFACTOR-PLAN.md`

### Files expected to remain externally stable

- `BorrowerController.java`
- `LoanController.java`
- `BorrowerDto.java`
- `LoanSummaryDto.java`
- `PaymentDto.java`

Mechanical changes to these files require an explicit explanation and must not alter their public JSON contract.

## Contracts at Risk

| Risk | Planned control |
|---|---|
| Payment route documentation conflicts with code | Preserve controller route; correct README only |
| Generated modern IDs replace business IDs | Query and serialize external IDs/account numbers/source payment IDs |
| `LocalDate` changes JSON date format | Keep DTO strings formatted exactly as baseline |
| Uppercase modern statuses leak into JSON | Map stored canonical values to existing display labels |
| Entity relationships cause N+1 queries | Verify query counts and use targeted fetch queries if needed |
| Modern FK constraints reject source rows | Validate all references before writes and fail transactionally |
| Partial/duplicate migration corrupts target | Preflight target state plus one migration transaction |
| Repository ordering changes arrays | Capture baseline and add explicit ordering where observable |
| Modern schema reference and runtime DDL drift | Test/synchronize both DDL representations |
| Legacy cleanup removes migration evidence | Retain isolated migration fixtures/tests until parity is proven |

## Guardrail Overrides

One explicit default-guardrail override is planned:

| Guardrail | Scope authorization |
|---|---|
| Do not change configuration defaults | The scope explicitly requires the final default runtime to initialize/read the modern schema (`refactor-scope.md` lines 67-69 and 183). |

No other guardrail override is currently planned.

## Out-of-Scope Boundaries

Do not implement:

- dual-read runtime mode or a feature flag;
- performance benchmarking;
- external/cloud database deployment;
- a general ETL framework;
- endpoint aliases or redesign;
- new API error models;
- authentication/authorization;
- SSN re-encryption;
- unrelated business features;
- dependency or framework upgrades.

New dependencies are not expected. Spring Boot, Spring Data JPA, H2, JUnit, Mockito, and Spring test support already cover the plan.

## Rollback Approach

- Use a new `refactor/legacy-to-modern-data-source` branch created from the latest `main` only after approval.
- Keep each plan step in a separate working commit.
- Do not drop or mutate live database objects; this repository uses in-memory H2 fixtures.
- Add modern tables before switching reads.
- Retain legacy fixtures until migration and parity tests pass.
- If any step fails, revert that step's commit rather than patching a broken intermediate state.
- Before the cutover commit, rollback is simply continued use of the legacy service.
- After the cutover commit, rollback is reverting the cutover/service commits while the preserved legacy fixtures remain available.
- A migration exception rolls back the full migration transaction.

## Stop Conditions

Implementation will halt and report if:

- any safety invariant would be violated;
- approved recommendations in `SCOPE-QUESTIONS.md` become invalid;
- the touched file set grows beyond roughly twice this plan without re-approval;
- the same verification fails after three distinct approaches;
- exact API parity cannot be achieved without an unapproved contract change;
- a new dependency or version bump becomes necessary;
- migration cannot be made transactional and referentially consistent.

## Approval Request

Approve this plan together with the recommended interpretations in `SCOPE-QUESTIONS.md` to authorize:

1. creation of `refactor/legacy-to-modern-data-source`;
2. the minimum Maven build repair;
3. baseline capture;
4. implementation Steps 2-6;
5. a draft PR after final verification.
