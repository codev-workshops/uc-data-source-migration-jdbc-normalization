# Data Migration Script: Legacy MySQL -> Modern DynamoDB

Production-ready async Python script that migrates data from the legacy CDW MySQL tables to the modern DynamoDB schema.

## Prerequisites

- **Python 3.10+**
- **MySQL** — The legacy database must be accessible
- **DynamoDB** — Either AWS DynamoDB or [DynamoDB Local](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) for development
- DynamoDB tables must already exist (use `DynamoDbTableInitializer` in the Spring Boot app with `--spring.profiles.active=dynamodb-init`, or create them via AWS CLI / CloudFormation using `data/dynamodb-schema/table_definitions.json`)

## Setup

```bash
cd scripts/

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

## Configuration

All settings are controlled via **environment variables** (or CLI flags where noted):

### MySQL (Source)

| Variable | Default | Description |
|---|---|---|
| `MYSQL_HOST` | `127.0.0.1` | MySQL host |
| `MYSQL_PORT` | `3306` | MySQL port |
| `MYSQL_USER` | `root` | MySQL user |
| `MYSQL_PASSWORD` | *(empty)* | MySQL password |
| `MYSQL_DATABASE` | `legacydw` | Legacy database name |
| `MYSQL_POOL_SIZE` | `5` | Connection pool size |

### DynamoDB (Target)

| Variable | Default | Description |
|---|---|---|
| `DYNAMODB_REGION` | `us-east-1` | AWS region |
| `DYNAMODB_ENDPOINT` | *(none)* | Custom endpoint (e.g., `http://localhost:8000` for DynamoDB Local) |
| `AWS_ACCESS_KEY_ID` | *(none)* | AWS access key (uses default credential chain if not set) |
| `AWS_SECRET_ACCESS_KEY` | *(none)* | AWS secret key |

### Tuning

| Variable | Default | Description |
|---|---|---|
| `READ_BATCH_SIZE` | `500` | Rows fetched per MySQL query |
| `WRITE_BATCH_SIZE` | `25` | Items per DynamoDB BatchWriteItem (max 25) |
| `MAX_RETRIES` | `5` | Retry attempts for failed writes |
| `MAX_CONCURRENT_WRITES` | `10` | Parallel DynamoDB batch write operations |

## Usage

### Dry Run (validate transforms without writing)

```bash
python Datamigration.py --dry-run
```

### Full Migration

```bash
# Against AWS DynamoDB
export MYSQL_HOST=your-mysql-host
export MYSQL_PASSWORD=your-password
export MYSQL_DATABASE=legacydw
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export DYNAMODB_REGION=us-east-1

python Datamigration.py
```

### Local Development (DynamoDB Local)

```bash
# Start DynamoDB Local
docker run -d -p 8000:8000 amazon/dynamodb-local

# Run migration against local endpoints
export MYSQL_HOST=127.0.0.1
export MYSQL_PASSWORD=root
export DYNAMODB_ENDPOINT=http://localhost:8000

python Datamigration.py
```

### Migrate Specific Tables

```bash
# Only migrate Borrowers and Payments
python Datamigration.py --tables Borrowers Payments
```

### Custom Batch Sizes

```bash
# Larger read batches, conservative writes
python Datamigration.py --read-batch-size 1000 --max-concurrent-writes 5
```

## Migration Order

Tables are migrated in dependency order:

1. **Borrowers** (CDW_BORR_MSTR) — no dependencies
2. **LoanProducts** (CDW_LN_PROD) — no dependencies
3. **LoanAccounts** (CDW_LN_ACCT) — references Borrowers and LoanProducts
4. **Payments** (CDW_PMT_HIST) — references LoanAccounts

## Data Transformations

The script applies these transformations during migration:

| Transformation | Example |
|---|---|
| Date format | `03/15/1978` -> `1978-03-15` |
| Timestamp format | `01/15/2019` -> `2019-01-15T00:00:00Z` |
| Amount parsing | `"92,500"` -> `92500` (Decimal) |
| Integer parsing | `"745"` -> `745` (int) |
| Status expansion | `ACT` -> `ACTIVE`, `CLO` -> `CLOSED` |
| Property type expansion | `SFR` -> `Single Family Residence` |
| Payment type expansion | `REG` -> `REGULAR`, `PRE` -> `PREPAYMENT` |
| Composite sort key | Generated: `{date}#{payment_id}` |
| Denormalization removal | Drops `BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4` from loan accounts |

## Logging

- Console output: real-time progress with row counts and rates
- File output: `datamigration.log` (appended per run)
- Each run gets a unique `run_id` (timestamp-based) for traceability

## Error Handling

- **Transient DynamoDB errors**: Retried with exponential backoff + jitter (up to `MAX_RETRIES`)
- **Unprocessed items**: Automatically retried via DynamoDB's `UnprocessedItems` response
- **Transform errors**: Logged and counted; migration continues for remaining rows
- **Exit code**: `0` on success, `1` if any errors occurred

## Resumability

The script uses `PutItem` semantics (idempotent upsert), so it is **safe to re-run**. If a migration is interrupted, simply run it again — already-migrated items will be overwritten with identical data.
