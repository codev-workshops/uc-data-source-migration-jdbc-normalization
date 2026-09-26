---
name: loan-data-mode-runtime-testing
description: Run the loan API locally in selectable data modes and inspect the in-process H2 database through its web console.
---

# Local runtime testing

Use the repository blueprint for Maven/Java dependency setup.
Start `mvn spring-boot:run` on port 8080. To select another data mode,
restart with `mvn spring-boot:run -Dspring-boot.run.arguments=--application.data-mode=legacy`.
Terminate the prior Maven process tree first to release port 8080. Each
restart creates a fresh in-memory database; reconnect the console afterward.

## Devin Secrets Needed

None for local testing. H2 username is `sa`, password is empty.

## H2 console

Open `http://localhost:8080/h2-console`, use driver `org.h2.Driver`,
JDBC URL `jdbc:h2:mem:legacydw`, and click Connect. A separate JVM's
in-memory JDBC connection cannot inspect the application's database.
Enter SQL in the textarea and click Run. Quote lower-case Flyway
identifiers, e.g. `SELECT "version", "success" FROM "flyway_schema_history"`.
Use clear count aliases such as ROW_COUNT; inspect pasted SQL if typing
drops a character before interpreting a query error as an application failure.

## Runtime contract checks

The API is unauthenticated locally. Capture status codes and raw JSON for
`/api/loans`, `/api/borrowers`, detail IDs returned by those lists,
`/api/loans/{id}/payments`, and `/api/payments/loan/{id}`.
Compare both payment aliases and all JSON values across modes, not only keys.
Use unknown loan, borrower, and payment-owner IDs separately; they need
not share the same not-found behavior.

Service identity is logged on requests, not necessarily startup. Search
logs for `NormalizedLoanService` or `LegacyLoanService`, and retain
startup logs to detect ambiguous or missing dependency injection.
Browser requests for an absent favicon may add unrelated error logs.
Java ErrorResponse timestamps may carry nanosecond precision; avoid
mistaking a Python timestamp parser's precision limitation for an API defect.
