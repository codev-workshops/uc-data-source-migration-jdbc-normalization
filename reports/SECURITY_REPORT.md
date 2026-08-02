# Security report

Generated 2026-08-02 08:39 UTC by `scripts/security-scan.sh`.

**No findings.**

## SQL injection

Every query in the service is either a Spring Data derived query or static JPQL with bound
parameters. Nothing user-supplied is ever concatenated into a query, and the sort parameter -
the one input that does reach the query structure - is mapped through an allow list of
property names, so an unknown value is rejected with 400 rather than passed through.

| Check | Result |
|---|---|
| No `createNativeQuery` in main sources | pass |
| No `nativeQuery[[:space:]]*=[[:space:]]*true` in main sources | pass |
| No `JdbcTemplate` in main sources | pass |
| No `createStatement` in main sources | pass |
| No `Statement\.execute` in main sources | pass |
| `NoDynamicSqlSourceGuardTest` (static source guard) | pass |
| `SqlInjectionIT` (payloads through paths, sorts, paging) | pass |
| `PageRequestsTest` (sort allow list, size limits) | pass |

Payloads exercised: `' OR '1'='1`, `'; DROP TABLE loan_accounts; --`,
`LN-2019-00142' OR '1'='1`, `1 UNION SELECT ssn_hash FROM borrowers`, and sort values such as
`currentBalance,desc; DELETE FROM payments` and `id,asc) OR (1=1`. After each, row counts are
re-checked and the response is scanned for leaked values.

## Data exposure

| Control | Where |
|---|---|
| SSN hash never leaves the service (absent from v1 and v2 DTOs) | `BorrowerV2Dto`, `LoanV2Dto` |
| Hash preserved verbatim on migration - no re-hashing, no algorithm change | `LegacyToModernMigrationService#toBorrower` |
| Errors return a fixed reason and never echo the requested identifier | `ApiExceptionHandler`, `LoanNotFoundException` |
| No SSN, date of birth or income in any log statement (enforced statically) | `NoDynamicSqlSourceGuardTest` |
| H2 console disabled outside the `dev` profile | `application.properties` |
| Actuator limited to health, info, metrics and prometheus; health details hidden | `application.properties` |

## Known residual risk

`GET /api/loans` and `GET /api/borrowers` remain unbounded, by product decision: existing
clients depend on the current behaviour. At 500k rows a single call is a multi-hundred-megabyte
response and a plausible denial-of-service lever. It is metered (`loanservice.v1.large_response`)
and logged at WARN, but not capped. `/api/v2` exists as the bounded alternative.
