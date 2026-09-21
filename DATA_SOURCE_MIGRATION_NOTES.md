# Data Source Migration Notes

How the REST API moved from the legacy `CDW_*` tables to the modern normalized
schema while keeping every response byte-for-byte compatible with the golden files
in `src/test/resources/golden/`.

## Runtime flow

1. `schema-legacy.sql` + `schema-modern.sql` create both schemas in the same H2
   instance; `data-legacy.sql` seeds the legacy tables (`spring.sql.init.*`).
2. `MigrationStartupRunner` calls `MigrationService.migrate()` once the context is
   ready and copies legacy rows into the modern tables (`POST /migrate` re-runs it).
3. `LoanService` reads exclusively from `repository.modern.*`. Controllers and DTOs
   are unchanged.

## API parity: casing and display strings

The modern schema stores UPPERCASE canonical codes (written by `LegacyCodeMapper`);
the API keeps the legacy title-case display strings. `LoanService` maps them back:

| Field | Canonical (DB) | API display string |
|---|---|---|
| loan status | `ACTIVE` / `CLOSED` / `DEFAULT` / `FORBEARANCE` | `Active` / `Closed` / `Default` / `Forbearance` |
| property type | `SINGLE_FAMILY` / `CONDOMINIUM` / `MULTI_FAMILY` / `TOWNHOUSE` | `Single Family Residence` / `Condominium` / `Multi-Family Residence` / `Townhouse` |
| payment type | `REGULAR` / `EXTRA` / `PARTIAL` / `PREPAYMENT` | `Regular` / `Extra` / `Partial` / `Prepayment` |
| payment status | `POSTED` / `REVERSED` / `NSF` / `PENDING` | `Posted` / `Reversed` / `Non-Sufficient Funds` / `Pending` |

`null` renders as `Unknown` and an unrecognised canonical value is passed through,
exactly as the legacy code path did. Borrower `status` and product `is_active` are
canonicalised in the DB but are not part of any DTO, so no display mapping is needed.
`employmentStatus` was never translated (raw `EMPLOYED` etc.) and still is not.

## API parity: dates and numbers

* `LoanSummaryDto.originationDate` and `PaymentDto.paymentDate` remain `String`;
  `LocalDate` values are formatted with `DateTimeFormatter.ofPattern("MM/dd/yyyy")`.
* Legacy amounts were free text, so whole amounts had no fractional part
  (`"285,000"` -> `285000`) while everything else had two decimals (`"0.00"`,
  `"1,487.02"`). `DECIMAL(x,2)` columns always return scale 2, which would serialize
  as `285000.00` and change the JSON token type (float vs integer). `LoanService`
  therefore rescales whole, non-zero amounts to scale 0; zero and fractional values
  keep their scale (`0.00` still serializes as a float, as before). `interestRate`
  (`DECIMAL(5,3)`) is emitted as stored (`4.750`), identical to the legacy string.
* Derived fields are rebuilt the same way: `borrowerName = first + " " + last`,
  `fullName = first [+ " " + middleInitial + "."] + " " + last`,
  `propertyAddress = address + ", " + city + ", " + state + " " + zip`.
* `productDescription` comes from `loan_products.name` (legacy `PROD_DESC_TXT`).

## API parity: identifiers and ordering

* Borrower and loan IDs are natural keys (`borrowers.external_id`,
  `loan_accounts.account_number`), so `B-10001` / `LN-2019-00142` work unchanged.
* Payments have a surrogate `id` in the modern schema, so a `legacy_payment_id`
  column (legacy `PMT_SEQ_NBR`, e.g. `PMT-2025120001`) was added to `payments`
  and populated by `MigrationService`; `PaymentDto.paymentId` returns it.
* Legacy list endpoints returned rows in insertion order. Migration inserts in
  legacy `findAll()` order, so modern identity ids reproduce it; list queries
  order by `id` (`findAllByOrderByIdAsc`, `findByBorrowerExternalIdOrderByIdAsc`).
  Payments keep `ORDER BY payment_date DESC` with `id` as a deterministic tiebreak.
* Unknown ids still raise `RuntimeException` (HTTP 500) exactly as the legacy
  service did; no 404 was introduced.

## Migration: FK ordering, quarantine, idempotency

* Tables are migrated in FK-safe order: `borrowers` -> `loan_products` ->
  `loan_accounts` (needs both parents) -> `payments` (needs the loan account).
* Edge cases are quarantined, never defaulted: unparseable amounts/dates/integers,
  unknown status codes, blank required fields and orphaned children (missing
  borrower/product/loan account) throw `MigrationException`, the record is skipped
  and the reason is logged and reported in `MigrationSummary.quarantined`.
* Re-runs are idempotent: borrowers/products/loan accounts are skipped when their
  natural key already exists; payments are skipped when their `legacy_payment_id`
  (`UNIQUE`) already exists, falling back to the value-based natural key (loan
  account, date, amounts, type, status, received date) only for rows without one.

## Validation harness

* **Golden parity** (`GoldenFileParityTest`): every file in `src/test/resources/golden`
  is replayed through MockMvc and compared as Jackson `JsonNode` trees; a second test
  derives the endpoint set from the live `/api/loans` and `/api/borrowers` responses
  and asserts the golden files cover exactly that set (2 list + 15 detail endpoints).
  There are **no intentional differences** from the legacy baseline; any diff is a bug.
* **SQL reconciliation** (`MigrationReconciliationTest`, JdbcTemplate): 5/5/5/10 row
  counts equal to the CDW tables, natural keys one-to-one, amount totals (loan
  amounts, balances, payments, income) equal to the legacy comma-stripped strings,
  zero orphan FKs, same borrower/product/loan parent as legacy, and every payment has
  a unique non-null `legacy_payment_id` matching exactly one `PMT_SEQ_NBR`.
* **Transformer units** (`LegacyValueParserTest`, `LegacyCodeMapperTest`,
  `MigrationServiceTransformTest`): strict `MM/DD/YYYY` parsing, comma-stripped
  decimals keeping their scale, integers, and every code table (borrower status, loan
  status, product status boolean, payment type/status, property type) including
  cross-domain, blank and null inputs failing loud with record id and field.
* **Quarantine end-to-end** (`MigrationServiceIntegrationTest`): malformed and orphaned
  legacy rows are reported in `MigrationSummary.quarantined` and never inserted or
  defaulted, while valid rows are unaffected.

## Deprecation of legacy entities and repositories

`entity.Legacy*` and `repository.Legacy*Repository` are annotated `@Deprecated`
with a Javadoc note. They remain only as the read side of `MigrationService`
and must not gain new usages. Once the legacy CDW tables are decommissioned,
they can be deleted together with `schema-legacy.sql` / `data-legacy.sql` and
the startup migration.
