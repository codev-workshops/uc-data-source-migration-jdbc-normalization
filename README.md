# Loan Service

A small Spring Boot REST API for loan data: borrowers, loan products, loan accounts and payment
history. It reads a normalized, properly typed schema in an in-memory H2 database.

The service started out on a legacy CDW-style schema (all-`VARCHAR` columns, cryptic names,
denormalized tables). How it was migrated, and what changed, is described in
[docs/DATA_SOURCE_MIGRATION_NOTES.md](docs/DATA_SOURCE_MIGRATION_NOTES.md).

## Quick start

Requires Java 17. The Maven wrapper downloads Maven itself.

```bash
./mvnw spring-boot:run   # start on http://localhost:8080
./mvnw test              # run the tests
```

## API

| Endpoint | Returns |
|----------|---------|
| `GET /api/loans` | All loans |
| `GET /api/loans/{accountNumber}` | One loan, e.g. `LN-2019-00142` |
| `GET /api/loans/{accountNumber}/payments` | A loan's payments, newest first |
| `GET /api/borrowers` | All borrowers |
| `GET /api/borrowers/{borrowerId}` | One borrower with their loans, e.g. `B-10001` |

Example: `GET /api/loans/LN-2019-00142`

```json
{
  "loanAccountNumber": "LN-2019-00142",
  "borrowerName": "James Mitchell",
  "productDescription": "30-Year Fixed Rate Mortgage",
  "originalAmount": 285000.00,
  "currentBalance": 271432.56,
  "interestRate": 4.750,
  "monthlyPayment": 1487.02,
  "status": "Active",
  "originationDate": "02/15/2019",
  "propertyAddress": "742 Elm Street, Springfield, IL 62701",
  "propertyType": "Single Family Residence"
}
```

## Database

| Table | Contents |
|-------|----------|
| `borrowers` | Borrower details; `external_id` is the API id (`B-10001`) |
| `loan_products` | Product catalog |
| `loan_accounts` | Loans, linked to a borrower and a product |
| `payments` | Payments, linked to a loan; `legacy_payment_id` is the API `paymentId` |

At startup Spring Boot creates the tables from
[`schema-modern.sql`](src/main/resources/schema-modern.sql) and loads the sample data (5
products, 5 borrowers, 5 loans, 10 payments) from
[`data-modern.sql`](src/main/resources/data-modern.sql). Connection settings are in
[`application.yml`](src/main/resources/application.yml). Pointing the app at another database
means changing `spring.datasource.*` and adding its JDBC driver to `pom.xml`.

While the app runs, the H2 console is at http://localhost:8080/h2-console (JDBC URL
`jdbc:h2:mem:modern`, user `sa`, empty password).

## Project layout

```
src/main/java/com/workshop/loanservice/
  controller/         REST endpoints (LoanController, BorrowerController)
  service/            ModernLoanService: builds API responses from the entities
  dto/                API response shapes
  modern/entity/      JPA entities and status/type enums
  modern/repository/  Spring Data repositories
src/main/resources/   application.yml, schema-modern.sql, data-modern.sql
src/test/             API snapshot test, repository tests
data/, docs/          Workshop reference: legacy and target DDL, column mappings, task list
```

## Tests

- `ApiGoldenTest` calls all 17 endpoint variants and compares each response with a saved
  snapshot in `src/test/resources/golden/`. The snapshots were taken from the legacy
  implementation before the migration, so this test guards the API contract.
- `ModernRepositoryTest` checks the schema and repositories, including edge cases: a borrower
  without loans, one borrower with several products, a missed payment month, and rejected
  duplicate or orphaned payments.

## Tech stack

Java 17, Spring Boot 3.2, Spring Data JPA, H2, Maven.

## License

MIT
