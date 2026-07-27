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

## Appendix: actual endpoint JSON (legacy golden vs modern)

Captured in this run: legacy golden = `mode=legacy` responses committed under `src/test/resources/golden/`; modern = `mode=modern` responses served via `ModernLoanReader` after the `verification`-profile migration populated the modern schema. With the current seed data every endpoint is byte-for-byte identical, so the structural comparison passes with zero differences.

### GET /api/loans

Structural result: **PASS** — identical.

Legacy (golden):

```json
[
  {
    "loanAccountNumber": "LN-2019-00142",
    "borrowerName": "James Mitchell",
    "productDescription": "30-Year Fixed Rate Mortgage",
    "originalAmount": 285000,
    "currentBalance": 271432.56,
    "interestRate": 4.750,
    "monthlyPayment": 1487.02,
    "status": "Active",
    "originationDate": "02/15/2019",
    "propertyAddress": "742 Elm Street, Springfield, IL 62701",
    "propertyType": "Single Family Residence"
  },
  {
    "loanAccountNumber": "LN-2020-00398",
    "borrowerName": "Sarah Chen",
    "productDescription": "15-Year Fixed Rate Mortgage",
    "originalAmount": 420000,
    "currentBalance": 312876.43,
    "interestRate": 3.125,
    "monthlyPayment": 2924.18,
    "status": "Active",
    "originationDate": "04/01/2020",
    "propertyAddress": "1100 Oak Avenue, Portland, OR 97201",
    "propertyType": "Condominium"
  },
  {
    "loanAccountNumber": "LN-2018-00089",
    "borrowerName": "Michael Torres",
    "productDescription": "5/1 Adjustable Rate Mortgage",
    "originalAmount": 195000,
    "currentBalance": 178234.12,
    "interestRate": 5.250,
    "monthlyPayment": 1077.05,
    "status": "Active",
    "originationDate": "07/01/2018",
    "propertyAddress": "305 Pine Road, Austin, TX 78701",
    "propertyType": "Single Family Residence"
  },
  {
    "loanAccountNumber": "LN-2021-00567",
    "borrowerName": "Emily Johnson",
    "productDescription": "30-Year Fixed Rate Mortgage",
    "originalAmount": 525000,
    "currentBalance": 498123.78,
    "interestRate": 3.875,
    "monthlyPayment": 2468.35,
    "status": "Active",
    "originationDate": "10/01/2021",
    "propertyAddress": "89 Maple Drive, Denver, CO 80202",
    "propertyType": "Townhouse"
  },
  {
    "loanAccountNumber": "LN-2017-00034",
    "borrowerName": "Robert Williams",
    "productDescription": "FHA 30-Year Fixed",
    "originalAmount": 165000,
    "currentBalance": 142567.90,
    "interestRate": 4.250,
    "monthlyPayment": 811.61,
    "status": "Active",
    "originationDate": "03/01/2017",
    "propertyAddress": "2200 Cedar Lane, Phoenix, AZ 85001",
    "propertyType": "Single Family Residence"
  }
]
```

Modern:

```json
[
  {
    "loanAccountNumber": "LN-2019-00142",
    "borrowerName": "James Mitchell",
    "productDescription": "30-Year Fixed Rate Mortgage",
    "originalAmount": 285000,
    "currentBalance": 271432.56,
    "interestRate": 4.750,
    "monthlyPayment": 1487.02,
    "status": "Active",
    "originationDate": "02/15/2019",
    "propertyAddress": "742 Elm Street, Springfield, IL 62701",
    "propertyType": "Single Family Residence"
  },
  {
    "loanAccountNumber": "LN-2020-00398",
    "borrowerName": "Sarah Chen",
    "productDescription": "15-Year Fixed Rate Mortgage",
    "originalAmount": 420000,
    "currentBalance": 312876.43,
    "interestRate": 3.125,
    "monthlyPayment": 2924.18,
    "status": "Active",
    "originationDate": "04/01/2020",
    "propertyAddress": "1100 Oak Avenue, Portland, OR 97201",
    "propertyType": "Condominium"
  },
  {
    "loanAccountNumber": "LN-2018-00089",
    "borrowerName": "Michael Torres",
    "productDescription": "5/1 Adjustable Rate Mortgage",
    "originalAmount": 195000,
    "currentBalance": 178234.12,
    "interestRate": 5.250,
    "monthlyPayment": 1077.05,
    "status": "Active",
    "originationDate": "07/01/2018",
    "propertyAddress": "305 Pine Road, Austin, TX 78701",
    "propertyType": "Single Family Residence"
  },
  {
    "loanAccountNumber": "LN-2021-00567",
    "borrowerName": "Emily Johnson",
    "productDescription": "30-Year Fixed Rate Mortgage",
    "originalAmount": 525000,
    "currentBalance": 498123.78,
    "interestRate": 3.875,
    "monthlyPayment": 2468.35,
    "status": "Active",
    "originationDate": "10/01/2021",
    "propertyAddress": "89 Maple Drive, Denver, CO 80202",
    "propertyType": "Townhouse"
  },
  {
    "loanAccountNumber": "LN-2017-00034",
    "borrowerName": "Robert Williams",
    "productDescription": "FHA 30-Year Fixed",
    "originalAmount": 165000,
    "currentBalance": 142567.90,
    "interestRate": 4.250,
    "monthlyPayment": 811.61,
    "status": "Active",
    "originationDate": "03/01/2017",
    "propertyAddress": "2200 Cedar Lane, Phoenix, AZ 85001",
    "propertyType": "Single Family Residence"
  }
]
```

