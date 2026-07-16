# Data Source Migration Notes

Reference decisions for migrating the loan-service application from the legacy CDW
(`CDW_*`) tables to the modern normalized schema. Written **before** the migration so the
eventual work (new entities, repositories, ETL, service rewire, golden-file tests) preserves
API behavior.

**Sources of truth**
- **Expansion output strings:** `com.workshop.loanservice.service.LoanService`
  (`expandStatusCode`, `expandPropertyType`, `expandPaymentType`, `expandPaymentStatus`).
- **Date format:** the seed data (`src/main/resources/data-legacy.sql`), which uses
  `MM/DD/YYYY`.
- **Golden baselines:** captured live API responses in `src/test/resources/golden/`
  (see below).

---

## 1. Verification baseline (legacy code)

Before any doc changes, the legacy app was confirmed to build and run cleanly:

- `./mvnw test` → `LoanServiceApplicationTests.contextLoads()` **passes**
  (`Tests run: 1, Failures: 0, Errors: 0`).
- `schema-legacy.sql` and `data-legacy.sql` apply on startup with no errors; the Spring
  context loads and Tomcat starts on port 8080.
- All REST endpoints return data (captured as golden baselines):
  - `GET /api/loans`
  - `GET /api/loans/{id}`
  - `GET /api/borrowers`
  - `GET /api/borrowers/{id}`
  - `GET /api/loans/{loanId}/payments`

> **Build fix required:** `pom.xml` used an invalid `<relativeTo/>` element inside `<parent>`
> (Maven does not recognize this tag, so the POM failed to parse and nothing could build). It
> was corrected to the valid `<relativePath/>`. The `mvnw`/`mvnw.cmd` wrapper scripts were also
> missing from the repo (only `.mvn/wrapper/maven-wrapper.properties` was present) and were
> regenerated so `./mvnw` works.

Golden baseline files (byte-for-byte reference for the eventual golden-file tests):

```
src/test/resources/golden/loans.json
src/test/resources/golden/loan_LN-2019-00142.json
src/test/resources/golden/borrowers.json
src/test/resources/golden/borrower_B-10001.json
src/test/resources/golden/payments_LN-2019-00142.json
```

---

## 2. Date format — `MM/DD/YYYY`

- Legacy dates are stored as `MM/DD/YYYY` strings (e.g. `02/15/2019`, `12/15/2025`), **not**
  `YYYY-MM-DD`.
- Parse legacy → modern with the pattern `MM/dd/yyyy`.
- `LoanSummaryDto.originationDate` and `PaymentDto.paymentDate` are currently `String` fields
  that pass the raw legacy value straight through, so today's API emits `MM/DD/YYYY` verbatim.

---

## 3. Expansion output strings that MUST be preserved

The modern DB columns store enum-style `UPPER_CASE` codes (`ACTIVE`, `CLOSED`, `POSTED`, …).
The **REST API JSON** uses title-case, long-form strings produced by `LoanService`. To keep
API parity, the rewired service layer must emit **exactly** these strings:

| Field | Legacy code | Exact API output (preserve) |
|-------|-------------|------------------------------|
| Loan `status` | `ACT` | `Active` |
| Loan `status` | `CLO` | `Closed` |
| Loan `status` | `DFT` | `Default` |
| Loan `status` | `FRB` | `Forbearance` |
| Loan `status` | `null` / unmapped | `Unknown` / raw code passthrough |
| `propertyType` | `SFR` | `Single Family Residence` |
| `propertyType` | `CND` | `Condominium` |
| `propertyType` | `MFR` | `Multi-Family Residence` |
| `propertyType` | `TWN` | `Townhouse` |
| `propertyType` | `null` / unmapped | `Unknown` / raw code passthrough |
| Payment `type` | `REG` | `Regular` |
| Payment `type` | `EXT` | `Extra` |
| Payment `type` | `PRT` | `Partial` |
| Payment `type` | `PRE` | `Prepayment` |
| Payment `type` | `null` | `Unknown` |
| Payment `status` | `PST` | `Posted` |
| Payment `status` | `REV` | `Reversed` |
| Payment `status` | `NSF` | `Non-Sufficient Funds` |
| Payment `status` | `PND` | `Pending` |
| Payment `status` | `null` | `Unknown` |

There is **no** `PAID_OFF` status and there are **no** single-character (`'A'`/`'P'`) codes —
all codes are the 3-letter forms above.

---

## 4. Payment ID decision — preserve legacy value

- `PaymentDto.paymentId` is currently sourced from the legacy `PMT_SEQ_NBR`
  (`LoanService.toPaymentDto` → `dto.setPaymentId(pmt.getPaymentSequenceNumber())`), producing
  values like `"PMT-2025120001"`.
- The modern `payments.id` is an auto-increment `BIGINT` surrogate key and will **not**
  reproduce those values.
- **Decision (preserve parity):** a `legacy_payment_id VARCHAR(20) UNIQUE` column has been
  added to the modern `payments` table (`data/modern-schema/modern_tables.sql`). The ETL must
  populate it from `PMT_SEQ_NBR`, and the rewired service must source `paymentId` from
  `legacy_payment_id`. This keeps `paymentId` output byte-for-byte identical.
- If this column were dropped, `paymentId` output would change — a parity break that would have
  to be documented and accepted instead.

---

## 5. Accepted divergence — `origination_date` (and other date) serialization

- **What changes:** once the string date fields migrate to `LocalDate`/`DATE`, Jackson
  serializes them as ISO-8601 (`YYYY-MM-DD`). Example: `originationDate` changes from
  `"02/15/2019"` (current, `MM/DD/YYYY`) to `"2019-02-15"` (post-migration, ISO-8601). The same
  applies to `PaymentDto.paymentDate`.
- **Status: ACCEPTED, EXPLICIT DIVERGENCE.** ISO-8601 is the correct, unambiguous wire format
  and the intended benefit of moving off string-typed dates. Golden-file comparisons for date
  fields should expect ISO-8601 output post-migration and treat this as a known, justified
  difference (not a regression).
- If strict `MM/DD/YYYY` output parity were ever required instead, it would need an explicit
  Jackson `@JsonFormat(pattern = "MM/dd/yyyy")` on the DTO date fields — not the default.

---

## Scope note

This document covers **only** the pre-migration reconciliation (verification + doc fixes +
the optional `legacy_payment_id` schema column). The actual migration — new modern entities,
repositories, ETL, service rewiring, and golden-file tests — is a separate follow-up
(see `docs/MIGRATION_TASKS.md`).
