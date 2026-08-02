# Load test report

Generated 2026-08-02T08:43:16.056742109 by `scripts/run-load-test.sh`.

## What this measures

One JVM, one H2 database, one machine. Requests go through real HTTP and real
Hibernate; the write path is the internal payment-posting service, which has no HTTP
endpoint by design. **These are this machine's numbers, not a production projection.**
H2 has no network hop and no durable commit, so absolute latencies are optimistic;
the contention behaviour and the relative cost of each query shape are the useful part.

## Environment

| | |
|---|---|
| CPUs | 8 |
| Max heap | 6144 MB |
| JVM | 17.0.13 |
| OS | Linux amd64 |
| Database | H2 file-based, 500k loans / 2M payments |
| Duration per workload | 20 s |
| Concurrency | 16 threads |

## Latency and throughput

The migration is a single operation, so its `ops` column is rows written, its
`ops/s` is rows per second, and its latency columns are the duration of the whole
run rather than a distribution.

| Workload | ops | ops/s | p50 ms | p90 ms | p95 ms | p99 ms | max ms | errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Migration (500k loans, 2M payments) | 3,000,005 | 33,330 | 90009.25 | 90009.25 | 90009.25 | 90009.25 | 90009.25 | 0 |
| Read-heavy (v1 point lookup, v1 payments, v2 page, v2 keyset) | 205,328 | 10,268 | 1.03 | 3.37 | 4.60 | 7.57 | 51.63 | 0 |
| Write: payments spread across the book | 270,199 | 13,449 | 0.28 | 0.45 | 1.86 | 18.45 | 128.85 | 0 |
| Write: payments into ONE loan (worst-case contention) | 82,209 | 4,110 | 3.74 | 6.22 | 7.21 | 9.64 | 54.61 | 0 |
| Mixed 90/10 read/write | 252,752 | 12,639 | 1.02 | 2.25 | 2.96 | 5.42 | 22.19 | 0 |

## Resource usage

CPU is the share of the whole machine (100% = all 8 cores busy).

| Workload | CPU % | Heap MB | GC count | GC ms | Thread blocks | Blocked ms | Pool pending | SQL statements | Slowest SQL ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Migration (500k loans, 2M payments) | 16.9 | 786 | 108 | 1136 | 28 | 4 | 0 | 2,756 | 66 |
| Read-heavy (v1 point lookup, v1 payments, v2 page, v2 keyset) | 61.8 | 1048 | 23 | 167 | 25213 | 486 | 0 | 205,328 | 66 |
| Write: payments spread across the book | 66.1 | 1630 | 48 | 560 | 131 | 466 | 0 | 270,208 | 66 |
| Write: payments into ONE loan (worst-case contention) | 36.7 | 1105 | 26 | 104 | 2 | 0 | 0 | 225,726 | 66 |
| Mixed 90/10 read/write | 56.1 | 1246 | 32 | 264 | 26030 | 466 | 0 | 252,752 | 66 |

**Migration (500k loans, 2M payments)**: 3,000,005 rows written, 0 skipped, 0 rejected

## Capacity of this machine

- Write: payments spread across the book: **13,449 writes/s sustained (806,911/min)** at 66% of the machine, 0 errors.
- Write: payments into ONE loan (worst-case contention): **4,110 writes/s sustained (246,615/min)** at 37% of the machine, 0 errors.

The requirement is 2,000 writes/min/pod (33/s). Nothing here was starved: the
connection pool never queued, no workload saturated the CPU, and no run produced an
error, so the numbers above are what the code does on this box rather than what the
box would allow. They are still H2 lower bounds - a real database adds a network hop
and a durable commit, both of which cost more than everything measured here.

## Reading these numbers

- **Write throughput** is the figure to compare against the 2,000 writes/min/pod
  requirement (33/s). The single-hot-loan row is the pessimistic bound: every write
  contends for the same balance row, which no real book does.
- **Pool pending** above zero means requests are queueing for a connection; that is
  the first thing to raise if throughput plateaus below CPU saturation.
- **Thread blocks** count monitor contention, which on this path comes from the
  connection pool and H2's row locks rather than from application locks.
- If a workload is CPU-bound at ~100% with errors at zero, the machine is the limit.