### GET /api/loans/LN-2019-00142

Structural result: **PASS** — identical.

Legacy (golden):

```json
{
  "loanAccountNumber": "LN-2019-00142",
  "borrowerName": "James Mitchell",
  "productDescription": "30-Year Fixed Rate Mortgage",
  "originalAmount": 285000,
  "currentBalance": 271432.56,
  "interestRate": 4.750,
  "monthlyPayment": 1487.02,
  "status": "Active",
  "originationDate": "02/15/2019",
  "propertyAddress": "742 Elm Street, Springfield, IL 62701",
  "propertyType": "Single Family Residence"
}
```

Modern:

```json
{
  "loanAccountNumber": "LN-2019-00142",
  "borrowerName": "James Mitchell",
  "productDescription": "30-Year Fixed Rate Mortgage",
  "originalAmount": 285000,
  "currentBalance": 271432.56,
  "interestRate": 4.750,
  "monthlyPayment": 1487.02,
  "status": "Active",
  "originationDate": "02/15/2019",
  "propertyAddress": "742 Elm Street, Springfield, IL 62701",
  "propertyType": "Single Family Residence"
}
```

### GET /api/loans/LN-2019-00142/payments

Structural result: **PASS** — identical (payment `id` on the payments endpoint is excluded from the exact match and validated separately).

Legacy (golden):

```json
[
  {
    "paymentId": "PMT-2025120001",
    "loanAccountNumber": "LN-2019-00142",
    "paymentDate": "12/15/2025",
    "totalAmount": 1487.02,
    "principalAmount": 456.78,
    "interestAmount": 1074.69,
    "escrowAmount": 355.55,
    "lateFee": 0.00,
    "type": "Regular",
    "status": "Posted"
  },
  {
    "paymentId": "PMT-2025110001",
    "loanAccountNumber": "LN-2019-00142",
    "paymentDate": "11/15/2025",
    "totalAmount": 1487.02,
    "principalAmount": 454.97,
    "interestAmount": 1076.50,
    "escrowAmount": 355.55,
    "lateFee": 0.00,
    "type": "Regular",
    "status": "Posted"
  }
]
```

Modern:

```json
[
  {
    "paymentId": "PMT-2025120001",
    "loanAccountNumber": "LN-2019-00142",
    "paymentDate": "12/15/2025",
    "totalAmount": 1487.02,
    "principalAmount": 456.78,
    "interestAmount": 1074.69,
    "escrowAmount": 355.55,
    "lateFee": 0.00,
    "type": "Regular",
    "status": "Posted"
  },
  {
    "paymentId": "PMT-2025110001",
    "loanAccountNumber": "LN-2019-00142",
    "paymentDate": "11/15/2025",
    "totalAmount": 1487.02,
    "principalAmount": 454.97,
    "interestAmount": 1076.50,
    "escrowAmount": 355.55,
    "lateFee": 0.00,
    "type": "Regular",
    "status": "Posted"
  }
]
```

### GET /api/borrowers

Structural result: **PASS** — identical.

Legacy (golden):

