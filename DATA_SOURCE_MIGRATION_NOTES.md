# Data Source Migration Notes (legacy CDW → modern schema)

## What changed

| Area | Before | After |
|------|--------|-------|
| Entities | `LegacyBorrower`, `LegacyLoanProduct`, `LegacyLoanAccount`, `LegacyPayment` (all-`String`) | `Borrower`, `LoanProduct`, `LoanAccount`, `Payment` with `LocalDate`, `LocalDateTime`, `BigDecimal`, `Integer`, `Boolean` and `@ManyToOne`/`@OneToMany` relationships |
| Repositories | `Legacy*Repository` keyed by legacy string PKs | `BorrowerRepository`, `LoanProductRepository`, `LoanAccountRepository`, `PaymentRepository` keyed by generated `BIGINT` ids, with lookups by `externalId` / `accountNumber` / `code` |
| Service | `LoanService` parsed strings (`parseLegacyAmount`, `expandStatusCode`, …) | `LoanService` reads modern entities; only presentation formatting remains |
| Data | Legacy tables served directly | `DataMigrationService` transforms legacy rows into the modern tables at startup (`DataMigrationRunner`) |
| Schema | `schema-legacy.sql` only | `schema-legacy.sql` (migration source) + `schema-modern.sql` (served) |

## Transformations applied

Implemented in `DataMigrationService`, following `data/mappings/column_mappings.md`:

- `MM/DD/YYYY` strings → `LocalDate`; created/updated dates → `LocalDateTime` at start of day.
- Amounts with thousands separators (`"1,487.02"`) → `BigDecimal`.
- Numeric strings (`"745"`, `"360"`) → `Integer`.
- Status codes expanded: `ACT→ACTIVE`, `CLO→CLOSED`, `DFT→DEFAULT`, `FRB→FORBEARANCE`, `INA→INACTIVE`.
- Payment codes expanded: `REG→REGULAR`, `EXT→EXTRA`, `PRT→PARTIAL`, `PRE→PREPAYMENT`, `PST→POSTED`, `REV→REVERSED`, `NSF→NSF`, `PND→PENDING`.
- `PROD_STAT_CD` → `is_active` boolean.
- Denormalized borrower columns on `CDW_LN_ACCT` are dropped; `borrower_id` / `product_id` are resolved via `external_id` / `code` lookups, and `payments.loan_account_id` via `account_number`.
- Unparseable values are logged and stored as `NULL`; rows whose FK cannot be resolved are skipped with a warning instead of failing the migration.

## Deliberate schema deviations

- `payments.external_id` was added (the mapping doc allows "legacy ID stored if needed"). It preserves `PMT_SEQ_NBR`, so `PaymentDto.paymentId` is unchanged for API clients.
- `loan_accounts.property_type` stores the full display label (`Single Family Residence`, `Condominium`, `Multi-Family Residence`, `Townhouse`) so no code table is needed at read time.

## API compatibility

The REST contract is unchanged. Statuses and payment types are stored uppercase and rendered as the previous labels (`ACTIVE → Active`, `NSF → Non-Sufficient Funds`); dates are still emitted as `MM/dd/yyyy` strings.

`LoanServiceApplicationTests` was written against the legacy data source first and passes unchanged after the migration (12 service-layer tests plus 8 MockMvc tests covering every `LoanController` / `BorrowerController` endpoint). `DataMigrationServiceTests` reconciles row counts (5 borrowers, 5 products, 5 loan accounts, 10 payments), typed values, FK resolution, balance totals, and idempotency of re-running the migration.

## Legacy code status

The `Legacy*` entities and repositories are retained *only* as the migration source. Once the modern tables are populated by an external migration job, set `loanservice.migration.run-on-startup=false` and the legacy classes plus `schema-legacy.sql` / `data-legacy.sql` can be deleted.
