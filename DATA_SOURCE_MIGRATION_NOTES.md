# Data Source Migration Notes

## Result

The service now reads from the normalized `borrowers`, `loan_products`,
`loan_accounts`, and `payments` tables by default. The controllers, DTOs,
routes, status codes, response shapes, identifiers, title-cased labels, and
`MM/dd/yyyy` API date strings remain compatible with the captured legacy API.

The default runtime initializes:

```properties
spring.sql.init.schema-locations=classpath:schema-modern.sql
spring.sql.init.data-locations=classpath:data-modern.sql
```

`spring.jpa.hibernate.ddl-auto=none` remains unchanged.

## Migration Execution

`LegacyDataMigrationRunner` runs only with the `legacy-migration-run` profile.
That profile also enables the legacy repositories and transactional migration
service. The default profile does not register those components.

The migration dependency order is:

1. borrowers;
2. loan products;
3. loan accounts after borrower and product key resolution;
4. payments after loan-account key resolution.

All four stages run in one transaction.

## Transformations

- `MM/dd/yyyy` source strings become `LocalDate` or start-of-day
  `LocalDateTime` values.
- Commas are removed before amounts are parsed as `BigDecimal`.
- Numeric strings become `Integer` values.
- Blank optional fields become `null`.
- Borrower statuses become `ACTIVE` or `INACTIVE`.
- Loan statuses become `ACTIVE`, `CLOSED`, `DEFAULT`, or `FORBEARANCE`.
- Product activity codes become booleans.
- Property, payment-type, and payment-status codes become canonical readable
  values.
- Borrower and product references embedded in legacy loan rows become foreign
  keys.
- `PMT_SEQ_NBR` is retained as unique `payments.external_id`; the generated
  numeric payment ID remains internal.

## Integrity and Rerun Policy

The migration validates target state before writing:

- an empty target is migrated;
- a complete target with matching counts, identifiers, relationships, and
  values is a no-op;
- a partial or conflicting target fails before writes;
- malformed required values, unknown required codes, and unresolved foreign
  keys fail the migration;
- any failure rolls back the complete transaction.

Expected reconciliation counts are:

| Table | Rows |
|---|---:|
| `borrowers` | 5 |
| `loan_products` | 5 |
| `loan_accounts` | 5 |
| `payments` | 10 |

## API Compatibility

The modern service uses typed entities internally and maps them to the existing
DTO contract:

- business identifiers remain external API identifiers;
- typed dates are formatted back to `MM/dd/yyyy`;
- canonical statuses and types are presented with the original title-cased
  labels;
- borrower and loan arrays retain the captured ordering;
- payments use chronological `payment_date DESC` ordering with generated ID as
  a stable secondary key;
- the pre-existing not-found HTTP behavior remains unchanged.

`LegacyApiGoldenTest` executes the public HTTP endpoints against modern schema
and data and compares them with `src/test/resources/golden/legacy-api.json`.

## Retained Legacy Artifacts

The following artifacts remain solely for migration execution and tests:

- `schema-legacy.sql` and `data-legacy.sql`;
- `LegacyBorrower`, `LegacyLoanProduct`, `LegacyLoanAccount`, and
  `LegacyPayment`;
- the four `Legacy*Repository` interfaces;
- `LegacyValueTransformer`, `LegacyDataMigrationService`, and
  `LegacyDataMigrationRunner`.

Legacy repositories and the migration service require either the
`legacy-migration` test profile or `legacy-migration-run` execution profile.
No default API read path uses a legacy repository or `CDW_*` table.

## Verification

The final verification commands are:

```text
.\mvnw.cmd test
git diff --check
```

Final result:

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
git diff --check: clean
```

The suite verifies:

- modern DDL constraints, indexes, generated IDs, and relationships;
- typed date and decimal persistence;
- payment external-ID uniqueness and ordering;
- successful migration and exact row counts;
- reconciled no-op reruns and conflicting-target rejection;
- malformed-input failure and transaction rollback;
- command-line migration-runner activation;
- modern-only default startup;
- HTTP response parity with the legacy fixture.

No dependency or framework version was changed for the migration.

## Rollback

The database changes are additive and the repository uses in-memory H2
fixtures. Reverting the service/default-configuration commits restores the
legacy read path while the retained legacy schema, data, entities, and
repositories remain available. A failed migration transaction leaves the
modern target unchanged.
