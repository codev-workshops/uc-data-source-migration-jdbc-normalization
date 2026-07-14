# Microservices Decomposition Notes

This document records how the `loan-service` monolith was split into four
bounded-context microservices and the key decisions made along the way.

## Bounded contexts

| Service            | Owns                | Legacy source table | Modern table    |
|--------------------|---------------------|---------------------|-----------------|
| `borrower-service` | Borrower master     | `CDW_BORR_MSTR`     | `borrowers`     |
| `product-service`  | Loan product catalog| `CDW_LN_PROD`       | `loan_products` |
| `loan-service`     | Loan accounts       | `CDW_LN_ACCT`       | `loan_accounts` |
| `payment-service`  | Payment history     | `CDW_PMT_HIST`      | `payments`      |

Each service is a standalone Spring Boot app (own `pom.xml`, main class,
`application.properties`, port) with its own in-memory H2 database and its own
`schema.sql` / `data.sql`. There is no shared datasource.

## Modern schema adoption

Data was migrated from the legacy all-VARCHAR CDW tables into the modern
normalized schema (`data/modern-schema/modern_tables.sql`) using the rules in
`data/mappings/column_mappings.md`:

- Currency strings (`"285,000"`) → `DECIMAL`.
- `MM/DD/YYYY` date strings → `DATE`.
- Numeric strings (credit score, term months, delinquency days) → `INTEGER`.
- Coded values expanded and stored in readable form:
  - loan status `ACT`→`ACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`, `FRB`→`FORBEARANCE`
  - property type `SFR`→`Single Family Residence`, `CND`→`Condominium`, `TWN`→`Townhouse`
  - payment type `REG`→`REGULAR`, etc.; payment status `PST`→`POSTED`, etc.
  - product `ACT` status → `is_active` boolean

Because the codes are expanded at rest, the monolith's `parseLegacy*` /
`expand*` helper methods are no longer needed — the entities carry proper types.

## Cross-context references (business keys, not surrogate FKs)

The modern schema models relationships as `BIGINT` foreign keys
(`loan_accounts.borrower_id → borrowers.id`, etc.). Surrogate auto-increment keys
are **local to each database** and are not meaningful across service boundaries,
so each context instead references the others by their stable business key:

- `loan_accounts.borrower_id` stores the borrower `external_id` (e.g. `B-10001`).
- `loan_accounts.product_code` stores the product `code` (e.g. `FXD30`).
- `payments.loan_account_number` stores the loan `account_number`
  (e.g. `LN-2019-00142`).

This keeps the `id` values referentially meaningful across independently-owned
databases and avoids leaking one service's surrogate keys into another.

## Denormalization removed

- `loan_accounts` no longer carries `BORR_FST_NM` / `BORR_LST_NM` / `BORR_SSN_LST4`.
  The loan summary's `borrowerName` is resolved at request time from
  `borrower-service`.
- `BorrowerDto` no longer embeds a `loans` list. A borrower's loans are fetched
  from `loan-service` via `GET /api/borrowers/{id}/loans`.

## Inter-service communication

Spring `RestClient` replaces the monolith's in-process joins:

- `loan-service` → `borrower-service` (borrower name) and `product-service`
  (product description) to enrich `LoanSummaryDto`; results are cached per request
  to limit calls.
- `loan-service` → `payment-service` to serve `GET /api/loans/{id}/payments`.
- `borrower-service` → `loan-service` to serve `GET /api/borrowers/{id}/loans`.

All clients catch `RestClientException` and fall back (empty list, or the stored
reference id) so a service remains usable when a peer is down.

## Endpoint compatibility

See the mapping table in the root `README.md` for how the original monolith
endpoints map onto the new services. The individual monolith paths
(`/api/borrowers`, `/api/loans`, `/api/loans/{id}/payments`) are preserved on the
owning service; the one composite response (borrower **with** loans) is now two
calls, or could be recomposed by an API gateway / BFF if a single call is required.
