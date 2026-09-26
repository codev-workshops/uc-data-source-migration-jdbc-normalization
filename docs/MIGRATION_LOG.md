# Migration Log

Chronological record of the legacy-to-normalized data source migration for `loan-service`.
Each phase lists its intent and outcome; see `MIGRATION_TASKS.md` for the original task spec.

## 1. Baseline end-to-end tests

**Intent:** Capture the current REST behavior before touching any data access code.

**Outcome:** `@SpringBootTest` end-to-end suites (loans, borrowers, payments, plus a hybrid
data-access-failure suite) exercised every endpoint over HTTP with `TestRestTemplate` against the
seeded legacy `CDW_*` tables. These tests became the contract that every later phase had to keep.

## 2. Flyway as the sole schema owner

**Intent:** Replace `spring.sql.init` scripts with versioned, repeatable migrations.

**Outcome:** Flyway was introduced with `V1` (legacy `CDW_*` schema), `V2` (normalized schema),
`V3` (legacy seed data), `V4` (legacy to normalized data migration) and `V5` (normalized
constraints and indexes). `spring.sql.init.mode=never` and `spring.jpa.hibernate.ddl-auto=none`
were pinned so Flyway is the only component that creates or alters tables.

## 3. Service abstraction and conditional wiring

**Intent:** Make the data path swappable without changing the controllers.

**Outcome:** The concrete `LoanService` was split into a `LoanQueryService` interface with two
implementations: `LegacyLoanService` (string-typed `CDW_*` entities) and `NormalizedLoanService`
(typed `entity/normalized` entities and `repository/normalized` repositories). Controllers inject
the interface; `@ConditionalOnProperty` on `application.data-mode` selected the implementation,
defaulting to `normalized`. The e2e suite was split into `e2e/legacy` and `e2e/normalized` so both
paths were verified against the same DTO contract.

## 4. Missing payments endpoint

**Intent:** Close the gap between the README and the API.

**Outcome:** `GET /api/payments/loan/{loanId}` was added to `PaymentController`, returning the same
`PaymentDto` list as `GET /api/loans/{id}/payments`, and covered by unit and e2e tests.

## 5. Structured logging with Logback

**Intent:** Replace ad hoc logging with a consistent, structured pattern.

**Outcome:** `logback-spring.xml` defines the console pattern and per-package levels; controllers
and services log business intent (which loan, borrower or payment set is requested) rather than
implementation detail. A move to log4j2 is a planned future migration.

## 6. JaCoCo code coverage

**Intent:** Measure test coverage as part of the standard build.

**Outcome:** The `jacoco-maven-plugin` was added to `pom.xml`; `mvn verify` produces the coverage
report under `target/site/jacoco/`.

## 7. Unit tests

**Intent:** Complement the e2e suites with fast, focused tests.

**Outcome:** Unit tests were added for the services (mocked repositories), the repositories
(`@DataJpaTest` against the Flyway-migrated H2 schema) and the controllers (`@WebMvcTest`).

## 8. Controller-level error handling

**Intent:** Return consistent error bodies instead of framework defaults.

**Outcome:** `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `ResourceNotFoundException`
to 404, `DataAccessException` to 500 and any other exception to 500, all with an `ErrorResponse`
body. The `application.data-mode` flag was pinned to `normalized` in `application.properties`.

## 9. Legacy retirement

**Intent:** Remove the legacy path now that the normalized schema is the sole source of truth.

**Outcome:** `V6__drop_legacy_schema.sql` drops the four `CDW_*` tables (`CDW_PMT_HIST`,
`CDW_LN_ACCT`, `CDW_LN_PROD`, `CDW_BORR_MSTR`); `V1`-`V5` are untouched. `LegacyLoanService`,
the four `Legacy*` entities and repositories, their unit tests, the `e2e/legacy` suite, the
`test-data/e2e/legacy` fixtures and the unused `schema-legacy.sql`/`data-legacy.sql` helpers were
deleted. The `application.data-mode` property and the `@ConditionalOnProperty` wiring were removed
entirely, since a flag with a single value serves no purpose: `NormalizedLoanService` is now the
sole unconditional `@Service` implementing `LoanQueryService`. REST paths, DTO fields and the
Flyway-only schema ownership are unchanged.

## 10. Legacy path restored as deprecated

**Intent:** Keep the legacy path available behind the `application.data-mode` flag while it is
phased out, instead of removing it outright.

**Outcome:** `V6__drop_legacy_schema.sql` was removed, so the `CDW_*` tables (created by `V1`,
seeded by `V3`) persist. `LegacyLoanService`, the four `Legacy*` entities and repositories, their
unit tests, the `e2e/legacy` suite and the `test-data/e2e/legacy` fixtures were restored and are
annotated `@Deprecated`, pointing to `NormalizedLoanService` and the normalized entities and
repositories as the replacement. The `@ConditionalOnProperty` wiring and the
`application.data-mode=normalized` property are back: `normalized` is the default
(`matchIfMissing = true`) and `legacy` selects the deprecated path. `schema-legacy.sql` and
`data-legacy.sql` stay deleted, since Flyway owns the schema.

## 11. Per-request implementation selection

**Intent:** Choose the legacy or normalized data path per HTTP request instead of once per
deployment, so both paths can be compared side by side against the same running instance.

**Outcome:** The static `@ConditionalOnProperty` wiring and the `application.data-mode` property
were removed; `NormalizedLoanService` and `LegacyLoanService` are now always-on beans. A new
`routing` package holds the selection machinery:

- `ServiceImplementation` (`LEGACY`, `NORMALIZED`) with `fromParam(String)`, which parses
  case-insensitively and defaults to `NORMALIZED` for null, blank or unknown values.
- `LoanServiceRegistry`, a singleton `Map<ServiceImplementation, LoanQueryService>`. Each concrete
  service self-registers in a `@PostConstruct` hook; `get` falls back to `NORMALIZED` and throws
  `IllegalStateException` only if neither is registered.
- `RoutingContext`, a `@RequestScope` bean holding the selection for the current request.
- `ServiceSelectionInterceptor`, a `HandlerInterceptor` registered by `config/WebConfig` for
  `/api/**`. It reads the `serviceImpl` query parameter, logs the raw and resolved values, and
  stores the result on the `RoutingContext`.
- `LoanServiceRouter`, the `@Primary` `LoanQueryService` facade injected into the controllers. Each
  call reads the `RoutingContext` and delegates to the registered service.

Usage: `GET /api/loans?serviceImpl=legacy` serves from the `CDW_*` tables; omitting the parameter
or passing any other value serves from the normalized schema. The `e2e/legacy` suite appends the
parameter through a `RestTemplateBuilder` interceptor, and `e2e/routing/ServiceSelectionE2ETest`
asserts the routing decision itself (legacy expands `ACT` to `Active`, normalized returns
`ACTIVE`).
