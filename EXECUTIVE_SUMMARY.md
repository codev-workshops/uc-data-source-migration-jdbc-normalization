# Executive Summary — Loan Service Data Source Migration

## Problem statement

The `loan-service` is a read-only REST API for mortgage/loan servicing data
(borrowers, products, accounts, payments). It read directly from legacy CDW
(Corporate Data Warehouse) tables that are:

- **Untyped** — every column is `VARCHAR`; dates are `MM/DD/YYYY` strings, amounts
  are comma-grouped strings (`"285,000"`), and statuses are cryptic codes (`ACT`,
  `SFR`, `PST`).
- **Denormalized** — borrower fields are copied onto loan-account rows; there are
  no foreign keys.
- **Leaky** — all string→type parsing and code expansion happen at read time in
  the service layer, mixing presentation concerns with data access.

The goal: migrate to a clean, normalized, properly-typed modern schema **while
keeping the public API byte-for-byte identical**, with a safe, reversible cutover.

## Architecture before migration

```
Controllers → LoanService (parse strings, expand codes, format dates) → Legacy CDW repos → legacydw (H2, all VARCHAR)
```

- Single data source (`legacydw`).
- `LoanService` owned ~210 lines of translation logic.
- Test coverage was effectively just `contextLoads()`.

## Architecture after migration

```
                                ┌─ LegacyLoanDataProvider → legacy CDW repos → legacydw
Controllers → LoanService ──────┤   (selected by loanservice.datasource)
              (facade)          └─ ModernLoanDataProvider → modern repos     → moderndw (typed, normalized, FK)

MigrationService: legacydw ──(transform + FK resolve)──▶ moderndw
```

- **Two data sources**, each with its own `DataSource` / `EntityManagerFactory` /
  `TransactionManager` (`LegacyDataSourceConfig`, `ModernDataSourceConfig`).
- **Read abstraction** `LoanDataProvider` with legacy and modern implementations;
  `LoanService` is now a thin facade selecting the provider from a feature flag.
- **Migration service** performs idempotent ETL from legacy into the modern schema.
- Controllers and DTOs are unchanged — the public contract is fixed.

## Migration strategy

1. **Baseline first (Task 0).** Capture golden JSON for every endpoint from the
   untouched legacy app, with automated tests, so every later change is provably
   output-identical.
2. **Stand up the modern schema (Task 1).** Typed, normalized JPA entities,
   repositories, `schema-modern.sql`, and a second data source — initially empty.
3. **Migrate the data (Task 2).** `MigrationService.migrate()` clears modern
   tables, loads in FK dependency order (borrowers → products → accounts →
   payments), transforms each field, resolves foreign keys in memory (fail-fast on
   any miss), and verifies row counts.
4. **Rewire behind a flag (Task 3).** Introduce `LoanDataProvider`;
   `loanservice.datasource=legacy|modern` chooses the source at runtime with the
   legacy path as default and instant rollback.
5. **Reconcile & document (Task 4).** Cross-source parity tests + reconciliation,
   and full migration notes.

## Validation strategy

Three automated layers (26 tests, all green via `mvn test`):

- **Golden files in both modes** — every endpoint compared to the captured
  baseline with `loanservice.datasource` set to `legacy` and to `modern`.
- **Cross-source reconciliation** — both providers driven directly and asserted
  to produce identical output, plus reconciling monetary totals.
- **Migration integrity** — row counts (5/5/5/10), referential integrity,
  monetary totals (legacy parsed vs modern stored), and field transformations.

## Key technical decisions

- **Two distinct data sources** (vs. one DB with extra tables) to match the
  target architecture and make the legacy/modern boundary explicit.
- **Provider abstraction + feature flag** instead of rewriting `LoanService` in
  place — enables both paths to coexist, A/B comparison, and one-line rollback.
- **Store canonical, present legacy.** The modern schema stores canonical typed
  values (`ACTIVE`, `LocalDate`); the modern provider re-applies legacy
  presentation (title-case, `MM/DD/YYYY`) so the contract is preserved without
  polluting the schema.
- **Fail-fast FK resolution** to prevent silent data loss during migration.
- **Numeric-aware response comparison** to treat `285000` and `285000.00` as
  equal (the one documented difference) while keeping everything else exact.

## Risks encountered

- **Output fidelity** (dates, title-case statuses, expanded property type,
  external-id-based API ids) — mitigated by the modern provider's presentation
  layer and golden tests.
- **`BigDecimal` scale drift** — `DECIMAL(12,2)` yields `285000.00` vs legacy
  `285000`; numerically identical, documented and justified.
- **Multi-data-source JPA wiring** (lazy loading across the modern session) —
  modern reads run in a read-only modern transaction so associations initialize
  while DTOs are built.
- **Build/setup blockers** — an invalid `pom.xml` tag and a non-idempotent
  legacy schema were fixed early.

## Outcome

The migration is functionally complete (Tasks 0–4). The modern, typed, normalized
schema is fully wired and proven output-identical to the legacy API. Cutover and
rollback are a single config change. The legacy path remains intact and default;
removing it (Task 5) is intentionally deferred.

See `DATA_SOURCE_MIGRATION_NOTES.md` for full detail and `INTERVIEW_NOTES.md` for
a discussion-oriented deep dive.
