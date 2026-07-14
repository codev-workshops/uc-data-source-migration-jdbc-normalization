# Loan Platform: Legacy Monolith → Bounded-Context Microservices

A loan management platform, originally a single Spring Boot monolith reading from a
legacy data warehouse (CDW-style H2 schema), now split into **four independently
deployable microservices** aligned to bounded contexts. Each service owns its own
schema, database, and REST API, adopts the **modern normalized schema** (proper
types, expanded codes), and references other contexts **by id** rather than by
embedded/denormalized data.

## Services

| Service            | Port | Bounded context        | Owns table       | Key endpoints |
|--------------------|------|------------------------|------------------|---------------|
| `borrower-service` | 8081 | Borrowers              | `borrowers`      | `GET /api/borrowers`, `GET /api/borrowers/{id}`, `GET /api/borrowers/{id}/loans` |
| `product-service`  | 8082 | Loan product catalog   | `loan_products`  | `GET /api/products`, `GET /api/products/{code}` |
| `loan-service`     | 8083 | Loan accounts          | `loan_accounts`  | `GET /api/loans`, `GET /api/loans?borrowerId={id}`, `GET /api/loans/{id}`, `GET /api/loans/{id}/payments` |
| `payment-service`  | 8084 | Payment history        | `payments`       | `GET /api/payments/loan/{loanId}`, `GET /api/payments?loanAccountNumber={id}` |

Each service is a standalone Spring Boot app with its own in-memory H2 database
(`borrowerdb`, `productdb`, `loandb`, `paymentdb`), seeded from its own
`schema.sql` + `data.sql`.

## Architecture

```
                 ┌───────────────────┐
                 │  borrower-service │  :8081   borrowers DB
                 └───────────────────┘
                    ▲            │  /api/borrowers/{id}/loans
   /api/borrowers/{id}          ▼
                 ┌───────────────────┐
                 │   loan-service    │  :8083   loan_accounts DB
                 └───────────────────┘
                    │            │
   /api/products/{code}         │  /api/payments/loan/{id}
                    ▼            ▼
    ┌───────────────────┐   ┌───────────────────┐
    │  product-service  │   │  payment-service  │
    │       :8082       │   │       :8084       │
    │  loan_products DB │   │    payments DB    │
    └───────────────────┘   └───────────────────┘
```

Inter-service calls (Spring `RestClient`) replace the monolith's in-process joins:

- `loan-service` enriches each loan's `borrowerName` (from `borrower-service`) and
  `productDescription` (from `product-service`), and proxies payment history from
  `payment-service`. All calls degrade gracefully — if a peer is unavailable, the
  loan is still returned with its stored reference ids.
- `borrower-service` resolves a borrower's loans by calling `loan-service` rather
  than joining a local table.

## Cross-context references

Because each service has its own database, contexts reference each other by stable
**business keys**, not by the modern schema's `BIGINT` surrogate FKs (which aren't
shareable across separate databases):

- `loan_accounts.borrower_id` = borrower `external_id` (e.g. `B-10001`)
- `loan_accounts.product_code` = product `code` (e.g. `FXD30`)
- `payments.loan_account_number` = loan `account_number` (e.g. `LN-2019-00142`)

The legacy denormalized borrower name/SSN columns on loan accounts are gone, and the
embedded loans list on the borrower payload is removed.

## Running

Build everything from the repo root:

```bash
./mvnw clean install
```

Start each service in its own terminal (order doesn't matter — calls degrade
gracefully until peers are up):

```bash
./mvnw -pl borrower-service spring-boot:run
./mvnw -pl product-service  spring-boot:run
./mvnw -pl loan-service     spring-boot:run
./mvnw -pl payment-service  spring-boot:run
```

Run all tests:

```bash
./mvnw test
```

## Mapping from the original monolith endpoints

The previous monolith exposed everything under one app on `:8080`. The equivalent
composed calls across the new services:

| Monolith endpoint                     | New location |
|---------------------------------------|--------------|
| `GET /api/borrowers`                  | `borrower-service` `GET /api/borrowers` |
| `GET /api/borrowers/{id}` (with loans)| `borrower-service` `GET /api/borrowers/{id}` (borrower) + `GET /api/borrowers/{id}/loans` (loans, via loan-service) |
| `GET /api/loans`                      | `loan-service` `GET /api/loans` |
| `GET /api/loans/{id}`                 | `loan-service` `GET /api/loans/{id}` |
| `GET /api/loans/{loanId}/payments`    | `loan-service` `GET /api/loans/{loanId}/payments` (proxies payment-service) |

## Tech Stack

- Java 17, Spring Boot 3.2, Spring Data JPA, Spring `RestClient`
- H2 (in-memory, one per service)
- Maven multi-module build

## License

MIT
