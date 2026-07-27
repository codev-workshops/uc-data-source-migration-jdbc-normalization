# Endpoint Parity Report — Legacy vs Modern (Structural)

Verifies that `mode=modern` (dual-read of the normalized schema via `ModernLoanReader`) reproduces
the legacy API contract for all 5 endpoints, using **structural** JSON comparison instead of the
previous byte-for-byte assertion.

- Test: `src/test/java/com/workshop/loanservice/verification/EndpointParityVerificationTest.java`
- Golden files: `src/test/resources/golden/` (the legacy contract)
- Result: **all 5 endpoints PASS** — `./mvnw test` green (6 tests, 0 failures).

## How this was produced

1. **Migration actually runs (not pre-populated).** The session starts with empty in-memory
   datasources. `VerificationMigrationConfig` (test-only, gated by the `verification` Spring
   profile) runs `MigrationService.initializeTracking()` then `migrate()` as an
   `ApplicationRunner` at `HIGHEST_PRECEDENCE`, before any test method, so the migration populates
   the empty modern DB. A dedicated test `verificationMigrationPopulatedTheModernDatasource()`
   asserts the modern `loan_accounts`, `payments`, and `migration_id_map` are non-empty — a guard
   so `LoanService` cannot silently fall back to legacy and make the parity check vacuous.
2. **Golden capture (mode=legacy).** Booted the app in `mode=legacy`, called all 5 endpoints, and
   wrote each raw response to `src/test/resources/golden/*.json`. Raw bytes are preserved (no
   re-serialization) so legacy number rendering such as `4.750` and `0.00` is kept verbatim.
3. **Structural comparison (mode=modern).** One recursive comparator runs for all 5 endpoints and
   checks, node-by-node with Jackson: field presence (same field set), JSON node type, and value.
   Arrays must match length and compare element-wise.
   - Numbers are compared by numeric value (`BigDecimal.compareTo`), so a pure decimal-scale
     rendering difference (e.g. `4.75` vs `4.750`) is reconciled and reported as a formatting note
     rather than masking a genuine value mismatch. Any real value difference fails.
   - Strings/booleans/null compare exactly.

## Endpoint results

| # | Endpoint | Golden file | Status | Details |
|---|----------|-------------|--------|---------|
| 1 | `GET /api/loans` | `golden/loans.json` | PASS | All fields present, same types/values. |
| 2 | `GET /api/loans/LN-2019-00142` | `golden/loan-detail.json` | PASS | Identical. |
| 3 | `GET /api/loans/LN-2019-00142/payments` | `golden/payments.json` | PASS | Identical except `paymentId`, which is excluded from the exact match and validated separately (below). |
| 4 | `GET /api/borrowers` | `golden/borrowers.json` | PASS | Identical (`loans` is `null` in both, as the list view does not expand loans). |
| 5 | `GET /api/borrowers/B-10001` | `golden/borrower-detail.json` | PASS | Identical, including the nested `loans` array. |

## Differences found

**None requiring a human decision.** With the current seed data the `mode=modern` responses are, in
fact, byte-for-byte identical to the legacy golden files (verified independently by diffing the raw
modern responses against the golden files). Concretely:

- No missing/extra fields on any endpoint.
- No JSON type differences.
- No value differences.
- **No formatting-only (decimal-scale) differences were observed.** Watched-for cases all already
  match: `interestRate` renders `4.750` in both (modern column `DECIMAL(5,3)`); amounts such as
  `lateFee` render `0.00` in both (`DECIMAL(10,2)`); whole-dollar `originalAmount` renders `285000`
  in both (the modern reader strips the `.00` via `wholeDollar(...)`). The comparator would report
  any such case as a reconciled formatting note; none occurred.

If future seed/schema changes introduce a difference, the test fails with a per-field report of the
form:

```
[<kind>] <jsonPath>
    golden(legacy): <value>
    modern       : <value>
```

so a reviewer can decide on each difference individually (value mismatches fail; decimal-scale-only
differences are listed as reconciled formatting notes and do not fail).

## Excluded / auto-generated fields and their alternate validation

| Field | Endpoint | Why excluded from exact match | Alternate validation |
|-------|----------|------------------------------|----------------------|
| `paymentId` | `GET .../payments` | Surfaced from the modern auto-generated `payments.id` primary key. Treated as a modern-only auto-generated value, so it is excluded from the exact structural match. | `excludedPaymentIdIsPresentUniqueAndCorrectlyMapped()` asserts, per payment: **present**, **non-null**, **unique** across payments, and **correct FK mapping** — the value resolves via `MigrationIdMap.findModernId(PAYMENT, id)` to a modern `Payment` whose `loanAccount.accountNumber` equals the JSON `loanAccountNumber`. |

The exclusion lives **purely in test logic** (a per-endpoint excluded-field set applied inside the
shared comparator). No modern read code was changed — `ModernLoanReader.toPaymentDto` and the DTOs
are untouched.

> Note: with the current mapping the `paymentId` also happens to match the legacy value byte-for-byte
> (the modern reader maps the modern id back to the preserved legacy id via `MigrationIdMap`), but we
> still exclude it from the exact match on principle and validate it structurally, so the suite stays
> correct if that reverse-mapping ever changes.

## Intentional / justified differences

None in the current data. The only *potential* justified differences the comparator is designed to
tolerate are pure decimal-scale rendering differences on amounts/rates (same numeric value, e.g.
`4.75` vs `4.750`); none are present today. Date rendering (`MM/dd/yyyy`) matches exactly in both
paths.

## Scope

Only test code, golden files, and this report were added/changed. Legacy and modern production read
code (`LoanService`, `ModernLoanReader`, entities, repositories, DTOs) and the migration
script/config were **not** modified.
