# Legacy → Modern Data Source Migration

This documents how the loan service was moved from the legacy `CDW_*` tables to
the normalized modern schema without changing a single byte of API output.

## Approach

1. **Contract first.** `GoldenFileApiTest` (Phase 0 on `develop`) was written
   against the legacy schema and its JSON responses stored in
   `src/test/resources/golden/legacy/` (list/single loan, all payment histories,
   list/single borrower, not-found behaviour). It predates the migration and
   acts as the contract.
2. **Modern schema on the classpath.** `schema-modern.sql` / `data-modern.sql`
   replace `schema-legacy.sql` / `data-legacy.sql` in `application.properties`.
3. **Modern entities & repositories** replace the `Legacy*` classes.
4. **`LoanService` rewired** to FK-backed JPA reads with presentation mapping.
5. **Legacy artifacts relocated** to `data/legacy-schema/` (reference only).

## Table & column mapping

| Legacy | Modern | Transformation |
|---|---|---|
| `CDW_BORR_MSTR.BORR_ID` | `borrowers.external_id` | retained verbatim (API id) |
| `BORR_CRDT_SCR '745'` | `credit_score INT` | cast |
| `BORR_DOB_DT '03/15/1978'` | `date_of_birth DATE` | parsed |
| `BORR_ANN_INCM '92,500'` | `annual_income DECIMAL` | commas stripped |
| `CDW_LN_PROD.PROD_CD` | `loan_products.code` | retained |
| `PROD_DESC_TXT` | `loan_products.name` | → `productDescription` |
| `CDW_LN_ACCT.LN_ACCT_NBR` | `loan_accounts.account_number` | retained (API id) |
| `CDW_LN_ACCT.BORR_ID`, `BORR_FST_NM`, `BORR_LST_NM` | `loan_accounts.borrower_id` FK | denormalized names dropped |
| `CDW_LN_ACCT.PROD_CD` | `loan_accounts.product_id` FK | |
| `LN_ORIG_AMT '285,000'` | `original_amount DECIMAL(12,2)` | commas stripped |
| `LN_ORIG_DT '02/15/2019'` | `origination_date DATE` | parsed |
| `LN_STAT_CD ACT/CLO/DFT/FRB` | `status ACTIVE/CLOSED/DEFAULT/FORBEARANCE` | expanded |
| `PROP_TYP_CD SFR/CND/MFR/TWN` | `property_type SINGLE_FAMILY/CONDOMINIUM/MULTI_FAMILY/TOWNHOUSE` | expanded |
| `CDW_PMT_HIST.PMT_SEQ_NBR` | `payments.external_id` (**added**) | retained (API id) |
| `CDW_PMT_HIST.LN_ACCT_NBR` | `payments.loan_account_id` FK | |
| `PMT_DT` | `payment_date DATE` | parsed |
| `PMT_TYP_CD REG/EXT/PRT/PRE` | `type REGULAR/EXTRA/PARTIAL/PREPAYMENT` | expanded |
| `PMT_STAT_CD PST/REV/NSF/PND` | `status POSTED/REVERSED/NSF/PENDING` | expanded |

## Presentation mapping in `LoanService`

The DTOs are unchanged, so the service converts typed values back to the legacy
presentation:

```java
originationDate = LocalDate  -> "MM/dd/yyyy"
status          = ACTIVE      -> "Active"
propertyType    = SINGLE_FAMILY -> "Single Family Residence"
payment type    = REGULAR     -> "Regular"
payment status  = POSTED      -> "Posted"   (NSF -> "Non-Sufficient Funds")
borrowerName    = borrower.firstName + " " + borrower.lastName   (via FK)
productDescription = product.name                                 (via FK)
originalAmount  = stripped to scale 0 when it has no cents (legacy "285,000" -> 285000)
```

## Runtime access model

- `LoanAccountRepository` methods use `@EntityGraph(borrower, product)` so a
  single query populates the FK-related data needed by `LoanSummaryDto`.
- `PaymentRepository.findByLoanAccount_AccountNumberOrderByPaymentDateDesc`
  keeps the newest-first ordering, now on a real `DATE` column.
- `LoanAccountRepository.findByBorrower_ExternalIdOrderByIdAsc` powers the
  borrower → loans association in `GET /api/borrowers/{id}`.
- `spring.jpa.open-in-view=false` and `@Transactional(readOnly = true)` on the
  service; `ddl-auto=validate` guarantees entities match `schema-modern.sql`.
- Not-found semantics are unchanged: unknown loan/borrower throws
  `RuntimeException` (HTTP 500); unknown loan payments return `[]`.

## Verification

```bash
./mvnw test
```

- `GoldenFileApiTest` (19 tests) — JSON identical to legacy baseline
- `ModernSchemaIntegrationTest` (9 tests) — modern H2 schema behaviour
- `grep -r CDW_ src/` returns nothing; the only `CDW_*` references are in
  `data/legacy-schema/`, `data/mappings/`, and docs.
