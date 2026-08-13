---
name: testing-loan-service
description: How to run and end-to-end test the loan-service app (legacy CDW -> modern schema migration workshop), including H2 console verification and REST endpoint checks.
---

# Testing the loan-service app

## Run locally
- Java 17 + Maven. Build/test: `mvn -o compile`, `mvn -o test` (offline flag works once deps are resolved).
- Run: `mvn spring-boot:run` (or `java -jar target/loan-service-1.0.0.jar`) in the repo root; serves on http://localhost:8080.
- Data source: single in-memory H2, `jdbc:h2:mem:legacydw`, user `sa`, blank password. Schemas are loaded via
  `spring.sql.init.schema-locations` in `src/main/resources/application.properties`; seed data from `data-legacy.sql`.
- No credentials/secrets are required for any part of this app.

## Devin Secrets Needed
None.

## H2 console verification (best visual evidence for schema changes)
1. Open http://localhost:8080/h2-console. The login form is pre-filled with the app's datasource URL
   (`jdbc:h2:mem:legacydw`, user `sa`, empty password) — just click **Connect**.
2. The left tree lists every table in the DB, which is the quickest proof a schema file was loaded
   (e.g. legacy `CDW_*` tables plus modern `BORROWERS`, `LOAN_PRODUCTS`, `LOAN_ACCOUNTS`, `PAYMENTS`).
3. Type SQL in the top textarea and click **Run**. Use a single `UNION ALL` of `COUNT(*)` per table so one
   screenshot proves all row counts at once.
4. Baseline seed data (from `data-legacy.sql`): `CDW_BORR_MSTR`=5, `CDW_LN_ACCT`=5, `CDW_PMT_HIST`=10, `CDW_LN_PROD`=5.
   Because the app runs `spring.sql.init` on every boot, the H2 console reflects the running app's DB — do not start a
   second app instance while testing or counts may look doubled/locked.

## REST endpoints (legacy-shaped DTOs)
Visiting these in the browser renders raw JSON, which is fine for evidence:
- `GET /api/borrowers` (5 items; `loans` is null in the list view)
- `GET /api/borrowers/B-10001` (`loans` populated with LN-2019-00142)
- `GET /api/loans` (5 items), `GET /api/loans/LN-2019-00142`, `GET /api/loans/LN-2019-00142/payments` (2 items)
Key values to assert: currentBalance 271432.56, monthlyPayment 1487.02, status "Active", payment PMT-2025120001.

## Proving new JPA entities actually map to a schema
Empty-table row counts do NOT prove entity↔column mapping (`spring.jpa.hibernate.ddl-auto=none`, so Hibernate
never validates the schema). To really test new entities, add a TEMPORARY `@SpringBootTest @Transactional` test that
saves an entity graph via the repositories and reads it back (`findByExternalId`, `findByAccountNumber`), then delete
the file before finishing. Insert order matters due to FKs: borrower -> loan_product -> loan_account -> payment.
