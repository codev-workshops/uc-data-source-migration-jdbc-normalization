# Target Schema Design Decisions

Rationale for the choices in `docs/proposed-target-schema.sql` and `docs/proposed-column-mappings.md`. Each section lists the options considered, the option chosen, and why. Inputs were `docs/MIGRATION_ANALYSIS.md`, the legacy DDL/seed data, and the translation logic in `LoanService`.

---

## D1. Surrogate keys vs. legacy string identifiers

**Options**
1. Keep legacy strings (`B-10001`, `LN-2019-00142`, `PMT-2025120001`) as primary keys.
2. Generate `BIGINT` surrogate PKs and drop the legacy identifiers.
3. Generate `BIGINT` surrogate PKs and keep the legacy identifiers as `NOT NULL UNIQUE` natural/business keys.

**Chosen:** 3.

**Why:** Surrogates give compact, uniform FKs and decouple the schema from legacy numbering conventions (e.g. the year embedded in `LN-2019-00142`). Keeping the legacy strings as unique columns (`legacy_borrower_id`, `account_number`, `product_code`, `legacy_payment_id`) preserves traceability for reconciliation, keeps the current REST paths (`/api/loans/{LN-…}`, `/api/borrowers/{B-…}`) resolvable without an API break, and makes ID resolution during load a simple lookup. Option 2 would break every existing client identifier; option 1 forgoes the benefits of typed keys and keeps year-encoded business meaning inside the PK.

## D2. Resolving legacy string IDs to numeric foreign keys

**Options**
1. Resolve at load time by joining on the `legacy_*` unique columns; fail the row if no parent exists.
2. Resolve at load time; auto-create a placeholder parent for orphans.
3. Keep both a string column and a numeric FK on the child and backfill later.

**Chosen:** 1, with a quarantine table for rejected rows.

**Why:** The analysis's top risk is that the legacy schema permits orphans. Auto-creating placeholders (2) would launder bad data into the new schema; keeping a dual column (3) perpetuates the manual-join pattern the migration is meant to remove. Load order is dictated by FKs: reference tables → `address` → `borrower` → `loan_product` → `property` → `loan_account` → `payment`. Orphans are written to a quarantine table with the source row and a reason code so a human can decide. With the current seed data, zero rows are expected to quarantine.

## D3. Expanding cryptic status codes

**Options**
1. Store the human label directly (`'Active'`, `'Single Family Residence'`) in the entity column.
2. Store the short code in a `VARCHAR` column constrained by a `CHECK (code IN (...))` list.
3. Database `ENUM` types.
4. Store the short code as an FK to a small reference table holding `code`, `label` and any behavioural flags.

**Chosen:** 4.

**Why:** The existing `expand*` methods in `LoanService` are effectively hard-coded reference tables; moving them into the database makes the mapping a single source of truth that SQL, the application and reporting tools all share. Reference tables also let us attach semantics that the label alone cannot (`loan_status.is_open`, `payment_status.is_final`) and let a new code be added with an `INSERT` rather than a deploy. Storing labels (1) wastes space and makes renaming a label a mass update; `CHECK` lists (2) and `ENUM`s (3) are both schema changes for every new code and carry no label. The FK also eliminates the current `default -> code` fallback that leaks raw abbreviations to end users: an unknown code is now a load-time rejection, not a silent pass-through. Codes were kept as the legacy abbreviations (`ACT`, `SFR`, …) so the mapping document stays one-to-one; the exception is `BORR_EMP_STAT`, where `SELF-EMP` is renamed to `SELF_EMPLOYED` for consistency with the other employment codes.

`PROD_STAT_CD` is the one code deliberately turned into a `BOOLEAN` (`is_active`): the only meaningful states for a product catalog entry are active/retired, and it is not exposed through any endpoint.

## D4. Duplicated borrower fields on the loan account

The legacy `CDW_LN_ACCT` re-stores `BORR_FST_NM`, `BORR_LST_NM` and `BORR_SSN_LST4`.

