---
name: loan-service-h2-runtime-testing
description: Run the loan-service locally and verify Flyway data migrations through the embedded H2 console and legacy REST endpoints.
---

# Local runtime verification

## Devin Secrets Needed
None for the default local profile. Check `src/main/resources/application.properties`
if datasource configuration changes.

## Start and connect
- Use the repository blueprint's Maven configuration and run `mvn spring-boot:run`.
- Wait for `Started LoanServiceApplication` before opening `http://localhost:8080/h2-console`.
- Use driver `org.h2.Driver`, URL
  `jdbc:h2:mem:legacydw;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
  user `sa`, empty password.
- Do not accept the console's generic `jdbc:h2:~/test` default: that is not the app's database.
- The in-memory database belongs to the application JVM. A separate H2 Shell JVM with
  the same `mem:` URL will not inspect the running app's data.
- Restarting the application resets its in-memory data and reruns migrations.

## Inspect and verify
- Run SQL in the console textarea using the Run button.
- Flyway creates a case-sensitive lowercase history table and lowercase columns:
  `SELECT "version", "description", "success" FROM "flyway_schema_history" WHERE "version" IS NOT NULL ORDER BY "installed_rank";`
- H2's Flyway history can include a null-version schema-history creation row; count
  actual versioned migrations separately.
- Compare source/target row counts and explicit converted literals, not just migration success.
- Query `INFORMATION_SCHEMA.COLUMNS`, `TABLE_CONSTRAINTS`, `CHECK_CONSTRAINTS`,
  and `INDEX_COLUMNS` for runtime metadata.
- For negative write tests, disable autocommit, verify expected constraint rejection,
  roll back, and explicitly restore `SET AUTOCOMMIT TRUE`.
  Verify with `SELECT AUTOCOMMIT();` and reread original values.
- At some console versions, toolbar actions can replace the header frame. SQL
  `ROLLBACK;` and `SET AUTOCOMMIT TRUE;` in the editor are convenient alternatives.

## REST regression
Discover routes from the controller classes before testing. Current GET routes:
`/api/borrowers`, `/api/borrowers/{id}`, `/api/loans`,
`/api/loans/{id}`, `/api/loans/{id}/payments`.
Direct browser navigation works without auth. Chrome's Pretty-print checkbox makes
JSON readable; pretty printing can strip insignificant decimal trailing zeroes.
Application Hibernate SQL logging can establish whether requests read legacy or
normalized tables.
