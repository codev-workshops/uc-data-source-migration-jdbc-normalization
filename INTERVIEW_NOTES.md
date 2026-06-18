# Interview Notes — Loan Service Data Source Migration

Discussion-oriented companion to `EXECUTIVE_SUMMARY.md` and
`DATA_SOURCE_MIGRATION_NOTES.md`. Organized as talking points with the "why"
behind each decision, plus likely follow-up questions and answers.

---

## 1. Problem in one sentence

Migrate a read-only loan API from an untyped, denormalized legacy warehouse schema
to a typed, normalized modern schema **without changing a single byte of the
public API**, and make the cutover safe and reversible.

### Why it's non-trivial
The legacy service does presentation *and* parsing at read time. The API output is
an emergent property of messy data (e.g. amounts keep whatever scale the source
string had). "Keep the API identical" means reproducing those quirks from clean
data — the hard part is fidelity, not the mapping.

---

## 2. Architecture before → after

**Before:** `Controllers → LoanService (string parsing + code expansion) → legacy
CDW repos → one VARCHAR-only data source`.

**After:** `Controllers → LoanService (facade) → LoanDataProvider {legacy|modern}`,
two fully-wired data sources, and a `MigrationService` that ETLs legacy → modern.
Controllers/DTOs unchanged.

The key structural move: **extract a read-side interface** so two implementations
can coexist and be compared, rather than mutating the existing service in place.

---

## 3. Migration strategy — and why this order

Baseline (golden) → modern schema → migrate data → rewire behind flag → reconcile.

- **Baseline before touching anything** is the single most important step:
  you cannot prove "identical" without a captured "before". Everything after is
  measured against it.
- **Schema before data before rewire** keeps each step independently verifiable
  and reversible. The modern schema can exist empty; the migration can run without
  the API depending on it; the flag flips only once parity is proven.

---

## 4. Feature flag approach (deep dive)

```
loanservice.datasource = legacy   (default) → LegacyLoanDataProvider
loanservice.datasource = modern             → ModernLoanDataProvider
```

- `LoanDataProvider` is the seam — five methods, one per endpoint's data need.
- `LegacyLoanDataProvider` is the original `LoanService` logic moved **verbatim**
  (so the baseline behavior is preserved exactly, not reimplemented).
- `ModernLoanDataProvider` reads the modern schema and re-applies the legacy
  presentation rules.
- `LoanService` resolves the provider once at construction from the flag and logs
  it; controllers inject `LoanService` and never know which source is active.

**Why a flag instead of just swapping the implementation?**
- **Reversibility:** rollback is a config change, no redeploy of code logic.
- **Comparability:** both paths run in the same build, enabling A/B / shadow
  comparison and the cross-source reconciliation tests.
- **Incremental cutover:** you can flip in lower environments first.

**Likely follow-ups**
- *Per-request or dynamic switching?* Currently per-boot (constructor). Could be
  made per-request by resolving the provider inside each method from a request/
  config source — easy extension, deliberately kept simple here.
- *Why default to legacy?* Safety: nothing changes for callers until modern is
  explicitly enabled and validated.

---

## 5. Reconciliation approach (deep dive)

Two independent comparison axes:

1. **Against the baseline (golden files), in both modes.** Proves each path
   matches the historical contract.
2. **Against each other (cross-source).** `CrossSourceReconciliationTest` drives
   both providers directly (no HTTP) and asserts identical output per endpoint,
   *and* that monetary totals reconcile (sum of original amounts, balances,
   monthly payments, and per-loan payment totals).

