# Repository Context

## Branching

- `master` is the primary branch for this repository. Always branch from `master` and target
  `master` in pull requests, even though GitHub currently lists `main` as the default branch.
- Do not base work on `main` or open pull requests against it.

## Migration validation report

Every task that touches the data path (`service/`, `routing/`, `entity/`, `repository/`,
`db/migration/`, the DTOs, or the golden files under `src/test/resources/test-data/e2e/golden/`)
must end by producing a migration validation report and attaching it to the final message and
the PR. Do not report completion without it.

1. Run the validation suite: `mvn test -Dtest=MigrationValidationE2ETest -Djacoco.skip=true`.
2. Start the app (`mvn spring-boot:run -Dspring-boot.run.profiles=e2e-test`) and capture every
   read endpoint twice, with `?serviceImpl=legacy` and `?serviceImpl=normalized`:
   `/api/loans`, `/api/loans/LN-2019-00142`, `/api/borrowers`, `/api/borrowers/B-10001`,
   `/api/loans/LN-2019-00142/payments`, `/api/payments/loan/LN-2019-00142`.
3. Write a Markdown report containing, in this order:
   - the commit the run was made on and the surefire summary (`Tests run / Failures / Errors`)
     plus a PASS/FAIL line per test method;
   - the full contents of every golden file in `src/test/resources/test-data/e2e/golden/`;
   - a `diff` of the legacy vs normalized response per endpoint (`(identical)` when equal);
   - the list of files changed by the task.
4. Any new legacy/normalized difference not already listed in `docs/MIGRATION_LOG.md`
   ("Task 4 validation") must be added there with a justification, or the code fixed, before
   the report is considered passing.
