---
name: testing-loan-service
description: How to run and end-to-end test the loan-service API locally (dual H2 data sources, legacy vs modern provider modes, byte-identical JSON checks, H2 console inspection).
---

# Testing loan-service locally

No credentials, no external services. Java 17 + the bundled Maven wrapper is all that is needed;
Maven dependencies are already in `~/.m2` in the standard Devin box, so `./mvnw -DskipTests package`
works offline.

## Run the app

```bash
# modern (default) mode
nohup ./mvnw spring-boot:run > /tmp/run-modern.log 2>&1 &
# legacy CDW mode
nohup ./mvnw spring-boot:run -Dspring-boot.run.arguments=--loanservice.datasource.mode=legacy > /tmp/run-legacy.log 2>&1 &
```
Port 8080, both H2 databases in-memory (`jdbc:h2:mem:legacydw`, `jdbc:h2:mem:moderndw`), so the
legacy→modern migration reruns on every boot. Readiness: poll `curl -s -o /dev/null http://localhost:8080/api/loans`.
Startup evidence: `grep "Migration report" /tmp/run-*.log`.

Gotchas:
- **Never put `pkill -f "spring-boot:run"` in the same shell command that also launches
  `./mvnw spring-boot:run`** — `pkill -f` matches the shell's own command line and kills the shell
  before the app starts. Kill in one call, launch in a separate call, and obfuscate the pattern
  (e.g. `pkill -f "sprin""g-boot:run"`).
- Another agent/session may already own port 8080 (`java -jar target/loan-service-*.jar`); check
  `ss -ltnp | grep 8080` before assuming your own instance is the one answering.

## Endpoints

`GET /api/loans`, `/api/loans/{id}`, `/api/loans/{id}/payments`, `/api/borrowers`,
`/api/borrowers/{id}`, plus `GET /api/admin/reconciliation` (legacy-vs-modern counts + per-endpoint
JSON comparison; look at `matched` and `mismatches`).
Unknown ids currently return **HTTP 500** (providers throw `RuntimeException`), not 404; payments for
an unknown loan return `200 []`.

## Comparing modes / golden files

Presentation is deliberately byte-exact (`provider/PresentationFormat.java`): whole-dollar
`originalAmount` (`285000`), 2dp money, **3dp rates (`4.750`)**, `MM/DD/YYYY` dates, title-case
statuses. Therefore:
- Compare **raw bytes** (`diff` on the curl output), never JSON-reparsed output — `json.tool`/`jq`
  normalise `4.750` to `4.75` and hide real scale bugs.
- Golden fixtures live in `src/test/resources/golden/` and must equal the modern-mode bytes exactly.
- For mode-vs-mode diffs, normalise only the `"timestamp"` field of Spring's HTTP-500 error bodies.
- Capture the whole seed surface, not just the sample ids: loans `LN-2019-00142`, `LN-2020-00398`,
  `LN-2018-00089`, `LN-2021-00567`, `LN-2017-00034`; borrowers `B-10001`..`B-10005`.

## Inspecting / manipulating the in-memory DBs

`/h2-console` (enabled by default): JDBC URL `jdbc:h2:mem:moderndw` (or `legacydw`), user `sa`, empty
password. Useful for duplicate/idempotency checks
(`SELECT external_id, COUNT(*) FROM payments GROUP BY external_id HAVING COUNT(*)>1`) and for
constructing scenarios the seed data lacks — e.g. inserting a payment-less `loan_accounts` row to
test an empty payments list, or injecting a modern-only row as a negative control that
`/api/admin/reconciliation` really does report `matched=false`.

Because the DBs are in-memory, the migration's "row already exists → skipped" branch cannot be
exercised by restarting; a persistent (file-based) H2 URL or a migration trigger endpoint would be
needed. Chrome's renderer occasionally freezes on these plain-JSON pages — fall back to `curl` for
the assertion and note it, rather than retrying indefinitely.
