# Phase 0 — Architecture Analysis

Analysis-only deliverable. **No application code has been written or modified** apart from one
build-blocking fix (see [Finding F1](#f1-the-build-is-broken)). Diagrams live in
[`README.md`](../README.md#architecture); this document holds the narrative, the measurements and
the decisions.

Contents:
1. [As-is architecture](#1-as-is-architecture)
2. [Findings in the current code](#2-findings-in-the-current-code)
3. [Scale analysis at 500,000 rows](#3-scale-analysis-at-500000-rows)
4. [Target architecture](#4-target-architecture)
5. [Risk register](#5-risk-register)
6. [Open questions](#6-open-questions)

Agreed constraints driving this analysis (confirmed with the requester):

| Decision | Choice |
|---|---|
| API contract | v1 (`/api/*`) stays **byte-identical and unbounded**; paginated endpoints are added as **`/api/v2/*`** |
| Database | H2 only; scale numbers are **H2-measured lower bounds** + engineering projection |
| Write path | No new public write endpoints; concurrency modelled via the **migration job** + an internal **payment-posting service** exercised by tests/load tests |
| Cutover | **Dual-write / dual-read with backfill and a reconciliation window** (so the feature flag is core, not a bonus) |
| Caching | **Caffeine**, in-process, no Redis |
| PII | Preserve `BORR_SSN_ENCR` value as-is into `ssn_hash`; **do not** change the crypto |
| Legacy code | Legacy entities/repositories are **kept** (deprecated), not deleted |

---

## 1. As-is architecture

Three layers, one datasource, no writes:

`LoanController` / `BorrowerController` → `LoanService` → 4 `JpaRepository` interfaces → single H2
in-memory datasource seeded from `schema-legacy.sql` + `data-legacy.sql`.

Everything in the legacy entities is a `String`. All type knowledge lives in `LoanService`'s private
helpers (`parseLegacyAmount`, `parseLegacyDecimal`, `parseLegacyInteger`, `expandStatusCode`,
`expandPropertyType`, `expandPaymentType`, `expandPaymentStatus`). That is the code the migration
deletes — the value of the migration is that the *database*, not the service, holds the types.

Endpoints actually exposed (verified against the controllers, **not** the README):

| Method | Path | Service call | Repository access |
|---|---|---|---|
| GET | `/api/loans` | `getAllLoans()` | `loanProductRepository.findAll()` + `loanAccountRepository.findAll()` |
| GET | `/api/loans/{id}` | `getLoanById()` | `findById` ×2 |
| GET | `/api/loans/{loanId}/payments` | `getPaymentsByLoan()` | `findByLoanAccountNumberOrderByPaymentDateDesc` |
| GET | `/api/borrowers` | `getAllBorrowers()` | `borrowerRepository.findAll()` |
| GET | `/api/borrowers/{id}` | `getBorrowerById()` | `findById` + `findAll()` (products) + `findByBorrowerId` |

Seed volume: 5 borrowers, 5 products, 5 loan accounts, 10 payments.

Runtime dependency facts (from `mvn dependency:tree`): Spring Boot 3.2.3, Spring Framework 6.1.4,
Hibernate ORM 6.4.4.Final, Spring Data JPA 3.2.3, **HikariCP 5.0.1**, Jackson 2.15.4, Tomcat 10.1.19,
H2 2.2.224, Logback 1.4.14. Micrometer **observation** (1.12.3) is already on the classpath via
`spring-web`, but there is **no actuator and no metrics registry** — nothing is measured today.

No dependency cycles exist; layering is clean (controller → service → repository). Controllers do not
touch repositories directly. The only structural smell is that `LoanService` is simultaneously the
mapper, the type-conversion utility and the orchestrator — Phase 1 splits those.

---

## 2. Findings in the current code

### F1: the build is broken

`pom.xml` line 11 contains `<relativeTo/>`, which is not a POM element (the correct tag is
`<relativePath/>`). Maven refuses to read the project:

```
Malformed POM ... Unrecognised tag: 'relativeTo' (position: START_TAG ...) @11:22
```

`./mvnw` from the README's Quick Start does not exist either: `.mvn/wrapper/maven-wrapper.properties`
is present, the wrapper JAR is `.gitignore`d and the `mvnw`/`mvnw.cmd` scripts were never committed.
**Nobody has ever built this repo as checked in.** The one-character POM fix is included in this
branch because no measurement or analysis is possible without it; the Maven wrapper will be committed
in Phase 1.

### F2: documented API ≠ implemented API

`README.md` advertises `GET /api/payments/loan/{loanId}`; the code exposes
`GET /api/loans/{loanId}/payments`. Per the agreed constraint, the **code** wins and the README is
corrected.

### F3: unbounded reads

`getAllLoans()` and `getAllBorrowers()` are unbounded `findAll()`. Fine for 5 rows, fatal at 500k —
quantified in §3.

### F4: security posture (baseline for `SECURITY_REVIEW.md`)

- **No SQL injection vector exists today.** Every read goes through Spring Data derived queries or
  `findById`, which Hibernate compiles to bound `PreparedStatement`s. There is no `@Query`, no
  `nativeQuery = true`, no `EntityManager.createNativeQuery`, no `JdbcTemplate`, no `Statement`, and
  no string-concatenated SQL anywhere in `src/main/java` (verified by grep across the tree).
- `/h2-console` is enabled with user `sa` and a blank password.
- `getLoanById` / `getBorrowerById` throw raw `RuntimeException` whose message echoes the
  user-supplied id, producing HTTP 500 with a stack trace instead of 404.
- `spring.jpa.show-sql=true` logs every statement; nothing masks PII.
- SSN is carried as `BORR_SSN_ENCR` → `ssn_hash`. Per the agreed constraint the value is preserved
  verbatim; it is therefore **only as strong as the legacy encryption**, and that residual risk is
  recorded in §5 rather than silently fixed.

### F5: no observability

No actuator, no metrics registry, no tracing, no structured logging, no request/DB timing. There is
currently no way to answer "how slow is `/api/loans`?" except with a stopwatch.

---

## 3. Scale analysis at 500,000 rows

### 3.1 Method

A standalone JDBC harness ([`perf/ScaleBench.java`](../perf/ScaleBench.java)) builds the modern
schema exactly as specified in `data/modern-schema/modern_tables.sql`, loads **500,000 borrowers,
500,000 loan accounts, 2,000,000 payments** (4 payments/loan) and 5 products, then times each query
pattern the API actually issues (median of 5 runs after warm-up, H2 query cache disabled via
`QUERY_CACHE_SIZE=0`) and prints `EXPLAIN ANALYZE` scan counts. Raw output:
[`docs/perf/phase0-h2-scale-bench.txt`](perf/phase0-h2-scale-bench.txt).

Environment: 8 vCPU / 31 GB Ubuntu VM, OpenJDK 17.0.13, H2 2.2.224 in-memory, `-Xmx16g`, no network,
no ORM, no HTTP. **Every number below is a lower bound** — real deployments add JDBC-over-TCP,
Hibernate hydration, DTO mapping and Jackson serialisation, each typically larger than the DB time
itself.

Bulk load for reference: 500k borrowers in **3.5 s**, 500k loan accounts in **4.8 s**, 2M payments in
**10.3 s** (batched, committing every 10k rows) — **18.6 s total**, i.e. a full 500k-row migration is
a seconds-to-minutes job when batched, and an hours job if committed per row.

### 3.2 Measured results

| # | Query pattern | Baseline indexes | After proposed indexes |
|---|---|---|---|
| Q1 | v1 `GET /api/loans` — unbounded 3-table join, 500k rows | **434.7 ms** | 455.1 ms |
| Q2 | v1 `GET /api/borrowers` — unbounded, 500k rows | **109.5 ms** | 115.8 ms |
| Q3 | `GET /api/loans/{id}` by `account_number` | 0.33 ms | **0.18 ms** |
| Q4 | payments for one loan, ordered desc | 0.26 ms | **0.17 ms** |
| Q5 | v2 page 0, size 50 (`OFFSET 0`) | 0.34 ms | **0.15 ms** |
| Q6 | v2 deep page (`OFFSET 450000`) | **84.5 ms** | 92.3 ms |
| Q7 | v2 deep page, **keyset** (`WHERE id > 450000`) | **0.28 ms** | 0.18 ms |
| Q8 | `COUNT(*)` for a `Page` total | 0.05 ms | 0.05 ms |
| Q9 | filter `status='ACTIVE'` + order + limit 50 | **46.9 ms** (scanCount **475,001**) | 46.3 ms (unchanged) |
| Q10 | batch-fetch 50 borrowers (one `IN`/range query) | 0.19 ms | 0.10 ms |
| Q11 | N+1: 50 individual borrower selects | 1.00 ms | 0.58 ms |

### 3.3 Are the indexes in `modern_tables.sql` sufficient?

**No — three are wrong for the workload and two are missing.** Index by index:

| Index | Verdict | Evidence / reasoning |
|---|---|---|
| `borrowers(external_id)` UNIQUE (implicit) | **Keep — essential.** | The migration resolves 500k FKs through it; without it the backfill is O(n²). |
| `loan_accounts(account_number)` UNIQUE (implicit) | **Keep — essential.** | Serves `GET /api/loans/{id}` (Q3, 0.18 ms) and every payment lookup. |
| `idx_borrowers_email` | **Drop.** | No query filters by email. Pure write-side tax on a table that the dual-write path writes to; it also indexes PII. |
| `idx_borrowers_status` | **Drop or make composite.** | Same low-selectivity problem as Q9: ~90 % of rows are `ACTIVE`. |
| `idx_loan_accounts_borrower` | **Keep.** | Serves `GET /api/borrowers/{id}`'s loan list. |
| `idx_loan_accounts_status` | **Ineffective — replace.** | Q9's `EXPLAIN ANALYZE` shows `scanCount: 475,001` for a `LIMIT 50` query: H2 walks every `ACTIVE` row because the index carries no ordering key. 46 ms for 50 rows. |
| `idx_payments_loan` | **Replace with composite.** | Superseded by `(loan_account_id, payment_date DESC)`, which also serves the `ORDER BY`. |
| `idx_payments_date` | **Drop unless a date-range report exists.** | No current query filters by `payment_date` alone; on 2M+ rows this is the most expensive index to maintain. |
| **`payments(loan_account_id, payment_date DESC)`** | **Add.** | Covering index for the payment-history endpoint; removes the sort. |
| **`loan_accounts(status, id)`** | **Add.** | Intended to give Q9 an ordered index scan. |

Two honest caveats on the "after" column:

- The composite `payments` index shows **no measurable gain here** (0.26 → 0.17 ms) because the seed
  shape is only 4 payments per loan, so the sort is trivial. Its value appears at realistic history
  depth (24–360 rows/loan); I have not manufactured that shape, so I am **not** claiming a measured
  win — the justification is structural (index-provided ordering), not empirical.
- `loan_accounts(status, id)` **did not change Q9 at all** (46.9 → 46.3 ms) because H2's optimiser
  kept choosing `idx_loan_accounts_status`. On PostgreSQL the composite index would serve both the
  predicate and the ordering. This is a concrete example of an H2-specific limitation, and the
  recommendation stands for the real engine — flagged rather than presented as a proven improvement.

### 3.4 Should pagination change?

**Yes**, and the measurements make the case:

- Q1 shows the v1 unbounded loan list costs **~435 ms of pure database time** for 500k rows, before
  Hibernate builds 500k entity graphs and Jackson serialises them. At roughly 400 bytes of JSON per
  loan, one request emits **~200 MB** and holds an equivalent object graph on-heap; a handful of
  concurrent calls will OOM the pod. (The 200 MB figure is an estimate from the DTO shape, not a
  measurement.)
- Deep paging with `OFFSET` costs **84–92 ms** and grows linearly with the offset; the equivalent
  **keyset** page costs **0.28 ms** — a ~300× difference that widens as the table grows.
- `COUNT(*)` is cheap on H2 in-memory (0.05 ms) but is a full index/heap scan on PostgreSQL; `Slice`
  (no count) should be the default and `Page` (with count) opt-in.

Because v1 must not change, the plan is:

- **v1 unchanged**: same paths, same unbounded semantics, same JSON — golden-file tested. It is
  documented as *deprecated for large datasets*, and protected operationally by a metric + a
  configurable warn/guard threshold (log a `WARN` when a v1 list exceeds N rows) rather than by a
  behaviour change. **I will not silently cap v1** — that would be a contract change.
- **v2 added**: `/api/v2/loans` and `/api/v2/borrowers` with `Pageable` (default size 20, hard max
  100), `Slice` semantics by default, `?count=true` to opt into a total, and keyset pagination via
  `?afterId=` for deep traversal. Sort keys whitelisted (see the security note in §4.4).

### 3.5 Which joins become expensive?

1. **`loan_accounts → borrowers → loan_products` on every list row.** This is Q1: 435 ms as a single
   set-based join. Done lazily per row it becomes N+1 — Q11 vs Q10 measures the shape of that
   penalty (0.58 ms for 50 individual selects vs 0.10 ms for one batched query, ~6×; at 500k rows
   that is the difference between ~0.5 s and ~100 s of round-trips on a networked DB).
   *Mitigation:* `@EntityGraph`/`join fetch` on list queries, DTO projections so only the ~10 columns
   the DTO needs are read (the loan row has 26 columns), `hibernate.default_batch_fetch_size=100`,
   and `loan_products` served from cache (5 rows) instead of joined at all.
2. **`payments → loan_accounts` for history.** Cheap per loan with the composite index; expensive for
   any cross-loan aggregate.
   *Mitigation:* composite index; if aggregates appear, roll them up (below).
3. **Low-selectivity filters + ordering** (Q9). *Mitigation:* composite `(status, id)`; on a real
   engine, a partial index on the minority status.

**Structural recommendation (beyond indexes).** Keep the normalized schema as the write model, and
add read-side structures rather than de-normalizing the source of truth:

- `borrower_display_name` maintained on `borrowers` (or computed in the projection) so the loan list
  never needs the borrower join for its single name field.
- Payment rollups on `loan_accounts` (`last_payment_date`, `payment_count`, `ytd_principal`) updated
  in the same transaction as the payment insert — removes every aggregate join, costs one extra
  UPDATE on the hot row (see the concurrency trade-off in §4.5).
- Time-partitioning `payments` by `payment_date` once history exceeds a few million rows
  (declarative partitioning on PostgreSQL; not available in H2).
- Optional `loan_account_summary` read table (or materialized view) for the list endpoints, refreshed
  by the same dual-write path — worth it only if v2 list latency misses its SLO after the above.

**Strict 3NF is not automatically the right answer at this scale.** Each denormalization above is
listed with its write cost and its consistency mechanism (same-transaction update = strong
consistency, higher lock hold time; async projection = lower write cost, documented staleness).

### 3.6 Caching (Caffeine, in-process)

| Data | Cache? | Design |
|---|---|---|
| `loan_products` (5 rows, read-mostly) | **Yes — highest value.** | Load-all into a Caffeine cache, TTL 1 h + explicit eviction on write. Removes one join from every list row. |
| Borrower display fields (name/city/state) | **Yes, bounded.** | Caffeine `maximumSize=50_000`, `expireAfterWrite=5m`, keyed by `borrowerId`. At 500k borrowers a full cache is not affordable; a bounded LRU covers the hot tail. |
| `GET /api/loans/{id}` / `GET /api/borrowers/{id}` responses | **Maybe.** | Short TTL (30–60 s) keyed by id, evicted by the write path. Measure first: Q3 is already 0.18 ms, so the cache mostly saves ORM/serialisation, not DB time. |
| Payments, balances, `current_balance` | **No.** | Money must be read-your-writes fresh. Explicitly excluded. |
| v1 unbounded list responses | **No.** | Caching a 200 MB response per pod is worse than the query. |

Cross-cutting: `Caffeine` with `recordStats()` bound to Micrometer (hit ratio per cache is a required
metric); `refreshAfterWrite` + a single-flight loader to prevent stampedes on cold start; eviction
hooks on every dual-write. **Because the cache is per-pod, entries diverge across pods** — acceptable
for products and display names with short TTLs, unacceptable for anything monetary, which is exactly
why balances are excluded.

### 3.7 Alternatives to the proposed normalization

| Option | Read latency | Write throughput | Storage | Ops complexity | Migration risk | Verdict |
|---|---|---|---|---|---|---|
| **A. Strict 3NF as specified, indexes fixed, DTO projections + Caffeine** | Good for point reads (0.2 ms) and v2 pages (0.15 ms); v1 list stays slow by design | Best — narrow rows, fewest indexes | Lowest | Lowest | Lowest | **Recommended** |
| B. 3NF + payment rollup columns on `loan_accounts` | Best for summary/aggregate reads | Slightly worse — extra UPDATE on the hot loan row per payment, raising contention | +small | Low | Low | **Recommended as a follow-up** once aggregates appear |
| C. 3NF write model + separate read model (CQRS / `loan_account_summary`) | Best for list endpoints | Good — writes untouched | +1 table | Medium (projection lag, backfill, reconciliation) | Medium | Only if v2 list misses SLO |
| D. Partial denormalization (borrower name copied into `loan_accounts`, i.e. keep the legacy shape) | Good | Worse — every borrower rename touches N loans | +small | Medium | **High** — reintroduces the anomaly the migration exists to remove | **Rejected** |
| E. Partitioned/sharded `payments` | Best for large history | Good | Same | High; unavailable on H2 | Medium | Defer until payments > ~10M rows |

Recommendation: **A now, B when payment aggregates are required, C only if measurements demand it.**
What would raise confidence: the real read/write mix in production, the actual payment-history depth
per loan, and whether any reporting queries (date ranges, portfolio aggregates) exist outside these
five endpoints.

### 3.8 Concurrency evidence (feeds `CONCURRENCY_ANALYSIS.md`)

Same harness, 16 threads × 500 committed updates, H2 in-memory:

| Contention | Threads | Throughput | Max latency | Errors |
|---|---|---|---|---|
| Same row (`id=1`) | 1 / 4 / 8 / 16 | 1.9M / 1.5M / 2.0M / 2.3M writes/min | 0.4 → 6.8 → 9.0 → **24.7 ms** | 0 |
| Distinct rows | 1 / 4 / 8 / 16 | 2.4M / 4.1M / 4.8M / **5.1M** writes/min | 0.1 → 2.2 → 11.3 → 15.2 ms | 0 |

Reading these honestly: the **2,000 writes/min/pod target is ~0.04 % of what raw in-memory H2 can
absorb**, so the database is not the constraint — the constraint will be the JVM/HTTP/JPA path and
the connection pool. What the numbers *do* show is the shape of contention: serialising every write
through one hot row costs ~2.2× throughput and a **60× worse tail latency** at 16 threads versus
spreading writes across rows. That is the argument for append-only `payments` + atomic/versioned
balance updates instead of a read-modify-write on `loan_accounts`, and it is the reason option B in
§3.7 is a follow-up rather than a default. No lock timeouts or deadlocks occurred at this scale on
H2's MVStore row locks; H2 is not PostgreSQL MVCC, so this must be re-validated on the target engine.

---

## 4. Target architecture

### 4.1 Cutover: dual-write / dual-read with backfill and reconciliation

Four phases, each independently reversible:

1. **Shadow** — modern schema created; backfill job copies legacy → modern in batched chunks; reads
   still 100 % legacy.
2. **Dual-write** — every write (migration job + payment-posting service) writes both stores in the
   same transaction; reads still legacy. Reconciliation job compares row counts, checksums and
   per-field values over a rolling window and reports drift.
3. **Dual-read / shadow-read** — reads served from legacy, modern read in parallel and compared
   (sampled, async); mismatches logged as `WARN` with a metric. Flip
   `loanservice.datasource.mode=modern` per pod to shift traffic.
4. **Modern-only** — legacy reads off, legacy entities retained (deprecated) as migration inputs;
   rollback = flip the flag back.

The flag is therefore a first-class `LoanDataProvider` abstraction (`legacy` / `modern` /
`dual-read`), selected by `@ConditionalOnProperty`, not a bonus feature.

### 4.2 Proposed package structure

```
com.workshop.loanservice
├── controller            v1 (unchanged) + v2 (paginated)
├── service               LoanService (delegates to a LoanDataProvider)
├── provider              LoanDataProvider ── LegacyLoanDataProvider / ModernLoanDataProvider / DualReadLoanDataProvider
├── dto                   unchanged v1 DTOs + v2 page DTOs
├── legacy.entity/…       @Deprecated, migration input only
├── modern.entity/…       Borrower, LoanProduct, LoanAccount, Payment (+ repositories)
├── migration             extractor, transformer (CodeTranslator, LegacyValueParser), loader, validator, reconciler
├── config                datasources, JPA, Caffeine, Hikari, feature flag
├── observability         metrics, MDC/trace enrichment, PII masking
└── security              input validation, error handling
```

Dependency rule: `controller → service → provider → repository`. `migration` may use both `legacy.*`
and `modern.*`; nothing else may touch `legacy.*`. Enforced by an ArchUnit-style test.

### 4.3 Type strategy

`LocalDate` for dates, `BigDecimal` (with the DDL's scale) for money, `Integer`/`Boolean` for the
rest. DTOs keep emitting `MM/DD/YYYY` strings so v1 JSON is unchanged — formatting moves to the edge,
parsing disappears entirely.

### 4.4 Security

No native SQL, no concatenation, named parameters only; `@Pattern`-validated path variables
(`^B-\d{5}$`, `^LN-\d{4}-\d{5}$`) returning 400; whitelisted v2 sort keys (a `Sort` built from raw
input is the one realistic injection vector this design introduces, since Spring Data interpolates
property names into JPQL — it will be validated against an allow-list); `/h2-console` disabled outside
`dev`; `show-sql=false` outside `dev`; 404 via `@RestControllerAdvice` with no echoed input and no
stack traces; PII masked in logs. Guard tests enforce all of it.

### 4.5 Observability

Actuator + Micrometer/Prometheus: HTTP latency histograms (P50/P90/P95/P99) tagged by `uri` only,
Hikari and Hibernate metrics, per-repository timers, migration counters/gauges, cache hit ratio,
optimistic-lock-failure and deadlock-retry counters, trace/span ids in the MDC, structured JSON logs
with the level policy from the prompt (no per-row logging, PII masked).

---

## 5. Risk register

| # | Risk | Likelihood | Impact | Blast radius | Mitigation |
|---|---|---|---|---|---|
| R1 | v1 unbounded endpoints OOM the pod at 500k rows (435 ms DB + ~200 MB JSON) | **High** | **High** | Whole pod, not just the caller | v2 for all new consumers; row-count metric + `WARN` threshold on v1; document v1 as unsafe at scale. **Cannot be fixed without a contract change** — see Q1 |
| R2 | Silent data corruption during type conversion (comma amounts, `MM/DD/YYYY`, scale rounding) | Medium | **High** | Financial data | Strict parsers, per-field reconciliation, checksum/sum comparison, rejects never dropped silently |
| R3 | Dual-write divergence between stores during the reconciliation window | Medium | High | Data integrity | Same-transaction dual-write, reconciliation job with drift metrics, documented window, one-way rollback |
| R4 | SSN preserved as-is keeps whatever weakness the legacy encryption has | **High** (accepted) | High | Compliance | Explicitly accepted per requester decision; masked in logs, never in DTOs/metrics tags; recorded here as residual risk |
| R5 | H2 conclusions do not transfer to the real engine (optimiser, MVCC, partitioning) — already observed with `(status,id)` | **High** | Medium | Planning accuracy | Every claim labelled measured vs projected; re-validate on PostgreSQL before production |
| R6 | Migration runtime/lock footprint on 500k+ rows | Low (18.6 s measured batched) | Medium | Migration window | Chunked commits, `REQUIRES_NEW` per chunk, resumable, progress gauge |
| R7 | Hot-row contention on `current_balance` (60× tail-latency degradation measured) | Medium | Medium | Write latency | Append-only payments + versioned/atomic balance update; rollups deferred |
| R8 | Per-pod Caffeine caches diverge | Medium | Low–Medium | Read consistency | Short TTLs, monetary data never cached, eviction on write |
| R9 | Repo has never been built (F1) — unknown further breakage behind the POM error | Medium | Medium | Delivery | POM fixed, wrapper to be committed, CI added in Phase 6 |
| R10 | Load-test numbers on a single local JVM will overstate performance (no network/k8s) | **High** | Low | Reporting accuracy | Report as lower bound, state environment, publish raw output |

---

## 6. Open questions

1. **R1 is unfixable inside the agreed constraint.** v1 stays unbounded, so a single
   `GET /api/loans` at 500k rows is a ~200 MB response and a likely OOM. Do you want (a) nothing —
   accept and document, (b) a `WARN` + metric only (my default), or (c) a configurable, **off by
   default** guard rail that a deployment can opt into?
2. **v2 shape** — confirm `/api/v2/loans?page=&size=&sort=` + `?afterId=` keyset, default size 20,
   max 100, `Slice` by default with `?count=true` for totals. Should v2 DTOs keep the v1
   `MM/DD/YYYY` string dates and display strings ("Active", "Single Family Residence"), or move to
   ISO-8601 + raw enum values now that it is a new contract?
3. **Reconciliation window** — how long should dual-write run before cutover, and should the
   reconciliation job block cutover on any drift, or report and continue?
4. **Payment-posting service scope** — internal Spring bean exercised by tests/load only (my
   assumption), or also reachable over HTTP behind the `dev`/`load` profile so k6 can drive it
   realistically? The latter is needed for meaningful P99 write numbers.
5. **Index changes on a v1-frozen contract** — dropping `idx_borrowers_email` and `idx_payments_date`
   is safe for the five known endpoints but would break any unknown consumer querying by those
   columns. Confirm no such consumer exists.
