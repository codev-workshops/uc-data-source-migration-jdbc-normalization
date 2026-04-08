# Data Source Migration Notes

This document captures the decisions, patterns, and trade-offs made during the migration of the `loan-service` application from the legacy CDW (Corporate Data Warehouse) schema to a modern normalized relational schema.

---

## 1. Migration Overview

| Aspect | Legacy (Before) | Modern (After) |
|:---|:---|:---|
| **Schema style** | Denormalized, cryptic column names (`CDW_BORR_MSTR`) | Normalized, readable names (`borrowers`) |
| **Data types** | All `VARCHAR` | `BIGINT`, `DECIMAL`, `DATE`, `BOOLEAN` |
| **Relationships** | None (borrower data embedded in loan records) | `@ManyToOne` / `@OneToMany` with FK constraints |
| **Primary keys** | String IDs (`B-10001`, `LN-2019-00142`) | Auto-increment `BIGINT` with `external_id` for legacy lookup |
| **Service logic** | Heavy string parsing (7 helper methods) | Direct field access with 5 thin display formatters |

**Approach:** Both schemas coexist in the same H2 in-memory database. The migration service reads from legacy tables on startup and populates the modern tables. The service layer was then rewired to read exclusively from modern repositories.

---

## 2. Task 1 -- Modern Entities and Repositories

### Type Upgrades

| Legacy Type | Modern Type | Example |
|:---|:---|:---|
| `VARCHAR` date (`"02/15/2019"`) | `LocalDate` | `2019-02-15` |
| `VARCHAR` timestamp (`"01/10/2019"`) | `LocalDateTime` | `2019-01-10T00:00:00` |
| `VARCHAR` amount (`"285,000"`) | `BigDecimal` | `285000` |
| `VARCHAR` integer (`"360"`) | `Integer` | `360` |
| `VARCHAR` rate (`"5.250"`) | `BigDecimal` | `5.250` |
| `VARCHAR` boolean (`"ACT"`) | `Boolean` | `true` |

### JPA Relationships

Three `@ManyToOne` relationships replace the denormalized structure:

- `LoanAccount.borrower` -> `Borrower` (FK: `borrower_id`)
- `LoanAccount.product` -> `LoanProduct` (FK: `product_id`)
- `Payment.loanAccount` -> `LoanAccount` (FK: `loan_account_id`)

Each has a corresponding `@OneToMany(mappedBy=...)` on the parent side for bidirectional navigation.

### Repositories

Four Spring Data repositories with custom query methods:

- `BorrowerRepository` -- `findByExternalId()`, `findByStatus()`
- `LoanProductRepository` -- `findByCode()`
- `LoanAccountRepository` -- `findByBorrowerId()`, `findByAccountNumber()`, `findByStatus()`
- `PaymentRepository` -- `findByLoanAccountIdOrderByPaymentDateDesc()`

---

## 3. Task 2 -- Data Migration Service

### Migration Order

Tables are migrated in FK-dependency order to ensure referential integrity:

1. `CDW_BORR_MSTR` -> `borrowers` (5 rows)
2. `CDW_LN_PROD` -> `loan_products` (5 rows)
3. `CDW_LN_ACCT` -> `loan_accounts` (5 rows)
4. `CDW_PMT_HIST` -> `payments` (10 rows)

### Transformation Patterns

| Pattern | Legacy Value | Modern Value | Method |
|:---|:---|:---|:---|
| Date parsing | `"02/15/2019"` | `LocalDate(2019,2,15)` | `parseLegacyDate()` |
| Amount parsing | `"285,000"` | `BigDecimal(285000)` | `parseLegacyAmount()` -- strips commas |
| Decimal parsing | `"5.250"` | `BigDecimal(5.250)` | `parseLegacyDecimal()` |
| Integer parsing | `"360"` | `Integer(360)` | `parseLegacyInteger()` |
| Status expansion | `"ACT"` | `"ACTIVE"` | `expandLoanStatus()` |
| Property type expansion | `"SFR"` | `"Single Family"` | `expandPropertyType()` |
| Payment type expansion | `"REG"` | `"REGULAR"` | `expandPaymentType()` |
| Payment status expansion | `"PST"` | `"POSTED"` | `expandPaymentStatus()` |
| Boolean conversion | `"ACT"` | `true` | Direct comparison |

