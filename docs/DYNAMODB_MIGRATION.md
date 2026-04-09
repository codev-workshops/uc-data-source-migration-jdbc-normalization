# DynamoDB Migration: Legacy MySQL to Modern DynamoDB

## 1. Overview

This document describes the migration of the loan-service application from a legacy MySQL-based data warehouse (H2 simulated) to Amazon DynamoDB. The migration addresses all legacy schema problems: loose VARCHAR typing, denormalized structures, cryptic column names, and missing constraints.

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Multi-table design** (4 tables) | Maps cleanly to existing domain entities; easier to reason about, manage capacity, and evolve independently |
| **PAY_PER_REQUEST billing** | Suitable for variable/unpredictable workloads; avoids capacity planning overhead |
| **ISO 8601 date strings** | DynamoDB has no native DATE type; ISO 8601 strings sort lexicographically in correct chronological order |
| **Number type for amounts** | DynamoDB Number supports arbitrary precision; avoids the comma-formatted VARCHAR problem |
| **Composite sort key for Payments** | `{date}#{id}` pattern enables chronological ordering with guaranteed uniqueness |
| **Application-level referential integrity** | DynamoDB does not support foreign keys; integrity enforced in service layer via transactional writes |

---

## 2. New Schema Definition

### 2.1 Borrowers Table

| Attribute | DynamoDB Type | Description | Key |
|-----------|--------------|-------------|-----|
| `borrower_id` | S (String) | Unique borrower identifier (e.g., `B-10001`) | **Partition Key** |
| `first_name` | S | Borrower's first name | |
| `last_name` | S | Borrower's last name | GSI PK (LastNameIndex) |
| `middle_initial` | S | Middle initial (nullable) | |
| `ssn_hash` | S | Hashed SSN | |
| `date_of_birth` | S | ISO 8601 date (`YYYY-MM-DD`) | |
| `address_line1` | S | Street address | |
| `address_line2` | S | Address line 2 (nullable) | |
| `city` | S | City | |
| `state` | S | 2-letter state code | |
| `zip_code` | S | ZIP code | |
| `phone` | S | Phone number | |
| `email` | S | Email address | GSI PK (EmailIndex) |
| `credit_score` | N (Number) | Credit score as integer | |
| `employment_status` | S | Employment status | |
| `annual_income` | N | Annual income as decimal | |
| `status` | S | `ACTIVE` or `INACTIVE` | GSI PK (StatusIndex) |
| `created_at` | S | ISO 8601 timestamp | |
| `updated_at` | S | ISO 8601 timestamp | |

### 2.2 LoanProducts Table

| Attribute | DynamoDB Type | Description | Key |
|-----------|--------------|-------------|-----|
| `product_code` | S (String) | Product code (e.g., `FXD30`) | **Partition Key** |
| `name` | S | Product name/description | |
| `type` | S | Product type (`FXD`, `ARM`, `FHA`, `VA`) | |
| `term_months` | N (Number) | Loan term in months | |
| `rate_type` | S | `FIXED` or `VARIABLE` | |
| `min_amount` | N | Minimum loan amount | |
| `max_amount` | N | Maximum loan amount | |
| `is_active` | BOOL (Boolean) | Whether product is active | |
| `effective_date` | S | ISO 8601 date | |
| `expiration_date` | S | ISO 8601 date | |

### 2.3 LoanAccounts Table

| Attribute | DynamoDB Type | Description | Key |
|-----------|--------------|-------------|-----|
| `account_number` | S (String) | Loan account number (e.g., `LN-2019-00142`) | **Partition Key** |
| `borrower_id` | S | References Borrowers table | GSI PK (BorrowerIndex) |
| `product_code` | S | References LoanProducts table | GSI PK (ProductIndex) |
| `original_amount` | N (Number) | Original loan amount | |
| `current_balance` | N | Current outstanding balance | |
| `interest_rate` | N | Interest rate (e.g., `4.750`) | |
| `term_months` | N | Loan term in months | |
| `monthly_payment` | N | Monthly payment amount | |
| `origination_date` | S | ISO 8601 date | GSI SK (BorrowerIndex) |
| `maturity_date` | S | ISO 8601 date | |
| `first_payment_date` | S | ISO 8601 date | |
| `next_payment_date` | S | ISO 8601 date | |
| `status` | S | `ACTIVE`, `CLOSED`, `DEFAULT`, `FORBEARANCE` | GSI PK (StatusIndex) |
| `delinquency_days` | N | Days delinquent | |
| `escrow_balance` | N | Escrow account balance | |
| `ltv_percent` | N | Loan-to-value ratio | |
| `property_address` | S | Property street address | |
| `property_city` | S | Property city | |
| `property_state` | S | Property state code | |
| `property_zip` | S | Property ZIP code | |
| `property_type` | S | Full name (e.g., `Single Family Residence`) | |
| `appraised_value` | N | Property appraised value | |
| `created_at` | S | ISO 8601 timestamp | |
| `updated_at` | S | ISO 8601 timestamp | |

