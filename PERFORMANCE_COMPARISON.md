# Performance Comparison — Legacy vs Modern Read Path

Latency comparison of the 5 API endpoints under the two `datasource.mode`
settings, on identical (migrated) data.

## Methodology

- Build: `./mvnw -DskipTests package` → `target/loan-service-1.0.0.jar`
  (Java 17.0.13, Spring Boot 3.2, in-memory H2 for both datasources).
- The app was booted once per mode (`--datasource.mode=modern`, then
  `--datasource.mode=legacy`) on the same machine.
- Per endpoint: 50 warm-up requests, then 200 measured sequential requests via
  `curl` (`%{time_total}`, includes full HTTP round trip on localhost).
- Dataset: the seeded workshop data (5 borrowers / 5 products / 5 loans /
  10 payments) migrated to the modern schema at startup.

## Results (milliseconds)

### Modern mode (`datasource.mode=modern`, operative default)

| Endpoint | Requests | Mean | p50 | p95 | Min | Max |
|---|---|---|---|---|---|---|
| `GET /api/loans` | 200 | 2.11 | 2.05 | 2.58 | 1.69 | 3.49 |
| `GET /api/loans/LN-2019-00142` | 200 | 1.72 | 1.71 | 1.91 | 1.29 | 3.24 |
| `GET /api/borrowers` | 200 | 1.42 | 1.41 | 1.66 | 0.92 | 2.77 |
| `GET /api/borrowers/B-10001` | 200 | 1.82 | 1.81 | 2.02 | 1.35 | 3.48 |
| `GET /api/loans/LN-2019-00142/payments` | 200 | 1.66 | 1.50 | 2.76 | 1.03 | 6.50 |

### Legacy mode (`datasource.mode=legacy`, dual-read fallback)

| Endpoint | Requests | Mean | p50 | p95 | Min | Max |
|---|---|---|---|---|---|---|
| `GET /api/loans` | 200 | 2.11 | 2.06 | 2.64 | 1.57 | 3.29 |
| `GET /api/loans/LN-2019-00142` | 200 | 1.52 | 1.49 | 1.93 | 1.09 | 2.81 |
| `GET /api/borrowers` | 200 | 1.43 | 1.42 | 1.61 | 1.06 | 3.41 |
| `GET /api/borrowers/B-10001` | 200 | 1.85 | 1.83 | 2.04 | 1.39 | 3.04 |
| `GET /api/loans/LN-2019-00142/payments` | 200 | 1.41 | 1.37 | 1.73 | 0.98 | 2.54 |

## Analysis

- **The two read paths are effectively equivalent** at this dataset size:
  mean latencies differ by well under half a millisecond on every endpoint,
  within run-to-run noise for localhost HTTP benchmarking.
- This is expected. With 5–10 rows per table in an in-memory H2, endpoint
  latency is dominated by HTTP/Jackson/Spring MVC overhead rather than data
  access, and the legacy path's per-row string parsing
  (`BigDecimal`/date parsing in `LegacyLoanDataProvider`) is too cheap at
  this scale to register.
- **Where the modern schema wins structurally** (would matter at production
  scale, not measurable here):
  - No per-request string→type parsing: parsing happens once at migration
    time instead of on every read.
  - Typed, indexed columns and real foreign keys enable index-backed joins
    and range scans (e.g. `payments.loan_account_id`,
    `loan_accounts.borrower_id`), versus string-key lookups on the
    denormalized CDW tables.
  - Malformed-value failures cannot occur on the read path (they're rejected
    or skipped at migration time), removing a whole class of tail-latency /
    error cases the legacy parser has to guard against.
- **Conclusion**: the cutover is performance-neutral at current data volume
  and structurally favorable as data grows; there is no performance reason to
  keep serving from legacy mode.
