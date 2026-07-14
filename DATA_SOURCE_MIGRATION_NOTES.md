# Legacy-to-Modern Data Source Migration

The API now reads the normalized modern schema by default while retaining the
legacy read path as a temporary rollback option.

## Design

```text
Controllers -> LoanService -> LoanDataProvider
                                 |          |
                              modern     legacy
                                 |          |
                         modern schema   CDW schema
```

- `datasource.mode=modern` selects typed modern entities and repositories.
- `datasource.mode=legacy` selects the original CDW-backed behavior.
- Controllers and DTOs are unchanged.
- `LegacyApiCharacterizationTest` and its 17 golden responses were created and
  passed against the legacy implementation before the data source changed.
  The same test is unchanged and passes against the modern implementation.

## Migration assets

- `data/modern-schema/modern_tables.sql`: non-destructive production DDL.
- `src/main/resources/schema-modern.sql`: disposable H2 initialization only;
  it drops tables to keep local and test runs deterministic.
- `LegacyToModernMigrationService`: idempotent backfill in foreign-key order:
  borrowers, products, accounts, then payments.
- `data/validation/reconciliation_queries.sql`: row-count, amount, key-coverage,
  and orphan checks.

The migration converts legacy strings to `LocalDate`, `BigDecimal`, `Integer`,
and canonical status values. Records with missing mandatory values or
unresolvable foreign keys are logged and counted rather than partially saved.
The legacy payment sequence is retained as `payments.payment_number`, providing
a stable API identifier and migration key.

## Runtime switches

| Property | Local default | Production usage |
| --- | --- | --- |
| `datasource.mode` | `modern` | Start with `legacy`, then switch to `modern` |
| `migration.run-on-startup` | `true` | Enable only on a controlled migration instance |
| `modern.datasource.initialize-schema` | `true` | Set `false`; apply DDL through the release process |
| `spring.sql.init.mode` | `always` | Set `never` for an existing legacy database |

Production deployments must also supply the legacy and modern connection
properties instead of the in-memory H2 defaults.

## Production rollout

1. **Deploy compatibility release** with both data sources configured,
   `datasource.mode=legacy`, `migration.run-on-startup=false`,
   `modern.datasource.initialize-schema=false`, and
   `spring.sql.init.mode=never`. API traffic remains on legacy.
2. **Create the modern schema** using
   `data/modern-schema/modern_tables.sql` through the normal reviewed database
   change process.
3. **Quiesce legacy writes** and run one isolated application instance with
   `migration.run-on-startup=true`. Keep traffic on legacy while it backfills.
4. **Reconcile** counts, natural keys, monetary totals, and foreign keys with
   `data/validation/reconciliation_queries.sql`. Treat skipped-record counts
   as a cutover blocker unless explicitly accepted.
5. **Canary the modern read path** by setting `datasource.mode=modern` on a
   small instance group. Compare API responses and operational metrics, then
   roll the setting through the remaining instances.
6. **Rollback if needed** by restoring `datasource.mode=legacy`; no redeploy or
   reverse data migration is required for this read-only service.
7. **Retire legacy support** only after the agreed rollback window and after
   confirming no downstream dependency still uses the CDW schema.

This simple plan intentionally uses a write freeze instead of dual-write or
CDC. If production writers must remain active during backfill, add change-data
capture or an upsert-based delta migration before using this rollout.

## Validation

Run:

```bash
./mvnw clean test
```

The suite checks:

- byte-identical API behavior for all borrowers, loans, and payments;
- migrated row counts and monetary totals;
- foreign-key resolution and typed conversions;
- idempotent reruns;
- malformed-record handling.

The payment characterization test uses the implemented route:
`GET /api/loans/{loanId}/payments`.