**Why compare to each other and not just to golden?** The golden files are a
point-in-time snapshot; comparing the two providers directly catches divergence
even if the baseline were stale, and gives a crisper failure ("legacy vs modern
differ at `$[0].propertyType`").

**Numeric-aware comparison.** `JsonCompare` compares numbers by value
(`285000` == `285000.00`) but requires exact equality for everything else. This is
the principled handling of the one known difference (see §7), not a blanket
loosening.

**Migration-level reconciliation.** `MigrationService` itself verifies row counts
post-load and fails fast; `MigrationServiceTest` adds monetary totals
(legacy parsed vs modern stored) and referential integrity.

---

## 6. Key technical decisions (with trade-offs)

| Decision | Why | Trade-off / alternative |
|---|---|---|
| Two data sources | Matches target arch; explicit boundary; supports dual-read | More JPA wiring than one DB with extra tables |
| Provider interface + flag | Coexistence, A/B, instant rollback | Slightly more code than in-place rewrite |
| Store canonical, present legacy | Clean schema; contract preserved on read | Presentation logic lives in the modern provider |
| Fail-fast FK resolution | No silent data loss | Migration aborts on bad data (intended) |
| Idempotent migrate (clear first) | Safe re-runs in dev/test | Full reload each run (fine at this scale) |
| Preserve `external_id` | API ids map to legacy ids, not new PKs | Extra column, but required for contract |
| Numeric-aware JSON compare | Treats equal numbers as equal | Doesn't assert byte-identical numerics |

---

## 7. Risks encountered (and how each was handled)

- **Presentation fidelity** — dates as `MM/DD/YYYY`, title-case statuses
  (`"Active"` not `ACTIVE`), property type expanded to `"Single Family Residence"`,
  `fullName` middle-initial rule, ids from `external_id`. → Reproduced in the
  modern provider; locked down by golden + cross-source tests.
- **`BigDecimal` scale drift** — `DECIMAL(12,2)` returns `285000.00` vs legacy
  `285000`. → Numerically identical; documented as the single justified
  difference. A global `stripTrailingZeros()` was rejected because it would break
  the fields legacy emits *with* trailing zeros (`0.00`, `1076.50`).
- **Lazy loading across the modern persistence context** — FK associations would
  fail during serialization. → Modern reads run in a read-only modern transaction
  so associations initialize while DTOs are built.
- **Multi-data-source wiring** — primary/secondary `EntityManagerFactory`,
  package-scoped repositories, dedicated schema initializer for the non-primary DS.
- **Build/setup blockers** — invalid `pom.xml` parent tag and a non-idempotent
  `schema-legacy.sql` (second Spring context hit "table already exists"). → Fixed
  early and flagged.

---

## 8. Lessons learned

- **Capture the baseline before you change anything.** "Identical output" is only
  meaningful against a recorded "before"; the golden tests were the backbone of
  every later step.
- **Legacy output quirks are part of the contract.** Variable decimal scales and
  code-expansion strings are emergent from messy data; reproducing them from clean
  data is the real work. Decide explicitly which quirks to preserve vs. document.
- **A feature flag turns a risky cutover into a reversible toggle** and unlocks
  side-by-side reconciliation you wouldn't otherwise have.
- **Keep the schema clean and push presentation to the edge.** Storing canonical
  values and translating on read keeps the modern model honest.
- **Reconcile two ways** — to the baseline and across sources — to catch both
  contract drift and implementation divergence.
- **Fail fast on data integrity** during migration; silent FK fallbacks hide data
  loss.
- **Small environment issues block everything** — fix build/setup correctness
  first, and make schema initialization idempotent.

---

## 9. If I had more time (beyond Task 4)

- Flip the default to `modern` after a real-environment bake-in (Task 5), then
  deprecate/remove the legacy entities, repositories, and `LegacyLoanDataProvider`.
- Add SQL-level reconciliation queries (legacy vs modern) and a performance
  comparison (VARCHAR-everything vs typed/indexed).
- Fix the README endpoint discrepancy
  (`GET /api/payments/loan/{loanId}` documented vs implemented
  `GET /api/loans/{loanId}/payments`).
- Consider per-request flag resolution and a shadow-read mode that logs diffs in
  production traffic.