### 2.4 Payments Table

| Attribute | DynamoDB Type | Description | Key |
|-----------|--------------|-------------|-----|
| `loan_account_id` | S (String) | References LoanAccounts table | **Partition Key** |
| `payment_sort_key` | S | Composite: `{YYYY-MM-DD}#{payment_id}` | **Sort Key** |
| `payment_id` | S | Unique payment identifier | GSI PK (PaymentIdIndex) |
| `payment_date` | S | ISO 8601 date | GSI SK (StatusIndex) |
| `total_amount` | N (Number) | Total payment amount | |
| `principal_amount` | N | Principal portion | |
| `interest_amount` | N | Interest portion | |
| `escrow_amount` | N | Escrow portion | |
| `late_fee` | N | Late fee amount | |
| `type` | S | `REGULAR`, `EXTRA`, `PARTIAL`, `PREPAYMENT` | |
| `status` | S | `POSTED`, `REVERSED`, `NSF`, `PENDING` | GSI PK (StatusIndex) |
| `received_date` | S | ISO 8601 date | |
| `processed_date` | S | ISO 8601 date | |
| `created_at` | S | ISO 8601 timestamp | |
| `updated_at` | S | ISO 8601 timestamp | |

---

## 3. Indexing Strategy

### 3.1 Access Patterns & Index Mapping

| # | Access Pattern | Table | Index Used | Key Condition |
|---|---------------|-------|-----------|---------------|
| AP1 | Get borrower by ID | Borrowers | Table (PK) | `borrower_id = :id` |
| AP2 | List all borrowers | Borrowers | Table Scan | Full scan |
| AP3 | Find borrowers by status | Borrowers | `StatusIndex` (GSI) | `status = :status` |
| AP4 | Find borrower by email | Borrowers | `EmailIndex` (GSI) | `email = :email` |
| AP5 | Find borrowers by last name | Borrowers | `LastNameIndex` (GSI) | `last_name = :name` |
| AP6 | Get loan product by code | LoanProducts | Table (PK) | `product_code = :code` |
| AP7 | List all loan products | LoanProducts | Table Scan | Full scan (small table) |
| AP8 | Get loan by account number | LoanAccounts | Table (PK) | `account_number = :acct` |
| AP9 | List all loans | LoanAccounts | Table Scan | Full scan |
| AP10 | Get loans for a borrower | LoanAccounts | `BorrowerIndex` (GSI) | `borrower_id = :id` (sorted by origination_date) |
| AP11 | Get loans by status | LoanAccounts | `StatusIndex` (GSI) | `status = :status` |
| AP12 | Get loans by product | LoanAccounts | `ProductIndex` (GSI) | `product_code = :code` |
| AP13 | Get payments for a loan (date-ordered) | Payments | Table (PK+SK) | `loan_account_id = :acct` (ScanIndexForward=false for DESC) |
| AP14 | Get payment by ID | Payments | `PaymentIdIndex` (GSI) | `payment_id = :id` |
| AP15 | Get payments by status | Payments | `StatusIndex` (GSI) | `status = :status` (sorted by payment_date) |

### 3.2 Index Definitions

#### Borrowers Table Indexes

| Index Name | Type | Partition Key | Sort Key | Projection | Purpose |
|------------|------|--------------|----------|------------|---------|
| *(base table)* | Table | `borrower_id` (S) | -- | ALL | Primary lookup by borrower ID |
| `StatusIndex` | GSI | `status` (S) | `borrower_id` (S) | ALL | Filter borrowers by active/inactive status |
| `EmailIndex` | GSI | `email` (S) | -- | ALL | Unique borrower lookup by email |
| `LastNameIndex` | GSI | `last_name` (S) | `borrower_id` (S) | ALL | Search borrowers by last name |

#### LoanProducts Table Indexes

| Index Name | Type | Partition Key | Sort Key | Projection | Purpose |
|------------|------|--------------|----------|------------|---------|
| *(base table)* | Table | `product_code` (S) | -- | ALL | Primary lookup by product code |

> No GSIs needed: small reference table (~10s of items), full scan is efficient.

#### LoanAccounts Table Indexes

| Index Name | Type | Partition Key | Sort Key | Projection | Purpose |
|------------|------|--------------|----------|------------|---------|
| *(base table)* | Table | `account_number` (S) | -- | ALL | Primary lookup by account number |
| `BorrowerIndex` | GSI | `borrower_id` (S) | `origination_date` (S) | ALL | Get all loans for a borrower, sorted by origination date |
| `StatusIndex` | GSI | `status` (S) | `account_number` (S) | ALL | Filter loans by status (ACTIVE, CLOSED, etc.) |
| `ProductIndex` | GSI | `product_code` (S) | `account_number` (S) | ALL | Find all loans using a specific product |

#### Payments Table Indexes

