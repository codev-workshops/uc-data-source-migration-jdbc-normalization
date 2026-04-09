#!/usr/bin/env python3
"""
Production-ready data migration script: Legacy MySQL (CDW) -> Modern DynamoDB.

Migrates 4 tables in dependency order:
  1. Borrowers      (CDW_BORR_MSTR)
  2. LoanProducts   (CDW_LN_PROD)
  3. LoanAccounts   (CDW_LN_ACCT)
  4. Payments        (CDW_PMT_HIST)

Features:
  - Async I/O with aioboto3 + aiomysql for non-blocking DB access
  - Configurable batch sizes for read (SELECT) and write (BatchWriteItem)
  - Exponential-backoff retry with jitter on transient failures
  - Comprehensive logging (progress bars, error counts, per-table summaries)
  - Dry-run mode for validation without writing to DynamoDB
  - Resumable: idempotent PutItem (safe to re-run)

Usage:
  pip install aioboto3 aiomysql
  python Datamigration.py                     # full migration
  python Datamigration.py --dry-run           # validate transforms only
  python Datamigration.py --tables Borrowers  # migrate single table
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import os
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Any

import aioboto3
import aiomysql

# =============================================================================
# CONFIGURATION
# =============================================================================

@dataclass
class MysqlConfig:
    """Legacy MySQL connection settings (source)."""
    host: str = os.getenv("MYSQL_HOST", "127.0.0.1")
    port: int = int(os.getenv("MYSQL_PORT", "3306"))
    user: str = os.getenv("MYSQL_USER", "root")
    password: str = os.getenv("MYSQL_PASSWORD", "")
    database: str = os.getenv("MYSQL_DATABASE", "legacydw")
    pool_size: int = int(os.getenv("MYSQL_POOL_SIZE", "5"))


@dataclass
class DynamoDbConfig:
    """Modern DynamoDB connection settings (target)."""
    region: str = os.getenv("DYNAMODB_REGION", "us-east-1")
    endpoint_url: str | None = os.getenv("DYNAMODB_ENDPOINT", None)
    aws_access_key_id: str | None = os.getenv("AWS_ACCESS_KEY_ID", None)
    aws_secret_access_key: str | None = os.getenv("AWS_SECRET_ACCESS_KEY", None)


@dataclass
class MigrationConfig:
    """Tuning parameters for the migration."""
    mysql: MysqlConfig = field(default_factory=MysqlConfig)
    dynamodb: DynamoDbConfig = field(default_factory=DynamoDbConfig)
    read_batch_size: int = int(os.getenv("READ_BATCH_SIZE", "500"))
    write_batch_size: int = int(os.getenv("WRITE_BATCH_SIZE", "25"))  # DynamoDB max
    max_retries: int = int(os.getenv("MAX_RETRIES", "5"))
    retry_base_delay: float = float(os.getenv("RETRY_BASE_DELAY", "0.5"))
    max_concurrent_writes: int = int(os.getenv("MAX_CONCURRENT_WRITES", "10"))
    dry_run: bool = False
    tables: list[str] = field(default_factory=list)


# =============================================================================
# LOGGING
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler("datamigration.log", mode="a", encoding="utf-8"),
    ],
)
logger = logging.getLogger("datamigration")


# =============================================================================
# TRANSFORMATION HELPERS
# =============================================================================

# Status code expansion maps
BORROWER_STATUS = {"ACT": "ACTIVE", "INA": "INACTIVE"}
LOAN_STATUS = {"ACT": "ACTIVE", "CLO": "CLOSED", "DFT": "DEFAULT", "FRB": "FORBEARANCE"}
PAYMENT_TYPE = {"REG": "REGULAR", "EXT": "EXTRA", "PRT": "PARTIAL", "PRE": "PREPAYMENT"}
PAYMENT_STATUS = {"PST": "POSTED", "REV": "REVERSED", "NSF": "NSF", "PND": "PENDING"}
PROPERTY_TYPE = {
    "SFR": "Single Family Residence",
    "CND": "Condominium",
    "MFR": "Multi-Family Residence",
    "TWN": "Townhouse",
}


def parse_date(value: str | None) -> str | None:
    """Convert MM/DD/YYYY -> YYYY-MM-DD. Returns None for missing/invalid."""
    if not value:
        return None
    try:
        dt = datetime.strptime(value.strip(), "%m/%d/%Y")
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        logger.warning("Invalid date value: %r", value)
        return None


def parse_timestamp(value: str | None) -> str | None:
    """Convert MM/DD/YYYY -> ISO 8601 YYYY-MM-DDTHH:mm:ssZ."""
    if not value:
        return None
    try:
        dt = datetime.strptime(value.strip(), "%m/%d/%Y")
        return dt.strftime("%Y-%m-%dT00:00:00Z")
    except ValueError:
        logger.warning("Invalid timestamp value: %r", value)
        return None


def parse_amount(value: str | None) -> Decimal | None:
    """Remove commas, parse to Decimal. Returns None for missing/invalid."""
    if not value:
        return None
    try:
        return Decimal(value.strip().replace(",", ""))
    except InvalidOperation:
        logger.warning("Invalid amount value: %r", value)
        return None


def parse_integer(value: str | None) -> int | None:
    """Parse string to integer. Returns None for missing/invalid."""
    if not value:
        return None
    try:
        return int(value.strip().replace(",", ""))
    except ValueError:
        logger.warning("Invalid integer value: %r", value)
        return None


def expand_code(value: str | None, mapping: dict[str, str]) -> str | None:
    """Expand a short status code to its full value."""
    if not value:
        return None
    expanded = mapping.get(value.strip())
    if expanded is None:
        logger.warning("Unknown status code: %r (returning as-is)", value)
        return value.strip()
    return expanded


def _dynamo_val(value: Any) -> dict[str, Any] | None:
    """Convert a Python value to a DynamoDB-typed attribute dict.
    Returns None to signal the attribute should be omitted (NULL values).
    """
    if value is None:
        return None
    if isinstance(value, bool):
        return {"BOOL": value}
    if isinstance(value, int):
        return {"N": str(value)}
    if isinstance(value, Decimal):
        return {"N": str(value)}
    if isinstance(value, str):
        if value == "":
            return None  # DynamoDB does not allow empty strings as key attributes
        return {"S": value}
    return {"S": str(value)}


def build_item(mapping: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Build a DynamoDB item dict, dropping None values."""
    item: dict[str, dict[str, Any]] = {}
    for attr_name, python_value in mapping.items():
        ddb_val = _dynamo_val(python_value)
        if ddb_val is not None:
            item[attr_name] = ddb_val
    return item