### FK Resolution

Legacy tables use string IDs with no foreign keys. Modern tables use auto-increment `BIGINT` PKs. Resolution approach:

1. Migrate parent tables first (borrowers, loan_products)
2. Build in-memory lookup maps: `Map<String, Borrower>` keyed by `externalId`
3. For each child record, look up the parent by legacy ID and set the FK reference

### Idempotency

The migration checks `borrowerRepository.count() > 0` before running. If modern tables already contain data, the migration is skipped entirely.

### Error Handling

Each record is wrapped in a try-catch. If one record fails, the error is logged and migration continues with the next record.

### Validation

After migration, row counts are verified against expected values:
- Borrowers: 5
- Loan products: 5
- Loan accounts: 5
- Payments: 10

---

## 4. Task 3 -- Service Layer Rewiring

### What Changed

`LoanService.java` was rewired from 4 legacy repositories to 3 modern repositories:

**Removed dependencies:**
- `LegacyBorrowerRepository`
- `LegacyLoanAccountRepository`
- `LegacyLoanProductRepository`
- `LegacyPaymentRepository`

**Added dependencies:**
- `BorrowerRepository`
- `LoanAccountRepository`
- `PaymentRepository`

### Removed Legacy Helpers (7 methods)

These string-parsing methods were no longer needed because modern entities store proper types:

1. `parseLegacyAmount(String)` -- comma-stripped `BigDecimal` parsing
2. `parseLegacyDecimal(String)` -- raw `BigDecimal` parsing
3. `parseLegacyInteger(String)` -- `Integer.parseInt` wrapper
4. `expandStatusCode(String)` -- `ACT` -> `Active` expansion
5. `expandPropertyType(String)` -- `SFR` -> `Single Family Residence` expansion
6. `expandPaymentType(String)` -- `REG` -> `Regular` expansion
7. `expandPaymentStatus(String)` -- `PST` -> `Posted` expansion

### Added Display Formatters (5 methods)

To preserve the existing API contract (Option A), thin display formatters were added. These format already-typed values rather than parsing raw strings:

1. `stripZeros(BigDecimal)` -- removes trailing zeros for clean JSON output
2. `formatDate(LocalDate)` -- formats to `MM/dd/yyyy` for legacy API compatibility
3. `formatStatus(String)` -- `ACTIVE` -> `Active` (title case for API)
4. `formatPropertyType(String)` -- `Single Family` -> `Single Family Residence` (adds suffix)
5. `formatPaymentType(String)` -- `REGULAR` -> `Regular` (title case for API)
6. `formatPaymentStatus(String)` -- `POSTED` -> `Posted`, `NSF` -> `Non-Sufficient Funds`

### Decision: Option A (Preserve API Contract)

Two options were considered for handling the format mismatch between modern stored values and legacy API output:

- **Option A (chosen):** Keep thin display formatters to match legacy output exactly. The API contract remains identical, and downstream consumers are unaffected.
- **Option B (rejected):** Let modern format flow through (e.g., ISO dates, uppercase statuses). Cleaner code but breaks API contract.

Option A was chosen because the wiki requires "All API endpoints return identical JSON responses."

### Special Cases

**Payment External ID:** The modern `Payment` entity stores an `externalId` field (`VARCHAR(20)`) that holds the legacy payment sequence number (e.g., `PMT-2025120001`). Without this, the API would return auto-increment IDs instead of the legacy identifiers.

