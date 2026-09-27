# Perf Run Agent

Instructions for the agent that orchestrates the legacy-vs-normalized performance benchmark in
this directory. The root `AGENTS.md` applies as well: branch from `master` and target `master`
in pull requests, never `main`.

## Responsibility

Run `perf-module/launch.sh`, which benchmarks every read endpoint against both the legacy
(`CDW_*`) and normalized paths through the `serviceImpl` query parameter, and collect the results
from `perf-module/results/`.

## Required order of operations

1. **Build the app.** From the repository root run `mvn -B package -DskipTests` (or
   `mvn -B compile`). Do not start a perf run on code that does not compile.
2. **Start the Spring Boot application and wait until it is ready.** `launch.sh` does this: it
   starts `mvn spring-boot:run` in the background, logs to `perf-module/results/app.log`, and
   polls `GET ${BASE_URL}/api/loans` until it returns HTTP 200 (up to `READY_TIMEOUT` seconds).
   **Never launch K6 before the app is up**; if you start the app yourself, poll the same
   endpoint before running `k6 run perf-module/scripts/loan-endpoints.js`.
3. **Only then launch the K6 perf test** via `perf-module/launch.sh` (which runs
   `k6 run perf-module/scripts/loan-endpoints.js`). K6 ramps `0 -> VUS` over `RAMP_UP`, holds
   for `STEADY_DURATION`, then ramps down over `RAMP_DOWN`.
4. **Tear the app down and collect results.** `launch.sh` stops the app on exit (also on
   failure or interrupt). Read `perf-module/results/comparison.txt` and `summary.txt`; keep
   `app.log` if the run failed. Confirm no `LoanServiceApplication` process is left running.

## Environment variables

All have defaults; override only when the run needs it (for example a longer `RAMP_UP` to let
the JVM warm up, or fewer `VUS` on a small machine).

| Variable | Default |
| --- | --- |
| `BASE_URL` | `http://localhost:8080` |
| `PARAM_NAME` | `serviceImpl` |
| `VUS` | `10` |
| `RAMP_UP` | `30s` |
| `STEADY_DURATION` | `1m` |
| `RAMP_DOWN` | `10s` |

Example: `RAMP_UP=1m VUS=20 perf-module/launch.sh`. `PARAM_NAME` must stay `serviceImpl`
unless `routing/ServiceSelectionInterceptor.PARAM` changes.

## Pass/fail rules

- `launch.sh` exits with K6's exit code. A non-zero exit means a threshold was breached; report
  the run as failed.
- The run **must be failed** if the `routed to requested impl` check fails. It means requests
  with `serviceImpl=legacy` were not served by the legacy service (missing legacy path, wrong
  parameter name, interceptor not registered), so the legacy and normalized numbers are not
  comparable. Do not report latency deltas from such a run.
- The `status is 200` and `body is non-empty` checks must also pass for both implementations.

## Reporting

Summarize `comparison.txt` (p95 and avg per endpoint, delta `normalized - legacy`), the load
profile used, and whether all thresholds passed. Do not commit anything under
`perf-module/results/` except `.gitkeep`.