| Index Name | Type | Partition Key | Sort Key | Projection | Purpose |
|------------|------|--------------|----------|------------|---------|
| *(base table)* | Table | `loan_account_id` (S) | `payment_sort_key` (S) | ALL | Get all payments for a loan, chronologically ordered |
| `PaymentIdIndex` | GSI | `payment_id` (S) | -- | ALL | Direct lookup of a specific payment |
| `StatusIndex` | GSI | `status` (S) | `payment_date` (S) | ALL | Find payments by status (e.g., all PENDING payments) |

### 3.3 Index Design Rationale

**Why composite sort key for Payments?**
- The sort key `{YYYY-MM-DD}#{payment_id}` serves two purposes:
  1. **Chronological ordering:** ISO 8601 date prefix ensures lexicographic sort equals chronological sort
  2. **Uniqueness:** Appending payment_id prevents collisions when multiple payments occur on the same date for the same loan
- Query with `ScanIndexForward=false` returns payments in reverse chronological order (newest first)

**Why GSI instead of LSI?**
- LSIs must be defined at table creation time and share the table's partition key
- GSIs allow querying by completely different partition keys (e.g., `borrower_id` on LoanAccounts)
- GSIs can be added/removed after table creation, providing more flexibility

**Why ALL projection on GSIs?**
- Avoids extra read capacity from table fetches after index lookups
- Trade-off: higher storage cost for GSIs, but eliminates the latency of secondary lookups
- Acceptable for this workload given the moderate data volume

### 3.4 Capacity & Cost Considerations

| Table | Estimated Item Size | Expected Volume | Billing Mode |
|-------|-------------------|----------------|--------------|
| Borrowers | ~500 bytes | Thousands | PAY_PER_REQUEST |
| LoanProducts | ~200 bytes | Tens | PAY_PER_REQUEST |
| LoanAccounts | ~600 bytes | Thousands | PAY_PER_REQUEST |
| Payments | ~400 bytes | Hundreds of thousands | PAY_PER_REQUEST |

> For production with predictable traffic, consider switching to PROVISIONED mode with auto-scaling for cost optimization.

---

## 4. Referential Integrity (Application-Level)

DynamoDB does not support foreign keys. Referential integrity is enforced at the application level:

| Relationship | Enforcement Strategy |
|---|---|
| LoanAccounts.borrower_id -> Borrowers | Validate borrower exists before creating loan account |
| LoanAccounts.product_code -> LoanProducts | Validate product exists before creating loan account |
| Payments.loan_account_id -> LoanAccounts | Validate loan account exists before creating payment |

**Implementation approach:**
- Use `DynamoDbEnhancedClient` transactional writes (`transactWriteItems`) for operations that span multiple tables
- Service layer validates referenced entities exist before writes
- Deletion of parent records checks for dependent children first

---

## 5. Migration Strategy

### 5.1 Data Transformation Pipeline

```
Legacy MySQL (H2)                    DynamoDB
─────────────────                    ────────
CDW_BORR_MSTR ──── transform ────► Borrowers
  - Parse date strings (MM/DD/YYYY -> YYYY-MM-DD)
  - Parse amount strings (remove commas -> Number)
  - Expand status codes (ACT -> ACTIVE)
  - Drop BORR_REC_TYP column

CDW_LN_PROD ────── transform ────► LoanProducts
  - Parse numeric strings -> Number
  - Convert status to boolean (ACT -> true)
  - Parse date strings

CDW_LN_ACCT ────── transform ────► LoanAccounts
  - Drop denormalized borrower fields (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4)
  - Parse all numeric/date strings
  - Expand status and property type codes

CDW_PMT_HIST ───── transform ────► Payments
  - Compute payment_sort_key = "{date}#{id}"
  - Rename LN_ACCT_NBR -> loan_account_id
  - Parse all numeric/date strings
  - Expand type and status codes
```

### 5.2 Migration Order

1. **Borrowers** first (no dependencies)
2. **LoanProducts** second (no dependencies)
3. **LoanAccounts** third (depends on Borrowers + LoanProducts)
4. **Payments** last (depends on LoanAccounts)

---

## 6. Comparison: Legacy MySQL vs Modern DynamoDB

| Aspect | Legacy MySQL (H2) | Modern DynamoDB |
|--------|-------------------|-----------------|
| **Data types** | All VARCHAR | Proper S, N, BOOL types |
| **Date handling** | `MM/DD/YYYY` strings (broken sorting) | ISO 8601 strings (correct sorting) |
| **Amount handling** | Comma-formatted strings | Native Number type |
| **Column names** | Cryptic (`BORR_FST_NM`) | Clear (`first_name`) |
| **Normalization** | Denormalized (borrower in loans) | Normalized (reference by ID) |
| **Referential integrity** | None (no FK constraints) | Application-level validation |
| **Indexes** | Only PKs | PKs + 9 GSIs for all access patterns |
| **Scalability** | Single-node H2 | Fully managed, auto-scaling |
| **Date sorting** | Lexicographic (incorrect) | Lexicographic (correct with ISO 8601) |
| **Query flexibility** | Full SQL | Key-based access via table + GSIs |
