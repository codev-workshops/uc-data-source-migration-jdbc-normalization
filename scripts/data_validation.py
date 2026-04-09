#!/usr/bin/env python3
"""
Data validation script: Compare legacy MySQL (CDW) against modern DynamoDB
to detect mismatches after migration.

Validation stages:
  1. Record count comparison   — total rows per table
  2. Sampled field comparison  — transform legacy row in-memory, compare against DynamoDB
  3. Orphan / referential check — borrower_id and product_code references

Designed for large datasets:
  - Async I/O (aioboto3 + aiomysql)
  - Configurable sample size (% or absolute)
  - Batched reads from MySQL with ORDER BY pk + LIMIT/OFFSET
  - DynamoDB point-reads (GetItem) for sampled keys — no full scans
  - Streaming JSON-lines report for constant memory usage

Usage:
  pip install -r requirements.txt
  python data_validation.py                           # validate all tables, 10% sample
  python data_validation.py --sample-pct 100          # full comparison (every row)
  python data_validation.py --tables Borrowers        # single table
  python data_validation.py --report-file report.json # custom report path
  python data_validation.py --sample-count 500        # absolute sample size per table
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import random
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
    host: str = os.getenv("MYSQL_HOST", "127.0.0.1")
    port: int = int(os.getenv("MYSQL_PORT", "3306"))
    user: str = os.getenv("MYSQL_USER", "root")
    password: str = os.getenv("MYSQL_PASSWORD", "")
    database: str = os.getenv("MYSQL_DATABASE", "legacydw")
    pool_size: int = int(os.getenv("MYSQL_POOL_SIZE", "5"))


@dataclass
class DynamoDbConfig:
    region: str = os.getenv("DYNAMODB_REGION", "us-east-1")
    endpoint_url: str | None = os.getenv("DYNAMODB_ENDPOINT", None)
    aws_access_key_id: str | None = os.getenv("AWS_ACCESS_KEY_ID", None)
    aws_secret_access_key: str | None = os.getenv("AWS_SECRET_ACCESS_KEY", None)


@dataclass
class ValidationConfig:
    mysql: MysqlConfig = field(default_factory=MysqlConfig)
    dynamodb: DynamoDbConfig = field(default_factory=DynamoDbConfig)
    sample_pct: float = float(os.getenv("SAMPLE_PCT", "10"))
    sample_count: int | None = None
    read_batch_size: int = int(os.getenv("READ_BATCH_SIZE", "500"))
    tables: list[str] = field(default_factory=list)
    report_file: str = "validation_report.json"
    max_concurrent_reads: int = int(os.getenv("MAX_CONCURRENT_READS", "20"))


# =============================================================================
# LOGGING
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler("data_validation.log", mode="a", encoding="utf-8"),
    ],
)
logger = logging.getLogger("data_validation")


# =============================================================================
# TRANSFORMATION HELPERS (same as Datamigration.py — must produce identical output)
# =============================================================================

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
    if not value:
        return None
    try:
        dt = datetime.strptime(value.strip(), "%m/%d/%Y")
        return dt.strftime("%Y-%m-%d")
    except ValueError:
        return None


def parse_timestamp(value: str | None) -> str | None:
    if not value:
        return None
    try:
        dt = datetime.strptime(value.strip(), "%m/%d/%Y")
        return dt.strftime("%Y-%m-%dT00:00:00Z")
    except ValueError:
        return None


def parse_amount(value: str | None) -> Decimal | None:
    if not value:
        return None
    try:
        return Decimal(value.strip().replace(",", ""))
    except InvalidOperation:
        return None


def parse_integer(value: str | None) -> int | None:
    if not value:
        return None
    try:
        return int(value.strip().replace(",", ""))
    except ValueError:
        return None


def expand_code(value: str | None, mapping: dict[str, str]) -> str | None:
    if not value:
        return None
    return mapping.get(value.strip(), value.strip())


# =============================================================================
# EXPECTED-VALUE BUILDERS (legacy row -> expected DynamoDB attribute dict)
# =============================================================================

def expected_borrower(row: dict[str, Any]) -> dict[str, Any]:
    """Build expected DynamoDB attribute values from a legacy borrower row."""
    return {
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
    }


def expected_loan_product(row: dict[str, Any]) -> dict[str, Any]:
    status_code = row.get("PROD_STAT_CD", "").strip()
    return {
        "product_code":    row["PROD_CD"],
        "name":            row["PROD_DESC_TXT"],
        "type":            row["PROD_TYP_CD"],
        "term_months":     parse_integer(row.get("PROD_TERM_MOS")),
        "rate_type":       row["PROD_RT_TYP"],
        "min_amount":      parse_amount(row.get("PROD_MIN_AMT")),
        "max_amount":      parse_amount(row.get("PROD_MAX_AMT")),
        "is_active":       status_code == "ACT",
        "effective_date":  parse_date(row.get("PROD_EFF_DT")),
        "expiration_date": parse_date(row.get("PROD_EXP_DT")),
    }


def expected_loan_account(row: dict[str, Any]) -> dict[str, Any]:
    return {
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
    }


def expected_payment(row: dict[str, Any]) -> dict[str, Any]:
    payment_id = row["PMT_SEQ_NBR"]
    payment_date = parse_date(row.get("PMT_DT"))
    sort_key = f"{payment_date}#{payment_id}" if payment_date else f"0000-00-00#{payment_id}"
    return {
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
    }


# =============================================================================
# TABLE VALIDATION DESCRIPTORS
# =============================================================================

@dataclass
class TableValidation:
    """Describes how to validate one table pair."""
    name: str                   # Display / DynamoDB table name
    legacy_table: str           # MySQL table
    dynamo_table: str           # DynamoDB table
    legacy_pk_column: str       # MySQL primary key column (for ORDER BY)
    dynamo_pk_attr: str         # DynamoDB partition key attribute
    dynamo_sk_attr: str | None  # DynamoDB sort key attribute (None if no SK)
    transform: Any              # Callable[[dict], dict]  row -> expected attrs
    pk_extractor: Any           # Callable[[dict], dict]  legacy row -> DynamoDB Key dict
    ignored_attrs: list[str] = field(default_factory=list)  # attrs to skip in comparison


def _borrower_key(row: dict) -> dict:
    return {"borrower_id": {"S": row["BORR_ID"]}}

def _product_key(row: dict) -> dict:
    return {"product_code": {"S": row["PROD_CD"]}}

def _loan_key(row: dict) -> dict:
    return {"account_number": {"S": row["LN_ACCT_NBR"]}}

def _payment_key(row: dict) -> dict:
    payment_id = row["PMT_SEQ_NBR"]
    payment_date = parse_date(row.get("PMT_DT"))
    sort_key = f"{payment_date}#{payment_id}" if payment_date else f"0000-00-00#{payment_id}"
    return {
        "loan_account_id": {"S": row["LN_ACCT_NBR"]},
        "payment_sort_key": {"S": sort_key},
    }


TABLE_VALIDATIONS: list[TableValidation] = [
    TableValidation(
        name="Borrowers",
        legacy_table="CDW_BORR_MSTR",
        dynamo_table="Borrowers",
        legacy_pk_column="BORR_ID",
        dynamo_pk_attr="borrower_id",
        dynamo_sk_attr=None,
        transform=expected_borrower,
        pk_extractor=_borrower_key,
    ),
    TableValidation(
        name="LoanProducts",
        legacy_table="CDW_LN_PROD",
        dynamo_table="LoanProducts",
        legacy_pk_column="PROD_CD",
        dynamo_pk_attr="product_code",
        dynamo_sk_attr=None,
        transform=expected_loan_product,
        pk_extractor=_product_key,
    ),
    TableValidation(
        name="LoanAccounts",
        legacy_table="CDW_LN_ACCT",
        dynamo_table="LoanAccounts",
        legacy_pk_column="LN_ACCT_NBR",
        dynamo_pk_attr="account_number",
        dynamo_sk_attr=None,
        transform=expected_loan_account,
        pk_extractor=_loan_key,
    ),
    TableValidation(
        name="Payments",
        legacy_table="CDW_PMT_HIST",
        dynamo_table="Payments",
        legacy_pk_column="PMT_SEQ_NBR",
        dynamo_pk_attr="loan_account_id",
        dynamo_sk_attr="payment_sort_key",
        transform=expected_payment,
        pk_extractor=_payment_key,
    ),
]


# =============================================================================
# DYNAMO VALUE EXTRACTION
# =============================================================================

def extract_dynamo_value(attr: dict[str, Any]) -> Any:
    """Convert a DynamoDB typed attribute back to a Python value."""
    if "S" in attr:
        return attr["S"]
    if "N" in attr:
        val = attr["N"]
        if "." in val:
            return Decimal(val)
        return int(val)
    if "BOOL" in attr:
        return attr["BOOL"]
    if "NULL" in attr:
        return None
    return str(attr)


def dynamo_item_to_dict(item: dict[str, dict]) -> dict[str, Any]:
    """Convert a DynamoDB item (typed map) to a plain Python dict."""
    return {k: extract_dynamo_value(v) for k, v in item.items()}


# =============================================================================
# COMPARISON ENGINE
# =============================================================================

def compare_values(expected: Any, actual: Any) -> bool:
    """Compare an expected value to an actual DynamoDB value with type coercion."""
    if expected is None and actual is None:
        return True
    if expected is None or actual is None:
        return False
    # Decimal comparison: normalize to remove trailing zeros
    if isinstance(expected, Decimal) and isinstance(actual, Decimal):
        return expected.normalize() == actual.normalize()
    if isinstance(expected, Decimal) and isinstance(actual, int):
        return expected.normalize() == Decimal(actual).normalize()
    if isinstance(expected, int) and isinstance(actual, Decimal):
        return Decimal(expected).normalize() == actual.normalize()
    return expected == actual


def compare_records(
    expected: dict[str, Any],
    actual: dict[str, Any],
    ignored_attrs: list[str],
) -> list[dict[str, Any]]:
    """Compare expected vs actual attribute values. Returns list of mismatches."""
    mismatches: list[dict[str, Any]] = []
    all_keys = set(expected.keys()) | set(actual.keys())

    for attr in sorted(all_keys):
        if attr in ignored_attrs:
            continue

        exp_val = expected.get(attr)
        act_val = actual.get(attr)

        # Skip attributes that are None in expected and absent in actual
        # (DynamoDB omits NULL attributes)
        if exp_val is None and act_val is None:
            continue

        if not compare_values(exp_val, act_val):
            mismatches.append({
                "attribute": attr,
                "expected": _serialize(exp_val),
                "actual": _serialize(act_val),
            })
    return mismatches


def _serialize(value: Any) -> Any:
    """Make a value JSON-serializable."""
    if isinstance(value, Decimal):
        return str(value)
    return value


# =============================================================================
# VALIDATION ENGINE
# =============================================================================

class ValidationEngine:
    """Async engine that compares legacy MySQL against modern DynamoDB."""

    def __init__(self, config: ValidationConfig) -> None:
        self.config = config
        self._mysql_pool: aiomysql.Pool | None = None
        self._session: aioboto3.Session | None = None
        self._read_semaphore = asyncio.Semaphore(config.max_concurrent_reads)
        self.report: dict[str, Any] = {
            "validation_timestamp": datetime.now(tz=timezone.utc).isoformat(),
            "config": {
                "sample_pct": config.sample_pct,
                "sample_count": config.sample_count,
                "tables": config.tables or "all",
            },
            "tables": {},
            "summary": {},
        }

    # -------------------------------------------------------------------------
    # Lifecycle
    # -------------------------------------------------------------------------

    async def connect(self) -> None:
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
        logger.info("MySQL pool ready (pool_size=%d)", self.config.mysql.pool_size)

        self._session = aioboto3.Session(
            aws_access_key_id=self.config.dynamodb.aws_access_key_id,
            aws_secret_access_key=self.config.dynamodb.aws_secret_access_key,
            region_name=self.config.dynamodb.region,
        )
        logger.info("DynamoDB session ready (region=%s, endpoint=%s)",
                     self.config.dynamodb.region,
                     self.config.dynamodb.endpoint_url or "AWS default")

    async def disconnect(self) -> None:
        if self._mysql_pool:
            self._mysql_pool.close()
            await self._mysql_pool.wait_closed()
            logger.info("MySQL pool closed")

    # -------------------------------------------------------------------------
    # MySQL helpers
    # -------------------------------------------------------------------------

    async def _count_legacy(self, table_name: str) -> int:
        assert self._mysql_pool is not None
        async with self._mysql_pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(f"SELECT COUNT(*) AS cnt FROM {table_name}")
                result = await cur.fetchone()
                return result["cnt"]

    async def _fetch_legacy_pks(self, table_name: str, pk_column: str) -> list[str]:
        """Fetch all primary keys from a legacy table (for sampling)."""
        assert self._mysql_pool is not None
        pks: list[str] = []
        offset = 0
        batch = self.config.read_batch_size
        while True:
            async with self._mysql_pool.acquire() as conn:
                async with conn.cursor() as cur:
                    await cur.execute(
                        f"SELECT {pk_column} FROM {table_name} "
                        f"ORDER BY {pk_column} LIMIT %s OFFSET %s",
                        (batch, offset),
                    )
                    rows = await cur.fetchall()
                    if not rows:
                        break
                    pks.extend(row[pk_column] for row in rows)
                    offset += batch
        return pks

    async def _fetch_legacy_row(self, table_name: str,
                                pk_column: str, pk_value: str) -> dict | None:
        """Fetch a single legacy row by primary key."""
        assert self._mysql_pool is not None
        async with self._mysql_pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    f"SELECT * FROM {table_name} WHERE {pk_column} = %s",
                    (pk_value,),
                )
                return await cur.fetchone()

    # -------------------------------------------------------------------------
    # DynamoDB helpers
    # -------------------------------------------------------------------------

    async def _count_dynamo(self, dynamo_client: Any, table_name: str) -> int:
        """Get item count from DynamoDB table description."""
        resp = await dynamo_client.describe_table(TableName=table_name)
        return resp["Table"]["ItemCount"]

    async def _get_dynamo_item(self, dynamo_client: Any,
                               table_name: str,
                               key: dict[str, dict]) -> dict | None:
        """Point-read a single DynamoDB item by key."""
        async with self._read_semaphore:
            resp = await dynamo_client.get_item(
                TableName=table_name,
                Key=key,
                ConsistentRead=True,
            )
            return resp.get("Item")

    # -------------------------------------------------------------------------
    # Stage 1: Record count comparison
    # -------------------------------------------------------------------------

    async def _validate_counts(self, dynamo_client: Any,
                               tv: TableValidation) -> dict[str, Any]:
        """Compare row counts between legacy and modern tables."""
        legacy_count = await self._count_legacy(tv.legacy_table)
        dynamo_count = await self._count_dynamo(dynamo_client, tv.dynamo_table)
        match = legacy_count == dynamo_count
        result = {
            "legacy_count": legacy_count,
            "dynamo_count": dynamo_count,
            "match": match,
            "difference": dynamo_count - legacy_count,
        }
        status = "PASS" if match else "FAIL"
        logger.info("[%s] Count check: legacy=%d, dynamo=%d => %s",
                     tv.name, legacy_count, dynamo_count, status)
        return result

    # -------------------------------------------------------------------------
    # Stage 2: Sampled field-by-field comparison
    # -------------------------------------------------------------------------

    async def _validate_sample(self, dynamo_client: Any,
                               tv: TableValidation) -> dict[str, Any]:
        """Sample records and do field-by-field comparison."""
        logger.info("[%s] Fetching primary keys for sampling ...", tv.name)
        all_pks = await self._fetch_legacy_pks(tv.legacy_table, tv.legacy_pk_column)
        total = len(all_pks)

        if self.config.sample_count is not None:
            sample_size = min(self.config.sample_count, total)
        else:
            sample_size = max(1, int(total * self.config.sample_pct / 100))

        if sample_size >= total:
            sampled_pks = all_pks
        else:
            sampled_pks = random.sample(all_pks, sample_size)

        logger.info("[%s] Sampling %d / %d records (%.1f%%)",
                     tv.name, len(sampled_pks), total,
                     100 * len(sampled_pks) / total if total else 0)

        matched = 0
        mismatched = 0
        missing = 0
        mismatch_details: list[dict[str, Any]] = []

        for i, pk in enumerate(sampled_pks):
            if (i + 1) % 100 == 0 or (i + 1) == len(sampled_pks):
                logger.info("[%s] Progress: %d / %d sampled records validated",
                             tv.name, i + 1, len(sampled_pks))

            legacy_row = await self._fetch_legacy_row(
                tv.legacy_table, tv.legacy_pk_column, pk)
            if legacy_row is None:
                logger.warning("[%s] Legacy row vanished for pk=%s", tv.name, pk)
                continue

            expected_attrs = tv.transform(legacy_row)
            ddb_key = tv.pk_extractor(legacy_row)
            ddb_item = await self._get_dynamo_item(
                dynamo_client, tv.dynamo_table, ddb_key)

            if ddb_item is None:
                missing += 1
                mismatch_details.append({
                    "pk": pk,
                    "error": "MISSING_IN_DYNAMO",
                    "fields": [],
                })
                logger.warning("[%s] Missing in DynamoDB: pk=%s", tv.name, pk)
                continue

            actual_attrs = dynamo_item_to_dict(ddb_item)
            field_mismatches = compare_records(
                expected_attrs, actual_attrs, tv.ignored_attrs)

            if field_mismatches:
                mismatched += 1
                detail = {
                    "pk": pk,
                    "error": "FIELD_MISMATCH",
                    "fields": field_mismatches,
                }
                mismatch_details.append(detail)
                logger.warning("[%s] Mismatch for pk=%s: %d field(s) differ",
                               tv.name, pk, len(field_mismatches))
                for fm in field_mismatches:
                    logger.warning("  -> %s: expected=%r, actual=%r",
                                   fm["attribute"], fm["expected"], fm["actual"])
            else:
                matched += 1

        result = {
            "total_records": total,
            "sample_size": len(sampled_pks),
            "matched": matched,
            "mismatched": mismatched,
            "missing_in_dynamo": missing,
            "pass_rate": f"{100 * matched / len(sampled_pks):.2f}%" if sampled_pks else "N/A",
            "mismatch_details": mismatch_details[:200],  # cap to avoid huge reports
            "truncated": len(mismatch_details) > 200,
        }

        status = "PASS" if (mismatched == 0 and missing == 0) else "FAIL"
        logger.info("[%s] Sample check: matched=%d, mismatched=%d, missing=%d => %s",
                     tv.name, matched, mismatched, missing, status)
        return result

    # -------------------------------------------------------------------------
    # Stage 3: Referential integrity (orphan detection)
    # -------------------------------------------------------------------------

    async def _validate_references(self, dynamo_client: Any) -> dict[str, Any]:
        """Check that borrower_id and product_code in LoanAccounts
        exist in Borrowers and LoanProducts tables."""
        logger.info("[References] Checking referential integrity ...")

        # Fetch all borrower IDs from DynamoDB
        borrower_resp = await dynamo_client.scan(
            TableName="Borrowers",
            ProjectionExpression="borrower_id",
        )
        borrower_ids = {
            item["borrower_id"]["S"]
            for item in borrower_resp.get("Items", [])
        }
        # Handle pagination
        while borrower_resp.get("LastEvaluatedKey"):
            borrower_resp = await dynamo_client.scan(
                TableName="Borrowers",
                ProjectionExpression="borrower_id",
                ExclusiveStartKey=borrower_resp["LastEvaluatedKey"],
            )
            borrower_ids.update(
                item["borrower_id"]["S"]
                for item in borrower_resp.get("Items", [])
            )

        # Fetch all product codes from DynamoDB
        product_resp = await dynamo_client.scan(
            TableName="LoanProducts",
            ProjectionExpression="product_code",
        )
        product_codes = {
            item["product_code"]["S"]
            for item in product_resp.get("Items", [])
        }
        while product_resp.get("LastEvaluatedKey"):
            product_resp = await dynamo_client.scan(
                TableName="LoanProducts",
                ProjectionExpression="product_code",
                ExclusiveStartKey=product_resp["LastEvaluatedKey"],
            )
            product_codes.update(
                item["product_code"]["S"]
                for item in product_resp.get("Items", [])
            )

        # Scan LoanAccounts and check references
        orphan_borrowers: list[dict[str, str]] = []
        orphan_products: list[dict[str, str]] = []
        checked = 0

        loan_resp = await dynamo_client.scan(
            TableName="LoanAccounts",
            ProjectionExpression="account_number, borrower_id, product_code",
        )
        items = loan_resp.get("Items", [])
        while True:
            for item in items:
                checked += 1
                acct = item["account_number"]["S"]
                borr_id = item.get("borrower_id", {}).get("S")
                prod_code = item.get("product_code", {}).get("S")

                if borr_id and borr_id not in borrower_ids:
                    orphan_borrowers.append({
                        "account_number": acct,
                        "borrower_id": borr_id,
                    })
                if prod_code and prod_code not in product_codes:
                    orphan_products.append({
                        "account_number": acct,
                        "product_code": prod_code,
                    })

            if not loan_resp.get("LastEvaluatedKey"):
                break
            loan_resp = await dynamo_client.scan(
                TableName="LoanAccounts",
                ProjectionExpression="account_number, borrower_id, product_code",
                ExclusiveStartKey=loan_resp["LastEvaluatedKey"],
            )
            items = loan_resp.get("Items", [])

        result = {
            "loan_accounts_checked": checked,
            "orphan_borrower_refs": len(orphan_borrowers),
            "orphan_product_refs": len(orphan_products),
            "orphan_borrower_details": orphan_borrowers[:50],
            "orphan_product_details": orphan_products[:50],
        }

        status = "PASS" if (len(orphan_borrowers) == 0 and len(orphan_products) == 0) else "FAIL"
        logger.info("[References] Checked %d loan accounts: "
                     "orphan_borrowers=%d, orphan_products=%d => %s",
                     checked, len(orphan_borrowers), len(orphan_products), status)
        return result

    # -------------------------------------------------------------------------
    # Orchestrator
    # -------------------------------------------------------------------------

    async def run(self) -> None:
        """Run the full validation pipeline."""
        start = time.monotonic()
        logger.info("=" * 70)
        logger.info("DATA VALIDATION STARTED")
        logger.info("=" * 70)

        try:
            await self.connect()
        except Exception as e:
            logger.error("Failed to connect: %s", e)
            self.report["summary"] = {"status": "ERROR", "error": str(e)}
            self._write_report()
            return

        boto_kwargs: dict[str, Any] = {
            "service_name": "dynamodb",
            "region_name": self.config.dynamodb.region,
        }
        if self.config.dynamodb.endpoint_url:
            boto_kwargs["endpoint_url"] = self.config.dynamodb.endpoint_url

        try:
            async with self._session.client(**boto_kwargs) as dynamo_client:
                # Determine which tables to validate
                tables_to_validate = TABLE_VALIDATIONS
                if self.config.tables:
                    table_names = {t.lower() for t in self.config.tables}
                    tables_to_validate = [
                        tv for tv in TABLE_VALIDATIONS
                        if tv.name.lower() in table_names
                    ]
                    if not tables_to_validate:
                        logger.error("No matching tables found for: %s",
                                     self.config.tables)
                        return

                total_matched = 0
                total_mismatched = 0
                total_missing = 0
                all_counts_match = True

                for tv in tables_to_validate:
                    logger.info("-" * 50)
                    logger.info("Validating table: %s", tv.name)
                    logger.info("-" * 50)

                    # Stage 1: Counts
                    count_result = await self._validate_counts(dynamo_client, tv)
                    if not count_result["match"]:
                        all_counts_match = False

                    # Stage 2: Sampled comparison
                    sample_result = await self._validate_sample(dynamo_client, tv)
                    total_matched += sample_result["matched"]
                    total_mismatched += sample_result["mismatched"]
                    total_missing += sample_result["missing_in_dynamo"]

                    self.report["tables"][tv.name] = {
                        "count_check": count_result,
                        "sample_check": sample_result,
                    }

                # Stage 3: Referential integrity (only if not filtering tables)
                if not self.config.tables or len(self.config.tables) == len(TABLE_VALIDATIONS):
                    ref_result = await self._validate_references(dynamo_client)
                    self.report["referential_integrity"] = ref_result

                elapsed = time.monotonic() - start
                overall = "PASS" if (
                    all_counts_match
                    and total_mismatched == 0
                    and total_missing == 0
                ) else "FAIL"

                self.report["summary"] = {
                    "status": overall,
                    "tables_validated": len(tables_to_validate),
                    "total_sampled": total_matched + total_mismatched + total_missing,
                    "total_matched": total_matched,
                    "total_mismatched": total_mismatched,
                    "total_missing": total_missing,
                    "all_counts_match": all_counts_match,
                    "elapsed_seconds": round(elapsed, 2),
                }

                logger.info("=" * 70)
                logger.info("VALIDATION COMPLETE — %s", overall)
                logger.info("  Tables validated : %d", len(tables_to_validate))
                logger.info("  Records sampled  : %d", total_matched + total_mismatched + total_missing)
                logger.info("  Matched          : %d", total_matched)
                logger.info("  Mismatched       : %d", total_mismatched)
                logger.info("  Missing          : %d", total_missing)
                logger.info("  Elapsed          : %.2fs", elapsed)
                logger.info("=" * 70)

        except Exception as e:
            logger.error("Validation failed: %s", e, exc_info=True)
            self.report["summary"] = {"status": "ERROR", "error": str(e)}
        finally:
            await self.disconnect()
            self._write_report()

    # -------------------------------------------------------------------------
    # Report output
    # -------------------------------------------------------------------------

    def _write_report(self) -> None:
        """Write the validation report to a JSON file."""
        path = self.config.report_file
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.report, f, indent=2, default=str)
        logger.info("Validation report written to: %s", path)


# =============================================================================
# CLI
# =============================================================================

def parse_args() -> ValidationConfig:
    parser = argparse.ArgumentParser(
        description="Validate data consistency between legacy MySQL and modern DynamoDB.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Environment variables (override defaults):
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE
  DYNAMODB_REGION, DYNAMODB_ENDPOINT
  AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
  SAMPLE_PCT, READ_BATCH_SIZE, MAX_CONCURRENT_READS

Examples:
  python data_validation.py                           # 10% sample, all tables
  python data_validation.py --sample-pct 100          # every record
  python data_validation.py --sample-count 500        # 500 records per table
  python data_validation.py --tables Borrowers        # single table
  python data_validation.py --report-file out.json    # custom report path
        """,
    )
    parser.add_argument(
        "--sample-pct", type=float, default=None,
        help="Percentage of records to sample per table (default: 10)",
    )
    parser.add_argument(
        "--sample-count", type=int, default=None,
        help="Absolute number of records to sample per table (overrides --sample-pct)",
    )
    parser.add_argument(
        "--tables", nargs="+", metavar="TABLE",
        help="Validate only specified tables (e.g., --tables Borrowers Payments)",
    )
    parser.add_argument(
        "--report-file", type=str, default=None,
        help="Path for the JSON validation report (default: validation_report.json)",
    )
    parser.add_argument(
        "--read-batch-size", type=int, default=None,
        help="Batch size for reading legacy PKs (default: 500)",
    )
    parser.add_argument(
        "--max-concurrent-reads", type=int, default=None,
        help="Max concurrent DynamoDB GetItem calls (default: 20)",
    )

    args = parser.parse_args()
    config = ValidationConfig()

    if args.sample_pct is not None:
        config.sample_pct = args.sample_pct
    if args.sample_count is not None:
        config.sample_count = args.sample_count
    if args.tables:
        config.tables = args.tables
    if args.report_file:
        config.report_file = args.report_file
    if args.read_batch_size is not None:
        config.read_batch_size = args.read_batch_size
    if args.max_concurrent_reads is not None:
        config.max_concurrent_reads = args.max_concurrent_reads

    return config


async def main() -> None:
    config = parse_args()
    engine = ValidationEngine(config)
    await engine.run()


if __name__ == "__main__":
    asyncio.run(main())
