# Data Source Migration Notes

## Decisions

### FK Resolution Strategy
Modern schema uses auto-increment BIGINT primary keys. Legacy string-based IDs (e.g. `B-10001`, `FXD30`, `LN-2019-00142`) are preserved as secondary identifiers (`external_id`, `code`, `account_number`). FK relationships use the BIGINT IDs. Seed data insert order determines the auto-generated IDs, with mappings:
- Borrowers: B-10001->1, B-10002->2, B-10003->3, B-10004->4, B-10005->5
- Products: FXD30->1, FXD15->2, ARM51->3, FHA30->4, VA30->5
- Loan Accounts: LN-2019-00142->1, LN-2020-00398->2, LN-2018-00089->3, LN-2021-00567->4, LN-2017-00034->5

### Status Code Title-Case Mapping
Status and type values use **title-case** to match the legacy `LoanService.java` expand methods:
- `ACT` -> `Active` (not `ACTIVE`)
- `SFR` -> `Single Family Residence`
- `REG` -> `Regular`
- `PST` -> `Posted`

This preserves API contract compatibility with the legacy responses.

### Property Type Expansion
| Code | Expanded Value |
|------|---------------|
| `SFR` | `Single Family Residence` |
| `CND` | `Condominium` |
| `TWN` | `Townhouse` |
| `MFR` | `Multi-Family Residence` |

### Date Format Handling
- Legacy stored dates as `MM/DD/YYYY` VARCHAR strings
- Modern uses `DATE` and `TIMESTAMP` types (stored as `YYYY-MM-DD` internally)
- API responses format dates back to `MM/dd/yyyy` using `DateTimeFormatter` to preserve legacy API format

## Patterns Used

### JPA Relationships
- `@ManyToOne(fetch = FetchType.LAZY)` with `@JoinColumn` for FK relationships
- `@OneToMany(mappedBy = ...)` for inverse navigation
- Lazy fetching to avoid N+1 queries by default

### Spring Data Derived Queries
- `findByExternalId`, `findByCode`, `findByAccountNumber` for single-entity lookups
- `findByBorrowerExternalId` for cross-entity FK navigation
- `findByLoanAccountAccountNumberOrderByPaymentDateDesc` for ordered relationship queries

### H2 Schema Initialization
- `spring.sql.init.schema-locations=classpath:schema-modern.sql`
- `spring.sql.init.data-locations=classpath:data-modern.sql`
- `spring.jpa.hibernate.ddl-auto=none` (schema managed by SQL scripts, not Hibernate)
- `CREATE TABLE IF NOT EXISTS` for idempotent schema creation

## Data Transformation Summary

| Transformation | Count | Example |
|---------------|-------|---------|
| Date columns (VARCHAR -> DATE) | 18 | `'03/15/1978'` -> `DATE '1978-03-15'` |
| Amount columns (VARCHAR -> DECIMAL) | 15 | `'92,500'` -> `92500.00` |
| Status/code expansions | 6 | `'ACT'` -> `'Active'` |
| Integer columns (VARCHAR -> INTEGER) | 5 | `'745'` -> `745` |
| Boolean columns (VARCHAR -> BOOLEAN) | 1 | `'ACT'` -> `TRUE` |
| Denormalized columns dropped | 3 | `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` |
| Metadata columns dropped from seed | 4 | `PMT_CRET_DT`, `PMT_UPDT_DT` (auto-generated) |

## Intentional API Differences

| Field | Legacy Value | Modern Value | Reason |
|-------|-------------|--------------|--------|
| `paymentId` | `PMT-2025120001` | `1` | Modern uses auto-increment BIGINT IDs |
| `originalAmount` | `285000` | `285000.00` | DECIMAL type preserves scale |

## Cleanup Status

Legacy code retained with `@Deprecated` annotations:
- 4 legacy entities: `LegacyBorrower`, `LegacyLoanAccount`, `LegacyLoanProduct`, `LegacyPayment`
- 4 legacy repositories: `LegacyBorrowerRepository`, `LegacyLoanAccountRepository`, `LegacyLoanProductRepository`, `LegacyPaymentRepository`
- Legacy schema/data SQL files retained for reference

Recommended removal in next sprint after confirming no downstream dependencies.