```json
[
  {
    "id": "B-10001",
    "fullName": "James R. Mitchell",
    "email": "j.mitchell@email.com",
    "phone": "217-555-0142",
    "city": "Springfield",
    "state": "IL",
    "creditScore": 745,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10002",
    "fullName": "Sarah L. Chen",
    "email": "s.chen@email.com",
    "phone": "503-555-0198",
    "city": "Portland",
    "state": "OR",
    "creditScore": 780,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10003",
    "fullName": "Michael A. Torres",
    "email": "m.torres@email.com",
    "phone": "512-555-0167",
    "city": "Austin",
    "state": "TX",
    "creditScore": 692,
    "employmentStatus": "SELF-EMP",
    "loans": null
  },
  {
    "id": "B-10004",
    "fullName": "Emily M. Johnson",
    "email": "e.johnson@email.com",
    "phone": "303-555-0134",
    "city": "Denver",
    "state": "CO",
    "creditScore": 810,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10005",
    "fullName": "Robert Williams",
    "email": "r.williams@email.com",
    "phone": "602-555-0156",
    "city": "Phoenix",
    "state": "AZ",
    "creditScore": 658,
    "employmentStatus": "RETIRED",
    "loans": null
  }
]
```

Modern:

```json
[
  {
    "id": "B-10001",
    "fullName": "James R. Mitchell",
    "email": "j.mitchell@email.com",
    "phone": "217-555-0142",
    "city": "Springfield",
    "state": "IL",
    "creditScore": 745,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10002",
    "fullName": "Sarah L. Chen",
    "email": "s.chen@email.com",
    "phone": "503-555-0198",
    "city": "Portland",
    "state": "OR",
    "creditScore": 780,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10003",
    "fullName": "Michael A. Torres",
    "email": "m.torres@email.com",
    "phone": "512-555-0167",
    "city": "Austin",
    "state": "TX",
    "creditScore": 692,
    "employmentStatus": "SELF-EMP",
    "loans": null
  },
  {
    "id": "B-10004",
    "fullName": "Emily M. Johnson",
    "email": "e.johnson@email.com",
    "phone": "303-555-0134",
    "city": "Denver",
    "state": "CO",
    "creditScore": 810,
    "employmentStatus": "EMPLOYED",
    "loans": null
  },
  {
    "id": "B-10005",
    "fullName": "Robert Williams",
    "email": "r.williams@email.com",
    "phone": "602-555-0156",
    "city": "Phoenix",
    "state": "AZ",
    "creditScore": 658,
    "employmentStatus": "RETIRED",
    "loans": null
  }
]
```

### GET /api/borrowers/B-10001

Structural result: **PASS** — identical.

Legacy (golden):

```json
{
  "id": "B-10001",
  "fullName": "James R. Mitchell",
  "email": "j.mitchell@email.com",
  "phone": "217-555-0142",
  "city": "Springfield",
  "state": "IL",
  "creditScore": 745,
  "employmentStatus": "EMPLOYED",
  "loans": [
    {
      "loanAccountNumber": "LN-2019-00142",
      "borrowerName": "James Mitchell",
      "productDescription": "30-Year Fixed Rate Mortgage",
      "originalAmount": 285000,
      "currentBalance": 271432.56,
      "interestRate": 4.750,
      "monthlyPayment": 1487.02,
      "status": "Active",
      "originationDate": "02/15/2019",
      "propertyAddress": "742 Elm Street, Springfield, IL 62701",
      "propertyType": "Single Family Residence"
    }
  ]
}
```

Modern:

```json
{
  "id": "B-10001",
  "fullName": "James R. Mitchell",
  "email": "j.mitchell@email.com",
  "phone": "217-555-0142",
  "city": "Springfield",
  "state": "IL",
  "creditScore": 745,
  "employmentStatus": "EMPLOYED",
  "loans": [
    {
      "loanAccountNumber": "LN-2019-00142",
      "borrowerName": "James Mitchell",
      "productDescription": "30-Year Fixed Rate Mortgage",
      "originalAmount": 285000,
      "currentBalance": 271432.56,
      "interestRate": 4.750,
      "monthlyPayment": 1487.02,
      "status": "Active",
      "originationDate": "02/15/2019",
      "propertyAddress": "742 Elm Street, Springfield, IL 62701",
      "propertyType": "Single Family Residence"
    }
  ]
}
```
