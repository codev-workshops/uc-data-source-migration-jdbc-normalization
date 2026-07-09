# Submission Manifest — Legacy → Modern Data Source Migration

Candidate: **Priyal Walpita** (org: Priyal-Org)

## Pull Requests

### Session A (prerequisite stack)
1. Repo health + golden baseline: https://github.com/codev-workshops/uc-data-source-migration-legacy-to-modern/pull/35
   (`priyal/fix-repo-health` → `main`)
2. Task 1 — Modern schema entities, repositories & second datasource: https://github.com/codev-workshops/uc-data-source-migration-legacy-to-modern/pull/36
   (`priyal/task-1-modern-schema-entities` → `priyal/fix-repo-health`)
3. Task 2 — Idempotent legacy-to-modern data migration: https://github.com/codev-workshops/uc-data-source-migration-legacy-to-modern/pull/37
   (`priyal/task-2-data-migration` → `priyal/task-1-modern-schema-entities`)

### Session B (this branch)
4. Tasks 3–5 — Cutover, golden validation, docs + dual-read & reconciliation bonuses
   (`priyal/task-3-5-cutover` → `priyal/task-2-data-migration`)

## Branch graph

```
main (51dad16 Scaffold loan-service app with legacy data source)
└── priyal/fix-repo-health
    └── priyal/task-1-modern-schema-entities
        └── priyal/task-2-data-migration
            └── priyal/task-3-5-cutover   <-- this branch
```

## Commits per task

- Repo health / golden baseline (`priyal/fix-repo-health`):
  - `7f8cbcd` fix: correct invalid relativePath tag in parent POM
  - `532bfe4` chore: restore Maven wrapper (mvnw)
  - `420b1f1` chore(task-4-prep): capture legacy golden API responses
  - `e0f4ee6` fix(golden): capture raw API bytes — preserve BigDecimal scale
- Task 1 (`priyal/task-1-modern-schema-entities`):
  - `ee02ccf` feat(task-1): add modern schema JPA entities, repositories, and dual datasource config
- Task 2 (`priyal/task-2-data-migration`):
  - `63eaa7c` feat(task-2): add idempotent legacy-to-modern data migration service with validation tests
  - `07d1d74` fix(migration): null-safe dedup; validate-then-skip malformed records with warnings and counts
  - `88afe6e` fix(migration): validate mandatory loan account fields before save
- Tasks 3–5 (`priyal/task-3-5-cutover`, this branch):
  - `370fd9f` feat(task-3): cut LoanService over to modern schema with datasource.mode dual-read flag and startup migration
  - `1f5441f` test(task-4): golden-file comparison suites for all 5 endpoints in modern and legacy modes
  - `f249b85` docs(task-5): migration notes, deprecate legacy entities/repositories, reconciliation SQL

## Test summary (`./mvnw clean test`)

```
Running com.workshop.loanservice.LoanServiceApplicationTests
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.864 s -- in com.workshop.loanservice.LoanServiceApplicationTests
Running com.workshop.loanservice.golden.GoldenFileLegacyModeComparisonTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.794 s -- in com.workshop.loanservice.golden.GoldenFileLegacyModeComparisonTest
Running com.workshop.loanservice.golden.GoldenFileComparisonTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.475 s -- in com.workshop.loanservice.golden.GoldenFileComparisonTest
Running com.workshop.loanservice.modern.migration.LegacyToModernMigrationServiceTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.715 s -- in com.workshop.loanservice.modern.migration.LegacyToModernMigrationServiceTest

Results:

Tests run: 18, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS
```

Manual smoke: application booted in `datasource.mode=modern` and
`datasource.mode=legacy`; all 5 endpoints byte-compared (`cmp`) against the
17 golden files — zero differences in both modes.

## README vs code: payments endpoint path

The README documents `GET /api/payments/loan/{loanId}`, but the actual
controller mapping is `GET /api/loans/{id}/payments`
(`LoanController#getPayments`). The golden files were captured against the
real path; see DATA_SOURCE_MIGRATION_NOTES.md ("Seeded defects").

## Archival redundancy

- Git bundle (all refs): `/home/ubuntu/migration-deliverable.bundle`
- Format patches (`origin/main..HEAD`): `/home/ubuntu/patches/`