**Options**
1. Keep them for read performance (`GET /api/loans` shows borrower name).
2. Drop them; derive name via a join to `borrower`.
3. Drop the names; relocate `BORR_SSN_LST4` to `borrower` since it is a borrower attribute the master table currently lacks.

**Chosen:** 3.

**Why:** The analysis identifies the update anomaly (name changes not propagating to loan rows) as a Medium risk; the only fix is a single source of truth. The join cost is negligible for this data volume and is indexed (`ix_loan_account_borrower`). `BORR_SSN_LST4` is not a duplicate — it does not exist on `CDW_BORR_MSTR` — so it is moved rather than dropped. The load must pre-validate that the loan-row copies agree with the master row (and that all of a borrower's loans agree on the last-4) and report any divergence before dropping the columns; that report is the only evidence we will ever have of historic drift.

## D5. Addresses and property

**Options**
1. Inline address columns on both `borrower` and `loan_account` (mirror legacy).
2. A shared `address` table referenced by `borrower.mailing_address_id` and `property.address_id`, plus a `property` table for type/appraisal.
3. A `property` table with inline address, borrower address inline.

**Chosen:** 2.

**Why:** The analysis notes the property address duplicates the borrower address for owner-occupied loans (all five seed loans). A shared `address` table removes that duplication and gives one place to normalise/validate addresses. Splitting `property` out of `loan_account` also gives appraisal and property type a natural home and allows multiple loans (e.g. a refinance) against the same property without copying. Deduplication on load is exact-match on the five fields after trim; fuzzy matching is out of scope.

## D6. Handling malformed values

The legacy schema permits any string in any column; the current service either throws (`NumberFormatException` → HTTP 500) or silently coerces blank to `BigDecimal.ZERO`.

**Options**
1. Best-effort coercion: blank/unparseable → `NULL` or `0`, keep loading.
2. Fail the entire migration on the first bad value.
3. Row-level validation: parse strictly; a row with any unparseable required field is written to a quarantine table with the raw source and a reason, and excluded from the target; the load reports totals and continues.

**Chosen:** 3, with strict parsing rules per type:
- Dates: strict `MM/dd/yyyy` only; no leniency for `M/d/yyyy` or two-digit years unless the profiling step finds them (open question Q3).
- Amounts: strip `,` and `$`, parse `BigDecimal`, scale 2 `HALF_UP`. **Blank is `NULL`, never `0`** — reversing the current `parseLegacyAmount` behaviour, which the analysis calls out as silent financial corruption. Columns that must have a value (`current_balance`, `original_amount`, `total_amount`) are `NOT NULL`, so a blank quarantines the row.
- Codes: trim, upper-case, must exist in the reference table.
- The sentinel `12/31/2099` on `PROD_EXP_DT` maps to `NULL` ("no expiry").

**Why:** Coercion (1) is exactly the behaviour we are migrating away from. Fail-fast (2) makes a large load impossible to complete and gives no visibility into how much data is bad. Quarantine (3) is the standard ETL pattern: it keeps good data moving, keeps bad data auditable, and turns "malformed value" from a runtime 500 into a reviewable list. `CHECK` constraints in the target schema then guarantee the failure modes cannot recur after cut-over.

## D7. Numeric types and precision

**Options**
1. `DOUBLE`/`FLOAT` for money and rates.
2. `DECIMAL` with fixed scale.

**Chosen:** `DECIMAL(15,2)` for all money, `DECIMAL(6,3)` for interest rate, `DECIMAL(6,2)` for LTV, `SMALLINT`/`INTEGER` for counts.

**Why:** Floating point is unacceptable for financial figures. `(15,2)` covers up to 9,999,999,999,999.99, comfortably above the largest product maximum (1,500,000). Rate scale 3 preserves the legacy `4.750` precision exactly; LTV scale 2 preserves `82.5` and allows `82.55`. `credit_score` is `SMALLINT` with `CHECK 300–850`.

## D8. Dates and timestamps

**Options**
1. Keep dates as `VARCHAR` and parse in the application (status quo).
2. `DATE` for business dates, `TIMESTAMP` for audit columns.
3. `TIMESTAMP` everywhere.

**Chosen:** 2.

**Why:** Business dates (DOB, origination, payment) are calendar dates with no time component; `DATE` states that precisely and enables correct ordering — the payments endpoint currently sorts a `MM/DD/YYYY` *string* descending, which is only correct within a single year. Audit columns (`*_CRET_DT`, `*_UPDT_DT`) become `TIMESTAMP` so that post-migration writes can record real times; migrated rows get `00:00:00`. The API currently returns dates as raw `MM/DD/YYYY` strings, so the service layer will need to format `DATE` → string to preserve the contract (open question Q1).

## D9. Derived values kept for parity

`LN_LTV_PCT` is derivable (`original_amount / appraised_value`), and `payment.total_amount` is derivable from its split. Both are kept as stored columns.

**Why:** The DTOs expose them today; recomputing could change values by rounding and break golden-file parity tests. `ck_payment_split` enforces that the stored split reconciles to the total; LTV is cross-checked on load but not constrained, because legacy LTV may have been computed from a different appraisal than the one stored.

## D10. Sensitive data

`BORR_SSN_ENCR` is copied as an opaque ciphertext into `borrower.ssn_encrypted`; `ssn_last4` is stored in clear as today. No decryption, re-encryption or masking is performed by the migration. Column-level encryption at rest and access controls are deferred to implementation (Q5).

---

## Open questions requiring a human decision

| # | Question | Why it matters |
|---|---|---|
| **Q1** | Must the REST API keep returning dates as `MM/DD/YYYY` strings, or may it switch to ISO-8601 (`2019-02-15`)? | Determines whether the service formats `DATE` back to legacy strings or the API contract (and golden files) change. |
| **Q2** | Should `GET /api/loans/{id}` continue to accept the legacy `LN-…` account number, the new numeric `id`, or both? Same for borrowers. | Affects controller signatures, URL design and client migration. |
| **Q3** | Does the production CDW actually contain date formats other than `MM/DD/YYYY`, two-digit years, or amounts with `$`? | Decides whether strict parsing is safe or leniency rules must be added before the load. Requires a profiling run against real data. |
| **Q4** | For loan rows whose copied `BORR_FST_NM`/`BORR_LST_NM` differ from `CDW_BORR_MSTR`, which value is authoritative? | The migration drops the copies; if the loan copy is ever the *newer* value, dropping it loses data. |
| **Q5** | What are the encryption key custody and access requirements for `ssn_encrypted` and `ssn_last4` in the target database? | May require column-level encryption, a separate PII table, or masking in non-prod. |
| **Q6** | What are the full code sets and labels for `BORR_STAT_CD`, `BORR_REC_TYP` and `BORR_EMP_STAT`? Only `ACT`, `PRI`, `EMPLOYED`, `SELF-EMP`, `RETIRED` appear in seed data. | Reference tables must be fully seeded before load or rows will quarantine. |
| **Q7** | Is `12/31/2099` the only "no expiry" sentinel, and should it become `NULL` or be stored literally? | Affects `loan_product.expiry_date` nullability and any "active products" query. |
| **Q8** | Should addresses be de-duplicated by exact match only, or is a normalised (USPS-style) match acceptable? | Exact match is safe but may create near-duplicate `address` rows. |
| **Q9** | Should quarantined rows block cut-over (zero-tolerance) or is a threshold (e.g. < 0.1%) acceptable with manual follow-up? | Sets the acceptance criterion for the migration run. |
| **Q10** | Is `payment.late_fee_amount` included in `total_amount` in any legacy rows? The seed data says no (`ck_payment_split` excludes it). | If yes, the CHECK constraint must be relaxed or the total re-derived. |
| **Q11** | Will the legacy CDW remain writable during migration (dual-run) or is this a one-shot cut-over? | Determines whether change-data-capture / re-sync and the `legacy_*` columns must support repeated loads. |
