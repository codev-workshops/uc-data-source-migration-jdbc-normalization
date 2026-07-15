# Scope Questions and Approved Interpretations

The recommendations below were approved on 2026-07-15 before implementation began.

## Final Resolution Summary

| Item | Resolution |
|---|---|
| Maven baseline defects | Corrected the parent POM element and restored both wrapper launchers. |
| Payment endpoint | Preserved `GET /api/loans/{loanId}/payments` and corrected the README. |
| Payment identifiers | Added unique `payments.external_id` and preserved every `PMT-*` value. |
| Migration execution | Added isolated service/test profiles plus the explicit `legacy-migration-run` runner profile. |
| Reruns | Complete reconciled targets are no-ops; partial or conflicting targets fail before writes. |
| Malformed values | Required malformed values, unknown codes, and missing references fail the transaction. |
| Payment ordering | Uses typed `payment_date DESC` with generated ID as a stable secondary key. |
| API dates | Remain `MM/dd/yyyy` strings while persistence uses typed dates. |
| Legacy cleanup | Legacy artifacts are profile-isolated and retained only for migration execution/tests. |
| Not-found behavior | Preserved exactly and recorded as a separate backlog improvement. |

## 1. Pre-existing Maven build defects

**Ambiguity/conflict:** The protocol requires a green-or-documented baseline before implementation, but `main` cannot be parsed by Maven because `pom.xml` contains invalid `<relativeTo/>`. The scope also requires the application to build with the Maven wrapper, but `mvnw` and `mvnw.cmd` are missing.

**Recommended interpretation:** Treat replacing `<relativeTo/>` with `<relativePath/>` and restoring the wrapper launchers as minimum Blocking fixes. Make these changes first on the approved refactor branch, run the unchanged legacy application, and use that result as the executable behavioral baseline.

## 2. Payment endpoint discrepancy

**Ambiguity/conflict:** `README.md` documents `GET /api/payments/loan/{loanId}`, while the current controller exposes `GET /api/loans/{loanId}/payments`.

**Recommended interpretation:** Preserve only the current controller route and correct the README. Do not add an alias because that would expand the external API outside migration scope. This follows `refactor-scope.md` lines 20 and 58-61.

## 3. Preserving legacy payment identifiers

**Ambiguity/conflict:** `PaymentDto.paymentId` currently returns `PMT-*`, but the target schema has only an auto-generated numeric `payments.id`.

**Recommended interpretation:** Add a unique, non-null `external_id` (or `source_id`) column to the modern `payments` table, migrate `PMT_SEQ_NBR` into it, and keep returning it as `paymentId`. The generated `id` remains internal. This follows `refactor-scope.md` line 145.

## 4. Migration execution versus clean modern default

**Ambiguity/conflict:** The scope requires an executable migration utility, but it also requires the final default application to initialize/read the modern schema and allows legacy artifacts to be removed or isolated. The repository uses an in-memory database, so a one-time migrated database cannot persist between starts.

**Recommended interpretation:** Implement and integration-test a transactional Java migration using the legacy fixtures, then make the normal runtime initialize verified modern schema/data resources. Keep legacy schema/data only in the isolated migration test/execution path. Do not add dual-read behavior.

## 5. Migration rerun policy

**Ambiguity/conflict:** The scope says migration must avoid duplicates when rerun "or fail clearly," but it does not choose a policy.

**Recommended interpretation:** A rerun is a no-op only when all target records are already complete and reconcile exactly. If the target is partially populated or conflicts with source business keys, fail before writing anything. Do not silently update or merge ambiguous rows.

## 6. Unknown or malformed legacy values

**Ambiguity/conflict:** The scope requires handling malformed data but does not define whether invalid rows should be skipped.

**Recommended interpretation:** Fail fast with table name, source business ID, column, and rejected value for malformed required fields, unknown required codes, or missing foreign-key targets. Optional blank fields map to null/defaults allowed by the modern schema. Roll back the entire migration; do not skip rows.

## 7. Date and payment ordering semantics

**Ambiguity/conflict:** Current payments are ordered by descending `MM/DD/YYYY` strings. A typed `LocalDate` query is chronologically correct but may differ from lexical ordering across years. The supplied data does not expose this difference.

**Recommended interpretation:** Preserve the observed API order for supplied records and define payment ordering as chronological `payment_date DESC`, with a stable secondary key. Treat this as the intended meaning of "ordered consistently" rather than intentionally reproducing a string-sorting defect. If exact cross-year lexical behavior is required, revise this recommendation before approval.

## 8. Date types internally versus date strings externally

**Ambiguity/conflict:** The modern entities must use `LocalDate`, while the existing DTOs expose strings and the scope forbids accidental API format changes.

**Recommended interpretation:** Store and query typed dates internally, but format API date fields as the captured legacy `MM/DD/YYYY` strings. Do not change DTO JSON date types in this refactor.

## 9. Legacy code cleanup

**Ambiguity/conflict:** The scope permits legacy entities/repositories to be removed or retained for migration/reference.

**Recommended interpretation:** Remove legacy entities/repositories from the normal application path. Retain only the minimum legacy schema/data and migration adapters required by isolated migration tests or explicit migration execution. Document every retained artifact in `DATA_SOURCE_MIGRATION_NOTES.md`.

## 10. Not-found behavior

**Ambiguity/conflict:** The current service throws an uncaught `RuntimeException`; the exact HTTP response cannot be observed until the POM is repaired. Standardizing it would be architecturally preferable but is not authorized.

**Recommended interpretation:** Capture the actual legacy response after the Blocking build fix and preserve it exactly. Record any desired error-model improvement in `REFACTOR-BACKLOG.md`; do not implement it in this migration.
