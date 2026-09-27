# perf-module

K6 benchmark of the loan-service read endpoints, comparing the legacy `CDW_*` VARCHAR-everything
path with the normalized properly-typed path. Every endpoint is requested twice per iteration,
once with `?serviceImpl=legacy` and once with `?serviceImpl=normalized`, so the two paths are
measured side by side under identical load. This implements the "Performance comparison" bonus
task from `docs/MIGRATION_TASKS.md`.

## Layout

| Path | Purpose |
| --- | --- |
| `launch.sh` | Builds/starts the app, waits until it is ready, runs K6, stops the app |
| `scripts/loan-endpoints.js` | The K6 test (endpoints, load profile, thresholds, summary) |
| `results/` | Output: K6 summaries, raw metrics, app log (git-ignored except `.gitkeep`) |
| `AGENTS.md` | Orchestration instructions for an agent running the benchmark |

## Prerequisites

- Java 17 and Maven (the app must build: `mvn -B package -DskipTests`)
- [K6](https://grafana.com/docs/k6/latest/set-up/install-k6/) on the `PATH` (K6 is an external
  binary; it is deliberately **not** a Maven dependency)
- `curl` (used for the readiness poll)
- Network access to `jslib.k6.io` on the first run (the script imports `k6-summary` for the
  text report)
- Port `8080` free (or override `BASE_URL` and `server.port`)

## Running

From the repository root:

```bash
perf-module/launch.sh
```

With overrides (all optional):

```bash
VUS=20 RAMP_UP=1m STEADY_DURATION=3m RAMP_DOWN=15s perf-module/launch.sh
```

The K6 script can also be run on its own against an already-running app:

```bash
k6 run perf-module/scripts/loan-endpoints.js
```

### Load profile

The K6 `ramping-vus` scenario has three stages, all env-driven:

1. **Ramp-up**: `0 -> VUS` virtual users over `RAMP_UP`
2. **Steady state**: hold `VUS` for `STEADY_DURATION`
3. **Ramp-down**: `VUS -> 0` over `RAMP_DOWN`

### Environment variables

| Variable | Default | Used by | Meaning |
| --- | --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | script, launch | App base URL |
| `PARAM_NAME` | `serviceImpl` | script, launch | Routing query parameter name |
| `VUS` | `10` | script, launch | Peak virtual users |
| `RAMP_UP` | `30s` | script, launch | Duration of the ramp-up stage |
| `STEADY_DURATION` | `1m` | script, launch | Duration of the steady-state stage |
| `RAMP_DOWN` | `10s` | script, launch | Duration of the ramp-down stage |
| `RESULTS_DIR` | `perf-module/results` | script, launch | Where output files are written |
| `READY_TIMEOUT` | `120` | launch | Seconds to wait for the app to answer `READY_PATH` |
| `READY_PATH` | `/api/loans` | launch | Endpoint polled until it returns 200 |
| `SPRING_PROFILE` | `e2e-test` | launch | Spring profile for the app (quiet logging) |
| `K6_BIN` / `MVN_BIN` | `k6` / `mvn` (or `./mvnw` if present) | launch | Binaries to use |

### Readiness check

`launch.sh` polls `GET ${BASE_URL}/api/loans` until it returns HTTP 200. This avoids adding
`spring-boot-starter-actuator` to the project. If you prefer a dedicated health endpoint, add the
actuator dependency to `pom.xml` and set `READY_PATH=/actuator/health`.

## Endpoints benchmarked

| K6 `endpoint` tag | Request |
| --- | --- |
| `loans_list` | `GET /api/loans` |
| `loan_by_id` | `GET /api/loans/LN-2019-00142` |
| `loan_payments` | `GET /api/loans/LN-2019-00142/payments` |
| `borrowers_list` | `GET /api/borrowers` |
| `borrower_by_id` | `GET /api/borrowers/B-10001` |
| `payments_by_loan` | `GET /api/payments/loan/LN-2019-00142` |

Each request is tagged `impl=legacy|normalized` and `endpoint=<name>`.

## Checks and thresholds

Checks (per request):

- `status is 200`
- `body is non-empty`
- `routed to requested impl` — for the loan endpoints the response `status` must be `Active`
  for legacy and `ACTIVE` for normalized. This is the observable difference between the two
  services (see `docs/DATA_SOURCE_MIGRATION_NOTES.md`, "Task 4 validation"). If the legacy path is missing or
  the routing parameter is ignored, both requests would be served by the normalized service and
  this check fails, so a misroute cannot produce a false "identical performance" result.

Thresholds (fail the run and make `launch.sh` exit non-zero):

- `http_req_failed` rate `< 1%`
- `http_req_duration`, `latency_legacy`, `latency_normalized` `p(95) < 500ms`
- each of the three checks above `rate > 99%`

## Reading the results

After a run `perf-module/results/` contains:

| File | Content |
| --- | --- |
| `summary.txt` | K6 end-of-test summary (checks, thresholds, all metrics) |
| `comparison.txt` | Table of legacy vs normalized `p95`/`avg` per endpoint with deltas |
| `comparison.json` | Same comparison, machine-readable |
| `summary.json` | Full `handleSummary` data (every metric with all percentiles) |
| `k6-raw.json` | Raw per-request metric points (`--out json`), for custom analysis |
| `k6.log` | K6 console output |
| `app.log` | Spring Boot stdout/stderr |

The per-impl trends are `latency_legacy` / `latency_normalized` (overall) and
`latency_<impl>_<endpoint>` (per endpoint). In `comparison.txt` the delta column is
`normalized - legacy`, so a negative value means the normalized path is faster.

Note that the seeded data set is tiny (5 borrowers, 5 loans, 10 payments) and H2 is in-memory,
so absolute numbers are sub-millisecond and dominated by HTTP/serialization overhead; the
comparison is most meaningful as a relative trend across runs on the same machine.