# =============================================================================
# ROW TRANSFORMERS  (one per legacy table)
# =============================================================================

def transform_borrower(row: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """CDW_BORR_MSTR row -> Borrowers DynamoDB item."""
    return build_item({
        "borrower_id":       row["BORR_ID"],
        "first_name":        row["BORR_FST_NM"],
        "last_name":         row["BORR_LST_NM"],
        "middle_initial":    row.get("BORR_MID_INIT"),
        "ssn_hash":          row["BORR_SSN_ENCR"],
        "date_of_birth":     parse_date(row.get("BORR_DOB_DT")),
        "address_line1":     row["BORR_ADDR_LN1"],
        "address_line2":     row.get("BORR_ADDR_LN2"),
        "city":              row["BORR_CTY_NM"],
        "state":             row["BORR_ST_CD"],
        "zip_code":          row["BORR_ZIP_CD"],
        "phone":             row["BORR_PH_NBR"],
        "email":             row["BORR_EMAIL_ADDR"],
        "credit_score":      parse_integer(row.get("BORR_CRDT_SCR")),
        "employment_status": row["BORR_EMP_STAT"],
        "annual_income":     parse_amount(row.get("BORR_ANN_INCM")),
        "status":            expand_code(row.get("BORR_STAT_CD"), BORROWER_STATUS),
        "created_at":        parse_timestamp(row.get("BORR_CRET_DT")),
        "updated_at":        parse_timestamp(row.get("BORR_UPDT_DT")),
    })


def transform_loan_product(row: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """CDW_LN_PROD row -> LoanProducts DynamoDB item."""
    status_code = row.get("PROD_STAT_CD", "").strip()
    is_active = status_code == "ACT"

    return build_item({
        "product_code":    row["PROD_CD"],
        "name":            row["PROD_DESC_TXT"],
        "type":            row["PROD_TYP_CD"],
        "term_months":     parse_integer(row.get("PROD_TERM_MOS")),
        "rate_type":       row["PROD_RT_TYP"],
        "min_amount":      parse_amount(row.get("PROD_MIN_AMT")),
        "max_amount":      parse_amount(row.get("PROD_MAX_AMT")),
        "is_active":       is_active,
        "effective_date":  parse_date(row.get("PROD_EFF_DT")),
        "expiration_date": parse_date(row.get("PROD_EXP_DT")),
    })


def transform_loan_account(row: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """CDW_LN_ACCT row -> LoanAccounts DynamoDB item.
    Drops denormalized borrower fields (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4).
    """
    return build_item({
        "account_number":    row["LN_ACCT_NBR"],
        "borrower_id":       row["BORR_ID"],
        "product_code":      row["PROD_CD"],
        "original_amount":   parse_amount(row.get("LN_ORIG_AMT")),
        "current_balance":   parse_amount(row.get("LN_CURR_BAL")),
        "interest_rate":     parse_amount(row.get("LN_INT_RT")),
        "term_months":       parse_integer(row.get("LN_TERM_MOS")),
        "monthly_payment":   parse_amount(row.get("LN_PMT_AMT")),
        "origination_date":  parse_date(row.get("LN_ORIG_DT")),
        "maturity_date":     parse_date(row.get("LN_MAT_DT")),
        "first_payment_date": parse_date(row.get("LN_1ST_PMT_DT")),
        "next_payment_date": parse_date(row.get("LN_NXT_PMT_DT")),
        "status":            expand_code(row.get("LN_STAT_CD"), LOAN_STATUS),
        "delinquency_days":  parse_integer(row.get("LN_DLQ_DAYS")),
        "escrow_balance":    parse_amount(row.get("LN_ESCROW_BAL")),
        "ltv_percent":       parse_amount(row.get("LN_LTV_PCT")),
        "property_address":  row.get("PROP_ADDR_LN1"),
        "property_city":     row.get("PROP_CTY_NM"),
        "property_state":    row.get("PROP_ST_CD"),
        "property_zip":      row.get("PROP_ZIP_CD"),
        "property_type":     expand_code(row.get("PROP_TYP_CD"), PROPERTY_TYPE),
        "appraised_value":   parse_amount(row.get("PROP_APRS_VAL")),
        "created_at":        parse_timestamp(row.get("LN_CRET_DT")),
        "updated_at":        parse_timestamp(row.get("LN_UPDT_DT")),
    })


def transform_payment(row: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """CDW_PMT_HIST row -> Payments DynamoDB item.
    Generates the composite sort key: {YYYY-MM-DD}#{payment_id}.
    """
    payment_id = row["PMT_SEQ_NBR"]
    payment_date = parse_date(row.get("PMT_DT"))
    sort_key = f"{payment_date}#{payment_id}" if payment_date else f"0000-00-00#{payment_id}"

    return build_item({
        "loan_account_id":  row["LN_ACCT_NBR"],
        "payment_sort_key": sort_key,
        "payment_id":       payment_id,
        "payment_date":     payment_date,
        "total_amount":     parse_amount(row.get("PMT_AMT")),
        "principal_amount": parse_amount(row.get("PMT_PRIN_AMT")),
        "interest_amount":  parse_amount(row.get("PMT_INT_AMT")),
        "escrow_amount":    parse_amount(row.get("PMT_ESCROW_AMT")),
        "late_fee":         parse_amount(row.get("PMT_LATE_FEE")),
        "type":             expand_code(row.get("PMT_TYP_CD"), PAYMENT_TYPE),
        "status":           expand_code(row.get("PMT_STAT_CD"), PAYMENT_STATUS),
        "received_date":    parse_date(row.get("PMT_RECV_DT")),
        "processed_date":   parse_date(row.get("PMT_PROC_DT")),
        "created_at":       parse_timestamp(row.get("PMT_CRET_DT")),
        "updated_at":       parse_timestamp(row.get("PMT_UPDT_DT")),
    })


# =============================================================================
# TABLE MIGRATION DESCRIPTORS
# =============================================================================

@dataclass
class TableMigration:
    """Describes how to migrate one legacy table to a DynamoDB table."""
    name: str               # DynamoDB table name (display name)
    legacy_table: str       # MySQL table name
    dynamo_table: str       # DynamoDB table name
    primary_key: str        # Legacy primary key column (for ORDER BY in batched reads)
    transform: Any          # Callable[[dict], dict]  row transformer


TABLE_MIGRATIONS: list[TableMigration] = [
    TableMigration(
        name="Borrowers",
        legacy_table="CDW_BORR_MSTR",
        dynamo_table="Borrowers",
        primary_key="BORR_ID",
        transform=transform_borrower,
    ),
    TableMigration(
        name="LoanProducts",
        legacy_table="CDW_LN_PROD",
        dynamo_table="LoanProducts",
        primary_key="PROD_CD",
        transform=transform_loan_product,
    ),
    TableMigration(
        name="LoanAccounts",
        legacy_table="CDW_LN_ACCT",
        dynamo_table="LoanAccounts",
        primary_key="LN_ACCT_NBR",
        transform=transform_loan_account,
    ),
    TableMigration(
        name="Payments",
        legacy_table="CDW_PMT_HIST",
        dynamo_table="Payments",
        primary_key="PMT_SEQ_NBR",
        transform=transform_payment,
    ),
]


# =============================================================================
# CORE MIGRATION ENGINE
# =============================================================================

class MigrationEngine:
    """Async engine that reads from MySQL in batches and writes to DynamoDB."""

    def __init__(self, config: MigrationConfig) -> None:
        self.config = config
        self._mysql_pool: aiomysql.Pool | None = None
        self._session: aioboto3.Session | None = None
        self._write_semaphore = asyncio.Semaphore(config.max_concurrent_writes)
        self.stats: dict[str, dict[str, int]] = {}

    # -------------------------------------------------------------------------
    # Lifecycle
    # -------------------------------------------------------------------------

    async def connect(self) -> None:
        """Establish connections to MySQL and DynamoDB."""
        logger.info("Connecting to legacy MySQL at %s:%d/%s ...",
                     self.config.mysql.host, self.config.mysql.port,
                     self.config.mysql.database)
        self._mysql_pool = await aiomysql.create_pool(
            host=self.config.mysql.host,
            port=self.config.mysql.port,
            user=self.config.mysql.user,
            password=self.config.mysql.password,
            db=self.config.mysql.database,
            maxsize=self.config.mysql.pool_size,
            autocommit=True,
            cursorclass=aiomysql.DictCursor,
        )
        logger.info("MySQL connection pool ready (pool_size=%d)", self.config.mysql.pool_size)

        self._session = aioboto3.Session(
            aws_access_key_id=self.config.dynamodb.aws_access_key_id,
            aws_secret_access_key=self.config.dynamodb.aws_secret_access_key,
            region_name=self.config.dynamodb.region,
        )
        logger.info("DynamoDB session ready (region=%s, endpoint=%s)",
                     self.config.dynamodb.region,
                     self.config.dynamodb.endpoint_url or "AWS default")

    async def disconnect(self) -> None:
        """Clean up connections."""
        if self._mysql_pool:
            self._mysql_pool.close()
            await self._mysql_pool.wait_closed()
            logger.info("MySQL connection pool closed")

    # -------------------------------------------------------------------------
    # MySQL reading (batched with cursor-based pagination)
    # -------------------------------------------------------------------------

    async def _count_rows(self, table_name: str) -> int:
        """Get total row count for progress tracking."""
        assert self._mysql_pool is not None
        async with self._mysql_pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(f"SELECT COUNT(*) AS cnt FROM {table_name}")
                result = await cur.fetchone()
                return result["cnt"]

    async def _fetch_batch(self, table_name: str, pk_column: str,
                           offset: int, limit: int) -> list[dict[str, Any]]:
        """Fetch a batch of rows ordered by primary key."""
        assert self._mysql_pool is not None
        async with self._mysql_pool.acquire() as conn:
            async with conn.cursor() as cur:
                query = (f"SELECT * FROM {table_name} "
                         f"ORDER BY {pk_column} "
                         f"LIMIT %s OFFSET %s")
                await cur.execute(query, (limit, offset))
                rows = await cur.fetchall()
                return rows

    # -------------------------------------------------------------------------
    # DynamoDB writing (BatchWriteItem with retry)
    # -------------------------------------------------------------------------

    async def _write_batch_with_retry(self, dynamo_client: Any,
                                      table_name: str,
                                      items: list[dict]) -> int:
        """Write a batch of items to DynamoDB with exponential backoff retry.

        Returns the number of successfully written items.
        """
        if not items:
            return 0

        request_items = {
            table_name: [{"PutRequest": {"Item": item}} for item in items]
        }

        for attempt in range(1, self.config.max_retries + 1):
            try:
                async with self._write_semaphore:
                    response = await dynamo_client.batch_write_item(
                        RequestItems=request_items
                    )

                # Handle unprocessed items (DynamoDB throttling)
                unprocessed = response.get("UnprocessedItems", {})
                if not unprocessed or table_name not in unprocessed:
                    return len(items)

                # Retry only the unprocessed items
                failed_count = len(unprocessed[table_name])
                logger.warning(
                    "  %d/%d items unprocessed (attempt %d/%d), retrying...",
                    failed_count, len(items), attempt, self.config.max_retries,
                )
                request_items = unprocessed
                items = [req["PutRequest"]["Item"] for req in unprocessed[table_name]]

                # Exponential backoff with jitter
                delay = self.config.retry_base_delay * (2 ** (attempt - 1))
                jitter = delay * 0.1 * (hash(str(time.time())) % 10) / 10
                await asyncio.sleep(delay + jitter)

            except Exception as exc:
                if attempt == self.config.max_retries:
                    logger.error(
                        "  Batch write FAILED after %d attempts: %s",
                        self.config.max_retries, exc,
                    )
                    return 0

                delay = self.config.retry_base_delay * (2 ** (attempt - 1))
                logger.warning(
                    "  Batch write error (attempt %d/%d): %s — retrying in %.1fs",
                    attempt, self.config.max_retries, exc, delay,
                )
                await asyncio.sleep(delay)

        return 0

    # -------------------------------------------------------------------------
    # Single-table migration
    # -------------------------------------------------------------------------

    async def _migrate_table(self, migration: TableMigration) -> None:
        """Migrate a single legacy table to DynamoDB."""
        table_name = migration.name
        stats = {"total": 0, "migrated": 0, "errors": 0, "skipped": 0}
        self.stats[table_name] = stats

        logger.info("=" * 60)
        logger.info("MIGRATING: %s  (%s -> %s)",
                     table_name, migration.legacy_table, migration.dynamo_table)
        logger.info("=" * 60)

        total_rows = await self._count_rows(migration.legacy_table)
        stats["total"] = total_rows
        logger.info("  Total rows to migrate: %d", total_rows)

        if total_rows == 0:
            logger.info("  No rows found — skipping")
            return

        start_time = time.monotonic()
        offset = 0

        assert self._session is not None
        dynamo_kwargs: dict[str, Any] = {"service_name": "dynamodb"}
        if self.config.dynamodb.endpoint_url:
            dynamo_kwargs["endpoint_url"] = self.config.dynamodb.endpoint_url

        async with self._session.client(**dynamo_kwargs) as dynamo_client:
            while offset < total_rows:
                # Fetch batch from MySQL
                rows = await self._fetch_batch(
                    migration.legacy_table,
                    migration.primary_key,
                    offset,
                    self.config.read_batch_size,
                )
                if not rows:
                    break

                # Transform rows
                transformed_items: list[dict] = []
                for row in rows:
                    try:
                        item = migration.transform(row)
                        transformed_items.append(item)
                    except Exception as exc:
                        stats["errors"] += 1
                        pk_val = row.get(migration.primary_key, "?")
                        logger.error(
                            "  Transform error for %s=%s: %s", migration.primary_key, pk_val, exc
                        )

                # Write to DynamoDB in sub-batches of 25 (BatchWriteItem limit)
                if not self.config.dry_run:
                    write_tasks = []
                    for i in range(0, len(transformed_items), self.config.write_batch_size):
                        chunk = transformed_items[i : i + self.config.write_batch_size]
                        write_tasks.append(
                            self._write_batch_with_retry(
                                dynamo_client, migration.dynamo_table, chunk
                            )
                        )

                    results = await asyncio.gather(*write_tasks, return_exceptions=True)
                    for result in results:
                        if isinstance(result, Exception):
                            logger.error("  Unexpected write error: %s", result)
                            stats["errors"] += self.config.write_batch_size
                        elif isinstance(result, int):
                            stats["migrated"] += result
                else:
                    stats["skipped"] += len(transformed_items)
                    logger.info("  [DRY RUN] Would write %d items", len(transformed_items))

                offset += len(rows)

                # Progress logging
                elapsed = time.monotonic() - start_time
                rate = offset / elapsed if elapsed > 0 else 0
                pct = min(100.0, (offset / total_rows) * 100)
                logger.info(
                    "  Progress: %d/%d (%.1f%%) | %.0f rows/sec | errors: %d",
                    offset, total_rows, pct, rate, stats["errors"],
                )

        elapsed = time.monotonic() - start_time
        logger.info("  Completed %s in %.1fs — migrated: %d, errors: %d, skipped: %d",
                     table_name, elapsed, stats["migrated"], stats["errors"], stats["skipped"])

    # -------------------------------------------------------------------------
    # Full migration orchestrator
    # -------------------------------------------------------------------------

    async def run(self) -> None:
        """Run the full migration pipeline."""
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        logger.info("=" * 60)
        logger.info("DATA MIGRATION START  (run_id=%s)", run_id)
        logger.info("  Mode: %s", "DRY RUN" if self.config.dry_run else "LIVE")
        logger.info("  Read batch size: %d", self.config.read_batch_size)
        logger.info("  Write batch size: %d", self.config.write_batch_size)
        logger.info("  Max retries: %d", self.config.max_retries)
        logger.info("  Concurrent writes: %d", self.config.max_concurrent_writes)
        logger.info("=" * 60)

        total_start = time.monotonic()

        # Filter tables if specified
        migrations = TABLE_MIGRATIONS
        if self.config.tables:
            selected = {t.lower() for t in self.config.tables}
            migrations = [m for m in migrations if m.name.lower() in selected]
            if not migrations:
                logger.error("No matching tables found for: %s", self.config.tables)
                return

        await self.connect()
        try:
            # Migrate tables in dependency order
            for migration in migrations:
                await self._migrate_table(migration)
        finally:
            await self.disconnect()

        total_elapsed = time.monotonic() - total_start

        # Final summary
        logger.info("")
        logger.info("=" * 60)
        logger.info("MIGRATION SUMMARY  (run_id=%s)", run_id)
        logger.info("=" * 60)
        grand_total = 0
        grand_migrated = 0
        grand_errors = 0
        for table_name, s in self.stats.items():
            logger.info(
                "  %-15s | total: %6d | migrated: %6d | errors: %4d | skipped: %4d",
                table_name, s["total"], s["migrated"], s["errors"], s["skipped"],
            )
            grand_total += s["total"]
            grand_migrated += s["migrated"]
            grand_errors += s["errors"]
        logger.info("-" * 60)
        logger.info(
            "  %-15s | total: %6d | migrated: %6d | errors: %4d",
            "GRAND TOTAL", grand_total, grand_migrated, grand_errors,
        )
        logger.info("  Total time: %.1fs", total_elapsed)
        logger.info("=" * 60)

        if grand_errors > 0:
            logger.warning("Migration completed WITH ERRORS. Review log for details.")
            sys.exit(1)
        else:
            logger.info("Migration completed successfully.")


# =============================================================================
# CLI
# =============================================================================

def parse_args() -> MigrationConfig:
    """Parse command-line arguments and build the migration config."""
    parser = argparse.ArgumentParser(
        description="Migrate legacy MySQL CDW data to modern DynamoDB schema.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Environment variables (override defaults):
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE
  DYNAMODB_REGION, DYNAMODB_ENDPOINT
  AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
  READ_BATCH_SIZE, WRITE_BATCH_SIZE, MAX_RETRIES, MAX_CONCURRENT_WRITES
        """,
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Validate transforms without writing to DynamoDB",
    )
    parser.add_argument(
        "--tables", nargs="+", metavar="TABLE",
        help="Migrate only specified tables (e.g., --tables Borrowers Payments)",
    )
    parser.add_argument(
        "--read-batch-size", type=int, default=None,
        help="Number of rows to read per MySQL batch (default: 500)",
    )
    parser.add_argument(
        "--write-batch-size", type=int, default=None,
        help="Number of items per DynamoDB BatchWriteItem (max 25, default: 25)",
    )
    parser.add_argument(
        "--max-retries", type=int, default=None,
        help="Maximum retry attempts for failed writes (default: 5)",
    )
    parser.add_argument(
        "--max-concurrent-writes", type=int, default=None,
        help="Maximum concurrent DynamoDB batch writes (default: 10)",
    )

    args = parser.parse_args()
    config = MigrationConfig()
    config.dry_run = args.dry_run

    if args.tables:
        config.tables = args.tables
    if args.read_batch_size is not None:
        config.read_batch_size = args.read_batch_size
    if args.write_batch_size is not None:
        config.write_batch_size = min(args.write_batch_size, 25)
    if args.max_retries is not None:
        config.max_retries = args.max_retries
    if args.max_concurrent_writes is not None:
        config.max_concurrent_writes = args.max_concurrent_writes

    return config


async def main() -> None:
    """Entry point."""
    config = parse_args()
    engine = MigrationEngine(config)
    await engine.run()


if __name__ == "__main__":
    asyncio.run(main())