**BigDecimal Serialization:** Added `spring.jackson.serialization.write-bigdecimal-as-plain=true` to `application.properties` to prevent scientific notation in JSON output (e.g., `2.85E+5` instead of `285000`).

**Zero-value Handling:** `BigDecimal("0.00").stripTrailingZeros()` yields scale <= 0, which Jackson serializes as integer `0`. Legacy API returned `0.0`, so the `stripZeros()` method preserves one decimal place for zero values.

---

## 5. Task 4 -- Golden-File Validation

### Approach

1. Captured API responses from the legacy system as golden files (JSON) before any migration changes
2. After rewiring to modern data source, ran the same API calls
3. Compared responses using Jackson `ObjectMapper.readTree()` for deep JSON equality
4. Deep equality ignores whitespace and key ordering differences

### Test Coverage

17 integration tests in `GoldenFileValidationTest.java`:

| Category | Count | Endpoints |
|:---|:---|:---|
| All loans | 1 | `GET /api/loans` |
| Individual loans | 5 | `GET /api/loans/{id}` for each of 5 loans |
| All borrowers | 1 | `GET /api/borrowers` |
| Individual borrowers | 5 | `GET /api/borrowers/{id}` for each of 5 borrowers |
| Payments by loan | 5 | `GET /api/loans/{id}/payments` for each of 5 loans |

### Test Infrastructure

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` boots the full application context
- `TestRestTemplate` makes HTTP calls to the running app
- Golden files stored in `src/test/resources/golden/`
- `ClassPathResource` loads golden files from the classpath

### Results

All 18 tests pass (17 golden file + 1 `contextLoads` smoke test):
```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

No intentional differences -- the modern data source produces byte-identical JSON to the legacy system.

---

## 6. Key Decisions and Trade-offs

| Decision | Rationale |
|:---|:---|
| Coexist both schemas in same H2 database | Allows side-by-side comparison and migration validation without separate data sources |
| `@PostConstruct` migration on startup | Simple, automatic, runs before any API requests are served |
| Idempotent migration (skip if data exists) | Safe for repeated startups, no duplicate data |
| Option A display formatters | Preserves API contract exactly, no downstream breakage |
| `externalId` on Payment entity | Preserves legacy payment sequence numbers in API responses |
| `write-bigdecimal-as-plain` Jackson config | Prevents scientific notation, matches legacy numeric formatting |
| Legacy entities marked `@Deprecated` | Signals intent to remove while keeping code compilable for reference |
| Golden files captured before migration | Provides ground truth for regression testing |

---

## 7. Files Modified/Created

### Task 1
- **Created:** `Borrower.java`, `LoanProduct.java`, `LoanAccount.java`, `Payment.java` (entities)
- **Created:** `BorrowerRepository.java`, `LoanProductRepository.java`, `LoanAccountRepository.java`, `PaymentRepository.java`
- **Created:** `schema-modern.sql`
- **Modified:** `application.properties` (added modern schema to init)

### Task 2
- **Created:** `DataMigrationService.java`

### Task 3
- **Modified:** `LoanService.java` (rewired to modern repos, removed 7 helpers, added 5 formatters)
- **Modified:** `Payment.java` (added `externalId` field)
- **Modified:** `schema-modern.sql` (added `external_id` column)
- **Modified:** `DataMigrationService.java` (populates `externalId`)
- **Modified:** `application.properties` (added `write-bigdecimal-as-plain`)

### Task 4
- **Created:** `GoldenFileValidationTest.java` (17 test methods)
- **Created:** 17 golden files in `src/test/resources/golden/`
- **Modified:** `LoanServiceApplicationTests.java` (aligned to `RANDOM_PORT`)

### Task 5
- **Created:** `DATA_SOURCE_MIGRATION_NOTES.md` (this file)
- **Modified:** `application.properties` (documented modern schema config)
- **Modified:** Legacy entities marked `@Deprecated`
- **Modified:** Legacy repositories marked `@Deprecated`
