# Data Source Migration Notes

Legacy CDW (all-`VARCHAR`) tables → modern normalized schema, with the REST contract unchanged.

## Datasource strategy

A single H2 in-memory instance (`jdbc:h2:mem:loandb`) hosts both schemas:

```properties
spring.sql.init.schema-locations=classpath:schema-legacy.sql,classpath:schema-modern.sql
spring.sql.init.data-locations=classpath:data-legacy.sql
loanservice.migration.enabled=true
```

`schema-modern.sql` is a copy of `data/modern-schema/modern_tables.sql`. One datasource keeps the
migration a plain in-process read-legacy/write-modern transaction — no second `DataSource`,
`EntityManagerFactory`, or `TransactionManager`, and no XA concerns. A second datasource would only
be justified if the legacy source lived in another database engine; the transformation code would be
unchanged.

The legacy tables are migration input only. After startup, `LoanService` reads exclusively from the
modern repositories, so the modern schema is the application's source of truth.

`DataMigrationRunner` runs as an `InitializingBean` (not an `ApplicationRunner`) so the migration
also executes in `@SpringBootTest` contexts, which do not invoke application runners.

## Schema deviation: `payments.external_id`

`modern_tables.sql` drops the legacy `PMT_SEQ_NBR`, but `PaymentDto.paymentId` exposes it. One
column was added to the modern `payments` table (in both `data/modern-schema/modern_tables.sql` and
`schema-modern.sql`) to preserve the API contract:

```sql
external_id VARCHAR(20) UNIQUE, -- legacy PMT_SEQ_NBR, kept for API compatibility
```

This mirrors `borrowers.external_id`, `loan_products.code`, and `loan_accounts.account_number`,
which the modern schema already keeps for the same reason.

## Transformations

Implemented in `LegacyValueParser` and applied by `DataMigrationService`, per
`data/mappings/column_mappings.md`:

| Legacy form | Modern form | Rule |
| --- | --- | --- |
| `MM/DD/YYYY` string | `LocalDate` | strict `MM/dd/uuuu`; blank → `null`; malformed → `MigrationDataException` |
| `285,000.00` string | `BigDecimal` | strip commas, then parse |
| `"720"` | `Integer` | parse; blank → `null` |
| `"Y"`/`"N"` | `Boolean` | `Y` → `true` |
| `ACT`, `CLO`, `DFT`, `FRB`, `INA` | `ACTIVE`, `CLOSED`, `DEFAULT`, `FORBEARANCE`, `INACTIVE` | code expansion |
| `SFR`, `CND`, `MFR`, `TWN` | `Single Family Residence`, `Condominium`, `Multi-Family Residence`, `Townhouse` | code expansion |
| `REG`, `EXT`, `PRT`, `PRE` | `REGULAR`, `EXTRA`, `PARTIAL`, `PREPAYMENT` | payment type expansion |
| `PST`, `REV`, `NSF`, `PND` | `POSTED`, `REVERSED`, `NSF`, `PENDING` | payment status expansion |

Denormalized borrower columns on `CDW_LN_ACCT_MSTR` (`BORR_NM`, `BORR_EMAIL`, …) are dropped; the
loan account carries only the `borrower_id` FK.

FKs are resolved by natural key, so tables migrate in dependency order — borrowers and products,
then loan accounts, then payments:

- loan `BORR_ID` → `borrowers.id` via `external_id`
- loan `PROD_CD` → `loan_products.id` via `code`
- payment `LN_ACCT_NBR` → `loan_accounts.id` via `account_number`

### Edge cases

- Blank/null strings become `null` rather than empty strings.
- Malformed dates and numbers fail the migration loudly instead of silently writing `null`.
- Rows whose natural key is already present in the modern table are skipped (idempotent re-runs).
- Rows with an unresolvable FK are skipped and recorded in `MigrationReport.skipped`.
- Post-migration validation asserts 5 borrowers / 5 products / 5 loan accounts / 10 payments and
  that converted amounts equal the parsed legacy amounts; failures abort startup.

## API contract and intentional differences

`LoanService` maps typed entity fields straight to the DTOs; the legacy `parseLegacy*`/`expand*`
helpers are gone. Preserved behavior: `borrowerName`/`fullName` concatenation (middle initial with a
trailing dot), `"address, city, state zip"` for `propertyAddress`, product description fallback,
`MM/DD/YYYY` date rendering, and the title-cased API labels (`Active`, `Posted`,
`Non-Sufficient Funds`, …) mapped from the modern uppercase enum-style values.

The only difference from the legacy responses is **numeric scale**: legacy amounts were `VARCHAR`
parsed into `BigDecimal` without a fixed scale (`285000`), while `DECIMAL(12,2)` columns serialize
as `285000.00`. Values are equal; only the textual representation differs. `GoldenFileApiTest`
therefore compares numbers by value (`stripTrailingZeros`) and everything else literally.

## Validation

`src/test/resources/golden/` holds the five responses captured from the legacy-backed app before the
migration. `GoldenFileApiTest` replays every endpoint against the modern-backed app and compares.

## Legacy code

`Legacy*` entities and repositories are `@Deprecated`. They are still required as the migration's
input; they can be deleted together with `schema-legacy.sql`/`data-legacy.sql` once the modern
tables are populated from a real upstream source.
