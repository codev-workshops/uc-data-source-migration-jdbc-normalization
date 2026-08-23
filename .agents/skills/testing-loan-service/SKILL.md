---
name: testing-loan-service
description: How to build, run and end-to-end test the Spring Boot loan-service REST API (H2 in-memory, modern normalized schema) locally.
---

# Testing the loan-service API locally

## Build & run
Maven is not preinstalled on the box; use the wrapper (fallback: `/home/ubuntu/tools/apache-maven-3.9.6/bin/mvn`).

```bash
cd <repo root>
./mvnw -DskipTests package                 # ~1 min, produces target/loan-service-1.0.0.jar
java -jar target/loan-service-1.0.0.jar > /tmp/app.log 2>&1 &
sleep 20 && curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/loans   # expect 200
```
Java 17 is required and already installed. No auth, no frontend — the app is API-only, so test with curl and/or by opening the URLs in Chrome.

## Endpoints
- `GET /api/loans`, `GET /api/loans/{accountNumber}`, `GET /api/loans/{accountNumber}/payments`
- `GET /api/borrowers`, `GET /api/borrowers/{externalId}` (detail includes nested `loans`; the list endpoint returns `loans: null`)
- `GET /api/payments/loan/{accountNumber}` — same payload as `/api/loans/{id}/payments`
- Unknown loan/borrower ids intentionally return HTTP 500 (legacy behaviour preserved); unknown loan id on the payments routes returns 200 `[]`.

## Golden-file regression check
`src/test/resources/golden/*.json` holds responses captured from the legacy implementation. Compare served JSON against them with a script that **normalizes all numbers to float** — the modern schema serializes BigDecimal with scale (`285000.00`, `4.750`) while goldens have `285000` / `4.75`. That scale difference is expected; anything else is a real regression. Always include a negative control (mutate a copy of a golden file and confirm your comparator reports the diff) so the harness isn't vacuously passing.

## Proving the data source is really the DB (not cached/hardcoded)
The H2 console at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:loandb`, user `sa`, blank password) attaches to the *same in-memory DB as the running app process*, so it is a great provenance check:
1. Run e.g. `UPDATE borrowers SET first_name='Michelangelo' WHERE external_id='B-10003';` (auto-commit is on by default).
2. Reload `GET /api/borrowers/B-10003` — the change must appear immediately.
3. Revert the UPDATE before continuing other assertions, otherwise golden comparisons will fail.
The console's table tree also visually proves which tables exist.

Additionally, `spring.jpa.show-sql=true` means `/tmp/app.log` contains every Hibernate query: `grep -ic cdw /tmp/app.log` should be 0 and `grep -oiE '(from|join) [a-z_0-9]+' /tmp/app.log | sort -u` should only list `borrowers`, `loan_accounts`, `loan_products`, `payments`.

## Known coverage gaps in the seed data
`data-modern.sql` only seeds `ACTIVE` loans and `POSTED`/`REGULAR` payments, so the other status/type label mappings (Closed, Default, Forbearance, Reversed, Non-Sufficient Funds, Pending, Extra, Partial, Prepayment) and the `propertyType` "Unknown" fallback cannot be exercised without inserting rows via the H2 console.

## Devin Secrets Needed
None — the app is local, in-memory and unauthenticated.
