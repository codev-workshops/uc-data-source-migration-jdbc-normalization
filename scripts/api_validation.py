#!/usr/bin/env python3
"""
REST API Validation Script: Comprehensive end-to-end testing of the
loan-service REST API against the underlying DynamoDB data.

Validation stages:
  1. Health & connectivity    — verify the API is reachable
  2. List endpoints           — GET /api/loans, GET /api/borrowers
  3. Detail endpoints         — GET /api/loans/{id}, GET /api/borrowers/{id}
  4. Payments endpoint        — GET /api/loans/{id}/payments
  5. Schema validation        — every response has the expected fields & types
  6. Cross-validation         — API responses match DynamoDB source data
  7. Error handling           — 404/500 for invalid IDs
  8. Data consistency         — borrower names in loans match borrower records,
                                payment totals are consistent, etc.

Output:
  - api_validation_report.json  (structured JSON)
  - api_validation.log          (detailed log)

Usage:
  pip install requests boto3
  python api_validation.py                                  # default localhost:8080
  python api_validation.py --base-url http://localhost:8080  # explicit
  python api_validation.py --report-file report.json        # custom report path
  python api_validation.py --skip-dynamo                    # skip DynamoDB cross-check
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from decimal import Decimal
from typing import Any

import requests

# =============================================================================
# CONFIGURATION
# =============================================================================

@dataclass
class ApiConfig:
    base_url: str = os.getenv("API_BASE_URL", "http://localhost:8080")
    request_timeout: int = int(os.getenv("API_TIMEOUT", "30"))


@dataclass
class DynamoConfig:
    region: str = os.getenv("DYNAMODB_REGION", "us-east-1")
    endpoint_url: str | None = os.getenv("DYNAMODB_ENDPOINT", None)
    aws_access_key_id: str | None = os.getenv("AWS_ACCESS_KEY_ID", None)
    aws_secret_access_key: str | None = os.getenv("AWS_SECRET_ACCESS_KEY", None)


# =============================================================================
# LOGGING
# =============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler("api_validation.log", mode="a", encoding="utf-8"),
    ],
)
logger = logging.getLogger("api_validation")


# =============================================================================
# TEST RESULT TRACKING
# =============================================================================

@dataclass
class TestResult:
    test_name: str
    category: str
    status: str  # PASS, FAIL, SKIP, ERROR
    message: str = ""
    details: dict[str, Any] = field(default_factory=dict)
    duration_ms: float = 0.0


class TestRunner:
    """Collects test results and builds a structured report."""

    def __init__(self) -> None:
        self.results: list[TestResult] = []
        self._start_time: float = time.time()

    def record(self, result: TestResult) -> None:
        self.results.append(result)
        icon = {"PASS": "+", "FAIL": "X", "SKIP": "-", "ERROR": "!"}
        logger.info("[%s] %s — %s%s",
                     icon.get(result.status, "?"),
                     result.test_name,
                     result.status,
                     f": {result.message}" if result.message else "")

    def summary(self) -> dict[str, Any]:
        total = len(self.results)
        passed = sum(1 for r in self.results if r.status == "PASS")
        failed = sum(1 for r in self.results if r.status == "FAIL")
        errors = sum(1 for r in self.results if r.status == "ERROR")
        skipped = sum(1 for r in self.results if r.status == "SKIP")
        elapsed = time.time() - self._start_time
        return {
            "total_tests": total,
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "skipped": skipped,
            "pass_rate": f"{(passed / total * 100):.1f}%" if total else "N/A",
            "overall_result": "PASS" if failed == 0 and errors == 0 else "FAIL",
            "elapsed_seconds": round(elapsed, 2),
        }

    def build_report(self) -> dict[str, Any]:
        return {
            "validation_timestamp": datetime.now(tz=timezone.utc).isoformat(),
            "summary": self.summary(),
            "tests": [asdict(r) for r in self.results],
        }


# =============================================================================
# SCHEMA DEFINITIONS — expected fields and types per DTO
# =============================================================================

LOAN_SUMMARY_FIELDS: dict[str, list[type]] = {
    "loanAccountNumber": [str],
    "borrowerName": [str],
    "productDescription": [str],
    "originalAmount": [int, float],
    "currentBalance": [int, float],
    "interestRate": [int, float],
    "monthlyPayment": [int, float],
    "status": [str],
    "originationDate": [str],
    "propertyAddress": [str],
    "propertyType": [str],
}

BORROWER_FIELDS: dict[str, list[type]] = {
    "id": [str],
    "fullName": [str],
    "email": [str],
    "phone": [str],
    "city": [str],
    "state": [str],
    "creditScore": [int],
    "employmentStatus": [str],
}

BORROWER_DETAIL_FIELDS: dict[str, list[type]] = {
    **BORROWER_FIELDS,
    "loans": [list],
}

PAYMENT_FIELDS: dict[str, list[type]] = {
    "paymentId": [str],
    "loanAccountNumber": [str],
    "paymentDate": [str],
    "totalAmount": [int, float],
    "principalAmount": [int, float],
    "interestAmount": [int, float],
    "escrowAmount": [int, float],
    "lateFee": [int, float],
    "type": [str],
    "status": [str],
}


# =============================================================================
# HELPER FUNCTIONS
# =============================================================================

def _get(url: str, timeout: int) -> requests.Response:
    """Perform a GET request, returning the Response object."""
    return requests.get(url, timeout=timeout)


def validate_schema(record: dict[str, Any], expected_fields: dict[str, list[type]],
                     record_label: str) -> list[str]:
    """Validate that a record has all expected fields with correct types.
    Returns list of error messages (empty = all good).
    """
    errors: list[str] = []
    for field_name, allowed_types in expected_fields.items():
        if field_name not in record:
            errors.append(f"[{record_label}] Missing field: {field_name}")
            continue
        value = record[field_name]
        if value is None:
            continue  # null values are acceptable
        if not any(isinstance(value, t) for t in allowed_types):
            errors.append(
                f"[{record_label}] Field '{field_name}': expected {allowed_types}, "
                f"got {type(value).__name__} = {value!r}"
            )
    return errors


def approx_equal(a: float | int | None, b: float | int | None, tol: float = 0.01) -> bool:
    """Compare two numeric values with tolerance."""
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return abs(float(a) - float(b)) < tol


# =============================================================================
# DYNAMO CROSS-VALIDATION HELPERS
# =============================================================================

def get_dynamo_client(config: DynamoConfig):
    """Create a boto3 DynamoDB client."""
    import boto3
    kwargs: dict[str, Any] = {"region_name": config.region}
    if config.endpoint_url:
        kwargs["endpoint_url"] = config.endpoint_url
    if config.aws_access_key_id:
        kwargs["aws_access_key_id"] = config.aws_access_key_id
    if config.aws_secret_access_key:
        kwargs["aws_secret_access_key"] = config.aws_secret_access_key
    return boto3.resource("dynamodb", **kwargs)


def scan_all(table) -> list[dict]:
    """Scan an entire DynamoDB table (small dataset only)."""
    items: list[dict] = []
    response = table.scan()
    items.extend(response.get("Items", []))
    while "LastEvaluatedKey" in response:
        response = table.scan(ExclusiveStartKey=response["LastEvaluatedKey"])
        items.extend(response.get("Items", []))
    return items


def decimal_to_float(val: Any) -> Any:
    """Recursively convert Decimal to float for comparison."""
    if isinstance(val, Decimal):
        return float(val)
    if isinstance(val, dict):
        return {k: decimal_to_float(v) for k, v in val.items()}
    if isinstance(val, list):
        return [decimal_to_float(i) for i in val]
    return val


# =============================================================================
# TEST SUITE
# =============================================================================

class ApiValidationSuite:
    """Complete REST API validation test suite."""

    def __init__(self, api_config: ApiConfig, dynamo_config: DynamoConfig | None,
                 runner: TestRunner) -> None:
        self.api = api_config
        self.dynamo_config = dynamo_config
        self.runner = runner
        # Cached API responses for cross-test checks
        self._loans: list[dict] = []
        self._borrowers: list[dict] = []
        self._loan_ids: list[str] = []
        self._borrower_ids: list[str] = []

    # -------------------------------------------------------------------------
    # 1. HEALTH & CONNECTIVITY
    # -------------------------------------------------------------------------

    def test_api_reachable(self) -> bool:
        """Test that the API base URL is reachable."""
        t0 = time.time()
        test_name = "API Reachable"
        try:
            resp = _get(f"{self.api.base_url}/api/loans", self.api.request_timeout)
            dur = (time.time() - t0) * 1000
            if resp.status_code == 200:
                self.runner.record(TestResult(
                    test_name=test_name, category="connectivity",
                    status="PASS", message=f"HTTP 200 in {dur:.0f}ms",
                    duration_ms=dur))
                return True
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="connectivity",
                    status="FAIL",
                    message=f"HTTP {resp.status_code}",
                    duration_ms=dur))
                return False
        except requests.ConnectionError as e:
            dur = (time.time() - t0) * 1000
            self.runner.record(TestResult(
                test_name=test_name, category="connectivity",
                status="ERROR", message=f"Connection failed: {e}",
                duration_ms=dur))
            return False

    def test_response_time(self) -> None:
        """Check that responses return within acceptable time."""
        endpoints = [
            ("/api/loans", "GET /api/loans"),
            ("/api/borrowers", "GET /api/borrowers"),
        ]
        for path, label in endpoints:
            t0 = time.time()
            test_name = f"Response Time — {label}"
            try:
                resp = _get(f"{self.api.base_url}{path}", self.api.request_timeout)
                dur = (time.time() - t0) * 1000
                if dur < 5000:
                    self.runner.record(TestResult(
                        test_name=test_name, category="performance",
                        status="PASS", message=f"{dur:.0f}ms (< 5000ms threshold)",
                        duration_ms=dur))
                else:
                    self.runner.record(TestResult(
                        test_name=test_name, category="performance",
                        status="FAIL", message=f"{dur:.0f}ms exceeds 5000ms threshold",
                        duration_ms=dur))
            except Exception as e:
                self.runner.record(TestResult(
                    test_name=test_name, category="performance",
                    status="ERROR", message=str(e)))

    # -------------------------------------------------------------------------
    # 2. LIST ENDPOINTS
    # -------------------------------------------------------------------------

    def test_get_all_loans(self) -> None:
        """GET /api/loans — validate response is non-empty list with correct schema."""
        t0 = time.time()
        test_name = "GET /api/loans — List All"
        try:
            resp = _get(f"{self.api.base_url}/api/loans", self.api.request_timeout)
            dur = (time.time() - t0) * 1000
            if resp.status_code != 200:
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message=f"HTTP {resp.status_code}",
                    duration_ms=dur))
                return

            data = resp.json()
            if not isinstance(data, list):
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message=f"Expected list, got {type(data).__name__}",
                    duration_ms=dur))
                return

            if len(data) == 0:
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message="Empty list returned",
                    duration_ms=dur))
                return

            self._loans = data
            self._loan_ids = [loan["loanAccountNumber"] for loan in data if "loanAccountNumber" in loan]

            self.runner.record(TestResult(
                test_name=test_name, category="list_endpoints",
                status="PASS", message=f"Returned {len(data)} loans",
                details={"count": len(data), "loan_ids": self._loan_ids},
                duration_ms=dur))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="list_endpoints",
                status="ERROR", message=str(e)))

    def test_get_all_borrowers(self) -> None:
        """GET /api/borrowers — validate response is non-empty list with correct schema."""
        t0 = time.time()
        test_name = "GET /api/borrowers — List All"
        try:
            resp = _get(f"{self.api.base_url}/api/borrowers", self.api.request_timeout)
            dur = (time.time() - t0) * 1000
            if resp.status_code != 200:
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message=f"HTTP {resp.status_code}",
                    duration_ms=dur))
                return

            data = resp.json()
            if not isinstance(data, list):
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message=f"Expected list, got {type(data).__name__}",
                    duration_ms=dur))
                return

            if len(data) == 0:
                self.runner.record(TestResult(
                    test_name=test_name, category="list_endpoints",
                    status="FAIL", message="Empty list returned",
                    duration_ms=dur))
                return

            self._borrowers = data
            self._borrower_ids = [b["id"] for b in data if "id" in b]

            self.runner.record(TestResult(
                test_name=test_name, category="list_endpoints",
                status="PASS", message=f"Returned {len(data)} borrowers",
                details={"count": len(data), "borrower_ids": self._borrower_ids},
                duration_ms=dur))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="list_endpoints",
                status="ERROR", message=str(e)))

    # -------------------------------------------------------------------------
    # 3. SCHEMA VALIDATION
    # -------------------------------------------------------------------------

    def test_loan_schema(self) -> None:
        """Validate every loan record has expected fields and types."""
        test_name = "Schema Validation — LoanSummaryDto"
        if not self._loans:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="SKIP", message="No loans loaded"))
            return

        all_errors: list[str] = []
        for i, loan in enumerate(self._loans):
            errs = validate_schema(loan, LOAN_SUMMARY_FIELDS,
                                    f"loan[{i}]:{loan.get('loanAccountNumber', '?')}")
            all_errors.extend(errs)

        if all_errors:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="FAIL",
                message=f"{len(all_errors)} schema errors across {len(self._loans)} loans",
                details={"errors": all_errors[:20]}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="PASS",
                message=f"All {len(self._loans)} loans have valid schema"))

    def test_borrower_schema(self) -> None:
        """Validate every borrower record has expected fields and types."""
        test_name = "Schema Validation — BorrowerDto"
        if not self._borrowers:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="SKIP", message="No borrowers loaded"))
            return

        all_errors: list[str] = []
        for i, b in enumerate(self._borrowers):
            errs = validate_schema(b, BORROWER_FIELDS,
                                    f"borrower[{i}]:{b.get('id', '?')}")
            all_errors.extend(errs)

        if all_errors:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="FAIL",
                message=f"{len(all_errors)} schema errors across {len(self._borrowers)} borrowers",
                details={"errors": all_errors[:20]}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="schema",
                status="PASS",
                message=f"All {len(self._borrowers)} borrowers have valid schema"))

    # -------------------------------------------------------------------------
    # 4. DETAIL ENDPOINTS
    # -------------------------------------------------------------------------

    def test_get_loan_by_id(self) -> None:
        """GET /api/loans/{id} for each known loan — validate response matches list data."""
        if not self._loan_ids:
            self.runner.record(TestResult(
                test_name="GET /api/loans/{id} — Detail", category="detail_endpoints",
                status="SKIP", message="No loan IDs to test"))
            return

        for loan_id in self._loan_ids:
            t0 = time.time()
            test_name = f"GET /api/loans/{loan_id}"
            try:
                resp = _get(f"{self.api.base_url}/api/loans/{loan_id}",
                           self.api.request_timeout)
                dur = (time.time() - t0) * 1000

                if resp.status_code != 200:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message=f"HTTP {resp.status_code}",
                        duration_ms=dur))
                    continue

                data = resp.json()
                schema_errors = validate_schema(data, LOAN_SUMMARY_FIELDS, loan_id)
                if schema_errors:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message="Schema errors",
                        details={"errors": schema_errors}, duration_ms=dur))
                    continue

                # Verify the returned loan ID matches
                if data.get("loanAccountNumber") != loan_id:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL",
                        message=f"ID mismatch: requested {loan_id}, "
                                f"got {data.get('loanAccountNumber')}",
                        duration_ms=dur))
                    continue

                # Cross-check with list data
                list_loan = next((l for l in self._loans
                                  if l.get("loanAccountNumber") == loan_id), None)
                mismatches: list[str] = []
                if list_loan:
                    for key in LOAN_SUMMARY_FIELDS:
                        list_val = list_loan.get(key)
                        detail_val = data.get(key)
                        if isinstance(list_val, (int, float)) and isinstance(detail_val, (int, float)):
                            if not approx_equal(list_val, detail_val):
                                mismatches.append(
                                    f"{key}: list={list_val}, detail={detail_val}")
                        elif list_val != detail_val:
                            mismatches.append(
                                f"{key}: list={list_val!r}, detail={detail_val!r}")

                if mismatches:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL",
                        message=f"{len(mismatches)} mismatches vs list endpoint",
                        details={"mismatches": mismatches}, duration_ms=dur))
                else:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="PASS", message="Matches list data",
                        duration_ms=dur))

            except Exception as e:
                self.runner.record(TestResult(
                    test_name=test_name, category="detail_endpoints",
                    status="ERROR", message=str(e)))

    def test_get_borrower_by_id(self) -> None:
        """GET /api/borrowers/{id} for each known borrower — validate schema + loans attached."""
        if not self._borrower_ids:
            self.runner.record(TestResult(
                test_name="GET /api/borrowers/{id} — Detail", category="detail_endpoints",
                status="SKIP", message="No borrower IDs to test"))
            return

        for bid in self._borrower_ids:
            t0 = time.time()
            test_name = f"GET /api/borrowers/{bid}"
            try:
                resp = _get(f"{self.api.base_url}/api/borrowers/{bid}",
                           self.api.request_timeout)
                dur = (time.time() - t0) * 1000

                if resp.status_code != 200:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message=f"HTTP {resp.status_code}",
                        duration_ms=dur))
                    continue

                data = resp.json()
                schema_errors = validate_schema(data, BORROWER_DETAIL_FIELDS, bid)
                if schema_errors:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message="Schema errors",
                        details={"errors": schema_errors}, duration_ms=dur))
                    continue

                # Verify ID matches
                if data.get("id") != bid:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL",
                        message=f"ID mismatch: requested {bid}, got {data.get('id')}",
                        duration_ms=dur))
                    continue

                # Check loans field is a list
                loans = data.get("loans", [])
                if not isinstance(loans, list):
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message=f"loans field is not a list",
                        duration_ms=dur))
                    continue

                # Validate each nested loan schema
                nested_errors: list[str] = []
                for i, loan in enumerate(loans):
                    errs = validate_schema(loan, LOAN_SUMMARY_FIELDS,
                                            f"borrower.loans[{i}]")
                    nested_errors.extend(errs)

                # Cross-check borrower fields with list data
                list_borrower = next((b for b in self._borrowers
                                      if b.get("id") == bid), None)
                field_mismatches: list[str] = []
                if list_borrower:
                    for key in BORROWER_FIELDS:
                        list_val = list_borrower.get(key)
                        detail_val = data.get(key)
                        if list_val != detail_val:
                            field_mismatches.append(
                                f"{key}: list={list_val!r}, detail={detail_val!r}")

                issues = nested_errors + field_mismatches
                if issues:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="FAIL", message=f"{len(issues)} issues",
                        details={"issues": issues[:20]}, duration_ms=dur))
                else:
                    self.runner.record(TestResult(
                        test_name=test_name, category="detail_endpoints",
                        status="PASS",
                        message=f"Valid with {len(loans)} attached loans",
                        duration_ms=dur))

            except Exception as e:
                self.runner.record(TestResult(
                    test_name=test_name, category="detail_endpoints",
                    status="ERROR", message=str(e)))

    # -------------------------------------------------------------------------
    # 5. PAYMENTS ENDPOINT
    # -------------------------------------------------------------------------

    def test_get_payments(self) -> None:
        """GET /api/loans/{id}/payments for each loan — validate schema and consistency."""
        if not self._loan_ids:
            self.runner.record(TestResult(
                test_name="GET /api/loans/{id}/payments", category="payments",
                status="SKIP", message="No loan IDs to test"))
            return

        total_payments = 0
        for loan_id in self._loan_ids:
            t0 = time.time()
            test_name = f"GET /api/loans/{loan_id}/payments"
            try:
                resp = _get(
                    f"{self.api.base_url}/api/loans/{loan_id}/payments",
                    self.api.request_timeout)
                dur = (time.time() - t0) * 1000

                if resp.status_code != 200:
                    self.runner.record(TestResult(
                        test_name=test_name, category="payments",
                        status="FAIL", message=f"HTTP {resp.status_code}",
                        duration_ms=dur))
                    continue

                data = resp.json()
                if not isinstance(data, list):
                    self.runner.record(TestResult(
                        test_name=test_name, category="payments",
                        status="FAIL",
                        message=f"Expected list, got {type(data).__name__}",
                        duration_ms=dur))
                    continue

                # Schema validation for each payment
                all_errors: list[str] = []
                for i, pmt in enumerate(data):
                    errs = validate_schema(pmt, PAYMENT_FIELDS,
                                            f"payment[{i}]:{pmt.get('paymentId', '?')}")
                    all_errors.extend(errs)

                    # Verify loanAccountNumber matches the requested loan
                    if pmt.get("loanAccountNumber") != loan_id:
                        all_errors.append(
                            f"payment[{i}]: loanAccountNumber={pmt.get('loanAccountNumber')!r} "
                            f"!= requested {loan_id}")

                    # Validate payment amount consistency
                    # totalAmount should >= principalAmount + interestAmount
                    total_amt = pmt.get("totalAmount", 0) or 0
                    principal = pmt.get("principalAmount", 0) or 0
                    interest = pmt.get("interestAmount", 0) or 0
                    escrow = pmt.get("escrowAmount", 0) or 0
                    late = pmt.get("lateFee", 0) or 0
                    component_sum = principal + interest + escrow + late
                    if total_amt > 0 and component_sum > 0:
                        if not approx_equal(total_amt, component_sum, tol=0.02):
                            all_errors.append(
                                f"payment[{i}]: totalAmount={total_amt} != "
                                f"principal({principal})+interest({interest})"
                                f"+escrow({escrow})+lateFee({late}) = {component_sum}")

                total_payments += len(data)

                if all_errors:
                    self.runner.record(TestResult(
                        test_name=test_name, category="payments",
                        status="FAIL",
                        message=f"{len(all_errors)} errors in {len(data)} payments",
                        details={"errors": all_errors[:20]}, duration_ms=dur))
                else:
                    self.runner.record(TestResult(
                        test_name=test_name, category="payments",
                        status="PASS",
                        message=f"{len(data)} payments validated",
                        duration_ms=dur))

            except Exception as e:
                self.runner.record(TestResult(
                    test_name=test_name, category="payments",
                    status="ERROR", message=str(e)))

        logger.info("Total payments validated across all loans: %d", total_payments)

    # -------------------------------------------------------------------------
    # 6. ERROR HANDLING
    # -------------------------------------------------------------------------

    def test_invalid_loan_id(self) -> None:
        """GET /api/loans/{invalid_id} should return an error (not 200)."""
        t0 = time.time()
        test_name = "Error Handling — Invalid Loan ID"
        invalid_id = "NONEXISTENT-LOAN-99999"
        try:
            resp = _get(f"{self.api.base_url}/api/loans/{invalid_id}",
                       self.api.request_timeout)
            dur = (time.time() - t0) * 1000

            if resp.status_code in (404, 500):
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="PASS",
                    message=f"HTTP {resp.status_code} for invalid loan ID",
                    duration_ms=dur))
            elif resp.status_code == 200:
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="FAIL",
                    message="HTTP 200 returned for invalid loan ID — should be 404/500",
                    duration_ms=dur))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="PASS",
                    message=f"HTTP {resp.status_code} for invalid loan ID",
                    duration_ms=dur))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="error_handling",
                status="ERROR", message=str(e)))

    def test_invalid_borrower_id(self) -> None:
        """GET /api/borrowers/{invalid_id} should return an error (not 200)."""
        t0 = time.time()
        test_name = "Error Handling — Invalid Borrower ID"
        invalid_id = "NONEXISTENT-BORROWER-99999"
        try:
            resp = _get(f"{self.api.base_url}/api/borrowers/{invalid_id}",
                       self.api.request_timeout)
            dur = (time.time() - t0) * 1000

            if resp.status_code in (404, 500):
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="PASS",
                    message=f"HTTP {resp.status_code} for invalid borrower ID",
                    duration_ms=dur))
            elif resp.status_code == 200:
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="FAIL",
                    message="HTTP 200 returned for invalid borrower ID — should be 404/500",
                    duration_ms=dur))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="PASS",
                    message=f"HTTP {resp.status_code} for invalid borrower ID",
                    duration_ms=dur))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="error_handling",
                status="ERROR", message=str(e)))

    def test_payments_empty_for_invalid_loan(self) -> None:
        """GET /api/loans/{invalid}/payments should return empty list or error."""
        t0 = time.time()
        test_name = "Error Handling — Payments for Invalid Loan"
        invalid_id = "NONEXISTENT-LOAN-99999"
        try:
            resp = _get(
                f"{self.api.base_url}/api/loans/{invalid_id}/payments",
                self.api.request_timeout)
            dur = (time.time() - t0) * 1000

            if resp.status_code == 200:
                data = resp.json()
                if isinstance(data, list) and len(data) == 0:
                    self.runner.record(TestResult(
                        test_name=test_name, category="error_handling",
                        status="PASS",
                        message="Empty list returned for invalid loan",
                        duration_ms=dur))
                else:
                    self.runner.record(TestResult(
                        test_name=test_name, category="error_handling",
                        status="FAIL",
                        message=f"Non-empty response for invalid loan: {len(data)} records",
                        duration_ms=dur))
            elif resp.status_code in (404, 500):
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="PASS",
                    message=f"HTTP {resp.status_code} for invalid loan payments",
                    duration_ms=dur))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="error_handling",
                    status="FAIL",
                    message=f"Unexpected HTTP {resp.status_code}",
                    duration_ms=dur))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="error_handling",
                status="ERROR", message=str(e)))

    # -------------------------------------------------------------------------
    # 7. DATA CONSISTENCY (API-internal)
    # -------------------------------------------------------------------------

    def test_borrower_names_in_loans(self) -> None:
        """Verify borrowerName in loans matches data from borrowers endpoint."""
        test_name = "Consistency — Borrower Names in Loans"
        if not self._loans or not self._borrowers:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="SKIP", message="Missing loans or borrowers data"))
            return

        # Build name lookup from borrower list
        borrower_names: set[str] = set()
        for b in self._borrowers:
            full_name = b.get("fullName", "")
            if full_name:
                # Extract first and last name (API might include middle initial)
                parts = full_name.split()
                if len(parts) >= 2:
                    # Build "First Last" variant (without middle)
                    borrower_names.add(f"{parts[0]} {parts[-1]}")
                borrower_names.add(full_name)

        mismatches: list[str] = []
        for loan in self._loans:
            bname = loan.get("borrowerName", "")
            if bname and bname != "Unknown":
                # Check if loan's borrowerName matches any known borrower
                if bname not in borrower_names:
                    mismatches.append(
                        f"Loan {loan.get('loanAccountNumber')}: "
                        f"borrowerName='{bname}' not found in borrowers list")

        if mismatches:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="FAIL",
                message=f"{len(mismatches)} borrower name mismatches",
                details={"mismatches": mismatches[:20]}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="PASS",
                message=f"All {len(self._loans)} loan borrower names match"))

    def test_loan_status_values(self) -> None:
        """Verify all loan statuses are valid Title Case values."""
        test_name = "Consistency — Loan Status Values"
        valid_statuses = {"Active", "Closed", "Default", "Forbearance"}
        if not self._loans:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="SKIP", message="No loans loaded"))
            return

        invalid: list[str] = []
        for loan in self._loans:
            status = loan.get("status", "")
            if status not in valid_statuses:
                invalid.append(
                    f"Loan {loan.get('loanAccountNumber')}: status='{status}'")

        if invalid:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="FAIL",
                message=f"{len(invalid)} loans with unexpected status",
                details={"invalid": invalid}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="PASS",
                message=f"All {len(self._loans)} loans have valid status values"))

    def test_numeric_fields_non_negative(self) -> None:
        """Verify numeric fields (amounts, rates) are non-negative."""
        test_name = "Consistency — Non-Negative Amounts"
        if not self._loans:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="SKIP", message="No loans loaded"))
            return

        negative_fields: list[str] = []
        numeric_keys = ["originalAmount", "currentBalance", "interestRate",
                        "monthlyPayment"]
        for loan in self._loans:
            for key in numeric_keys:
                val = loan.get(key)
                if val is not None and val < 0:
                    negative_fields.append(
                        f"Loan {loan.get('loanAccountNumber')}: {key}={val}")

        if negative_fields:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="FAIL",
                message=f"{len(negative_fields)} negative values found",
                details={"negative_fields": negative_fields}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="PASS",
                message="All numeric fields are non-negative"))

    def test_credit_scores_in_range(self) -> None:
        """Verify credit scores are within valid range (300-850)."""
        test_name = "Consistency — Credit Score Range"
        if not self._borrowers:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="SKIP", message="No borrowers loaded"))
            return

        out_of_range: list[str] = []
        for b in self._borrowers:
            score = b.get("creditScore")
            if score is not None and (score < 300 or score > 850):
                out_of_range.append(f"Borrower {b.get('id')}: creditScore={score}")

        if out_of_range:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="FAIL",
                message=f"{len(out_of_range)} borrowers with invalid credit scores",
                details={"out_of_range": out_of_range}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="PASS",
                message=f"All {len(self._borrowers)} credit scores in range [300, 850]"))

    def test_unique_ids(self) -> None:
        """Verify no duplicate IDs in list responses."""
        test_name = "Consistency — Unique IDs"
        issues: list[str] = []

        if self._loan_ids:
            if len(self._loan_ids) != len(set(self._loan_ids)):
                dupes = [x for x in self._loan_ids if self._loan_ids.count(x) > 1]
                issues.append(f"Duplicate loan IDs: {set(dupes)}")

        if self._borrower_ids:
            if len(self._borrower_ids) != len(set(self._borrower_ids)):
                dupes = [x for x in self._borrower_ids if self._borrower_ids.count(x) > 1]
                issues.append(f"Duplicate borrower IDs: {set(dupes)}")

        if issues:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="FAIL",
                message="; ".join(issues),
                details={"issues": issues}))
        else:
            self.runner.record(TestResult(
                test_name=test_name, category="consistency",
                status="PASS",
                message=f"All IDs unique ({len(self._loan_ids)} loans, "
                        f"{len(self._borrower_ids)} borrowers)"))

    # -------------------------------------------------------------------------
    # 8. DYNAMODB CROSS-VALIDATION
    # -------------------------------------------------------------------------

    def test_cross_validate_loans_with_dynamo(self) -> None:
        """Compare API loan data against DynamoDB source records."""
        test_name = "Cross-Validation — Loans vs DynamoDB"
        if not self.dynamo_config:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="DynamoDB cross-check disabled"))
            return
        if not self._loans:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="No loans loaded from API"))
            return

        try:
            dynamo = get_dynamo_client(self.dynamo_config)
            loan_table = dynamo.Table("LoanAccounts")
            product_table = dynamo.Table("LoanProducts")
            borrower_table = dynamo.Table("Borrowers")

            db_loans = {item["account_number"]: decimal_to_float(item)
                        for item in scan_all(loan_table)}
            db_products = {item["product_code"]: decimal_to_float(item)
                           for item in scan_all(product_table)}
            db_borrowers = {item["borrower_id"]: decimal_to_float(item)
                            for item in scan_all(borrower_table)}

            mismatches: list[str] = []
            matched = 0
            for loan in self._loans:
                loan_id = loan.get("loanAccountNumber")
                db_loan = db_loans.get(loan_id)
                if not db_loan:
                    mismatches.append(f"Loan {loan_id}: not found in DynamoDB")
                    continue

                # Check key fields
                checks = [
                    ("originalAmount", loan.get("originalAmount"),
                     db_loan.get("original_amount")),
                    ("currentBalance", loan.get("currentBalance"),
                     db_loan.get("current_balance")),
                    ("interestRate", loan.get("interestRate"),
                     db_loan.get("interest_rate")),
                    ("monthlyPayment", loan.get("monthlyPayment"),
                     db_loan.get("monthly_payment")),
                    ("originationDate", loan.get("originationDate"),
                     db_loan.get("origination_date")),
                ]
                loan_ok = True
                for field_name, api_val, db_val in checks:
                    if isinstance(api_val, (int, float)) and isinstance(db_val, (int, float)):
                        if not approx_equal(api_val, db_val):
                            mismatches.append(
                                f"Loan {loan_id}.{field_name}: "
                                f"api={api_val}, dynamo={db_val}")
                            loan_ok = False
                    elif api_val != db_val:
                        mismatches.append(
                            f"Loan {loan_id}.{field_name}: "
                            f"api={api_val!r}, dynamo={db_val!r}")
                        loan_ok = False

                # Verify status (API returns Title Case, DB stores UPPERCASE)
                api_status = loan.get("status", "").upper()
                db_status = (db_loan.get("status") or "").upper()
                if api_status != db_status:
                    mismatches.append(
                        f"Loan {loan_id}.status: "
                        f"api='{loan.get('status')}', dynamo='{db_loan.get('status')}'")
                    loan_ok = False

                # Verify borrowerName
                borrower_id = db_loan.get("borrower_id")
                db_borrower = db_borrowers.get(borrower_id) if borrower_id else None
                if db_borrower:
                    expected_name = (f"{db_borrower.get('first_name', '')} "
                                     f"{db_borrower.get('last_name', '')}")
                    if loan.get("borrowerName") != expected_name:
                        mismatches.append(
                            f"Loan {loan_id}.borrowerName: "
                            f"api='{loan.get('borrowerName')}', "
                            f"expected='{expected_name}'")
                        loan_ok = False

                # Verify productDescription
                product_code = db_loan.get("product_code")
                db_product = db_products.get(product_code) if product_code else None
                if db_product:
                    expected_desc = db_product.get("name", product_code)
                    if loan.get("productDescription") != expected_desc:
                        mismatches.append(
                            f"Loan {loan_id}.productDescription: "
                            f"api='{loan.get('productDescription')}', "
                            f"expected='{expected_desc}'")
                        loan_ok = False

                if loan_ok:
                    matched += 1

            if mismatches:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="FAIL",
                    message=f"{len(mismatches)} mismatches "
                            f"({matched}/{len(self._loans)} loans matched)",
                    details={"mismatches": mismatches[:30]}))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="PASS",
                    message=f"All {matched}/{len(self._loans)} loans match DynamoDB"))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="ERROR", message=str(e)))

    def test_cross_validate_borrowers_with_dynamo(self) -> None:
        """Compare API borrower data against DynamoDB source records."""
        test_name = "Cross-Validation — Borrowers vs DynamoDB"
        if not self.dynamo_config:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="DynamoDB cross-check disabled"))
            return
        if not self._borrowers:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="No borrowers loaded from API"))
            return

        try:
            dynamo = get_dynamo_client(self.dynamo_config)
            table = dynamo.Table("Borrowers")
            db_borrowers = {item["borrower_id"]: decimal_to_float(item)
                            for item in scan_all(table)}

            mismatches: list[str] = []
            matched = 0
            for b in self._borrowers:
                bid = b.get("id")
                db_b = db_borrowers.get(bid)
                if not db_b:
                    mismatches.append(f"Borrower {bid}: not found in DynamoDB")
                    continue

                borrower_ok = True

                # Check email, phone, city, state, creditScore
                field_map = [
                    ("email", "email"),
                    ("phone", "phone"),
                    ("city", "city"),
                    ("state", "state"),
                    ("creditScore", "credit_score"),
                    ("employmentStatus", "employment_status"),
                ]
                for api_field, db_field in field_map:
                    api_val = b.get(api_field)
                    db_val = db_b.get(db_field)
                    if isinstance(api_val, (int, float)) and isinstance(db_val, (int, float)):
                        if not approx_equal(api_val, db_val):
                            mismatches.append(
                                f"Borrower {bid}.{api_field}: "
                                f"api={api_val}, dynamo={db_val}")
                            borrower_ok = False
                    elif api_val != db_val:
                        mismatches.append(
                            f"Borrower {bid}.{api_field}: "
                            f"api={api_val!r}, dynamo={db_val!r}")
                        borrower_ok = False

                # Verify fullName construction
                first = db_b.get("first_name", "")
                last = db_b.get("last_name", "")
                middle = db_b.get("middle_initial")
                if middle:
                    expected_name = f"{first} {middle}. {last}"
                else:
                    expected_name = f"{first} {last}"
                if b.get("fullName") != expected_name:
                    mismatches.append(
                        f"Borrower {bid}.fullName: "
                        f"api='{b.get('fullName')}', expected='{expected_name}'")
                    borrower_ok = False

                if borrower_ok:
                    matched += 1

            if mismatches:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="FAIL",
                    message=f"{len(mismatches)} mismatches "
                            f"({matched}/{len(self._borrowers)} matched)",
                    details={"mismatches": mismatches[:30]}))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="PASS",
                    message=f"All {matched}/{len(self._borrowers)} borrowers match DynamoDB"))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="ERROR", message=str(e)))

    def test_cross_validate_payments_with_dynamo(self) -> None:
        """Compare API payment data against DynamoDB source records."""
        test_name = "Cross-Validation — Payments vs DynamoDB"
        if not self.dynamo_config:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="DynamoDB cross-check disabled"))
            return
        if not self._loan_ids:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="No loan IDs to test"))
            return

        try:
            dynamo = get_dynamo_client(self.dynamo_config)
            table = dynamo.Table("Payments")
            db_payments_raw = scan_all(table)
            # Index by payment_id for lookup
            db_payments = {}
            for item in db_payments_raw:
                pid = item.get("payment_id")
                if pid:
                    db_payments[pid] = decimal_to_float(item)

            total_checked = 0
            mismatches: list[str] = []

            for loan_id in self._loan_ids:
                resp = _get(
                    f"{self.api.base_url}/api/loans/{loan_id}/payments",
                    self.api.request_timeout)
                if resp.status_code != 200:
                    continue
                api_payments = resp.json()
                if not isinstance(api_payments, list):
                    continue

                for pmt in api_payments:
                    pid = pmt.get("paymentId")
                    db_pmt = db_payments.get(pid)
                    if not db_pmt:
                        mismatches.append(f"Payment {pid}: not found in DynamoDB")
                        total_checked += 1
                        continue

                    pmt_ok = True
                    # Check amounts
                    amount_checks = [
                        ("totalAmount", "total_amount"),
                        ("principalAmount", "principal_amount"),
                        ("interestAmount", "interest_amount"),
                        ("escrowAmount", "escrow_amount"),
                        ("lateFee", "late_fee"),
                    ]
                    for api_field, db_field in amount_checks:
                        api_val = pmt.get(api_field)
                        db_val = db_pmt.get(db_field)
                        if isinstance(api_val, (int, float)) and isinstance(db_val, (int, float)):
                            if not approx_equal(api_val, db_val):
                                mismatches.append(
                                    f"Payment {pid}.{api_field}: "
                                    f"api={api_val}, dynamo={db_val}")
                                pmt_ok = False
                        elif api_val is not None and db_val is not None:
                            if api_val != db_val:
                                mismatches.append(
                                    f"Payment {pid}.{api_field}: "
                                    f"api={api_val!r}, dynamo={db_val!r}")
                                pmt_ok = False

                    # Check date
                    if pmt.get("paymentDate") != db_pmt.get("payment_date"):
                        mismatches.append(
                            f"Payment {pid}.paymentDate: "
                            f"api={pmt.get('paymentDate')!r}, "
                            f"dynamo={db_pmt.get('payment_date')!r}")
                        pmt_ok = False

                    total_checked += 1

            if mismatches:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="FAIL",
                    message=f"{len(mismatches)} mismatches in {total_checked} payments",
                    details={"mismatches": mismatches[:30]}))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="PASS",
                    message=f"All {total_checked} payments match DynamoDB"))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="ERROR", message=str(e)))

    def test_record_counts_match_dynamo(self) -> None:
        """Verify API record counts match DynamoDB table item counts."""
        test_name = "Cross-Validation — Record Counts"
        if not self.dynamo_config:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="SKIP", message="DynamoDB cross-check disabled"))
            return

        try:
            dynamo = get_dynamo_client(self.dynamo_config)
            issues: list[str] = []

            # Loans
            loan_table = dynamo.Table("LoanAccounts")
            db_loan_count = loan_table.scan(Select="COUNT")["Count"]
            api_loan_count = len(self._loans)
            if api_loan_count != db_loan_count:
                issues.append(
                    f"Loans: API={api_loan_count}, DynamoDB={db_loan_count}")

            # Borrowers
            borrower_table = dynamo.Table("Borrowers")
            db_borrower_count = borrower_table.scan(Select="COUNT")["Count"]
            api_borrower_count = len(self._borrowers)
            if api_borrower_count != db_borrower_count:
                issues.append(
                    f"Borrowers: API={api_borrower_count}, DynamoDB={db_borrower_count}")

            if issues:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="FAIL",
                    message="; ".join(issues),
                    details={"issues": issues}))
            else:
                self.runner.record(TestResult(
                    test_name=test_name, category="cross_validation",
                    status="PASS",
                    message=f"Counts match — Loans: {api_loan_count}, "
                            f"Borrowers: {api_borrower_count}"))

        except Exception as e:
            self.runner.record(TestResult(
                test_name=test_name, category="cross_validation",
                status="ERROR", message=str(e)))

    # -------------------------------------------------------------------------
    # RUN ALL
    # -------------------------------------------------------------------------

    def run_all(self) -> dict[str, Any]:
        """Execute the full test suite and return the report."""
        logger.info("=" * 70)
        logger.info("REST API VALIDATION STARTED")
        logger.info("  Base URL: %s", self.api.base_url)
        logger.info("  DynamoDB cross-check: %s",
                     "enabled" if self.dynamo_config else "disabled")
        logger.info("=" * 70)

        # 1. Health & connectivity
        logger.info("-" * 50)
        logger.info("Stage 1: Health & Connectivity")
        logger.info("-" * 50)
        if not self.test_api_reachable():
            logger.error("API is not reachable — aborting remaining tests")
            return self.runner.build_report()

        self.test_response_time()

        # 2. List endpoints
        logger.info("-" * 50)
        logger.info("Stage 2: List Endpoints")
        logger.info("-" * 50)
        self.test_get_all_loans()
        self.test_get_all_borrowers()

        # 3. Schema validation
        logger.info("-" * 50)
        logger.info("Stage 3: Schema Validation")
        logger.info("-" * 50)
        self.test_loan_schema()
        self.test_borrower_schema()

        # 4. Detail endpoints
        logger.info("-" * 50)
        logger.info("Stage 4: Detail Endpoints")
        logger.info("-" * 50)
        self.test_get_loan_by_id()
        self.test_get_borrower_by_id()

        # 5. Payments
        logger.info("-" * 50)
        logger.info("Stage 5: Payments Endpoint")
        logger.info("-" * 50)
        self.test_get_payments()

        # 6. Error handling
        logger.info("-" * 50)
        logger.info("Stage 6: Error Handling")
        logger.info("-" * 50)
        self.test_invalid_loan_id()
        self.test_invalid_borrower_id()
        self.test_payments_empty_for_invalid_loan()

        # 7. Data consistency
        logger.info("-" * 50)
        logger.info("Stage 7: Data Consistency")
        logger.info("-" * 50)
        self.test_borrower_names_in_loans()
        self.test_loan_status_values()
        self.test_numeric_fields_non_negative()
        self.test_credit_scores_in_range()
        self.test_unique_ids()

        # 8. DynamoDB cross-validation
        logger.info("-" * 50)
        logger.info("Stage 8: DynamoDB Cross-Validation")
        logger.info("-" * 50)
        self.test_record_counts_match_dynamo()
        self.test_cross_validate_loans_with_dynamo()
        self.test_cross_validate_borrowers_with_dynamo()
        self.test_cross_validate_payments_with_dynamo()

        # Summary
        report = self.runner.build_report()
        summary = report["summary"]
        logger.info("=" * 70)
        logger.info("VALIDATION COMPLETE — %s", summary["overall_result"])
        logger.info("  Total tests  : %d", summary["total_tests"])
        logger.info("  Passed       : %d", summary["passed"])
        logger.info("  Failed       : %d", summary["failed"])
        logger.info("  Errors       : %d", summary["errors"])
        logger.info("  Skipped      : %d", summary["skipped"])
        logger.info("  Pass rate    : %s", summary["pass_rate"])
        logger.info("  Elapsed      : %.2fs", summary["elapsed_seconds"])
        logger.info("=" * 70)

        return report


# =============================================================================
# CLI
# =============================================================================

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="REST API Validation Script — validates loan-service endpoints")
    parser.add_argument("--base-url", default=os.getenv("API_BASE_URL", "http://localhost:8080"),
                        help="Base URL of the API (default: http://localhost:8080)")
    parser.add_argument("--report-file", default="api_validation_report.json",
                        help="Output report file path (default: api_validation_report.json)")
    parser.add_argument("--skip-dynamo", action="store_true",
                        help="Skip DynamoDB cross-validation")
    parser.add_argument("--timeout", type=int, default=30,
                        help="HTTP request timeout in seconds (default: 30)")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    api_config = ApiConfig(base_url=args.base_url, request_timeout=args.timeout)
    dynamo_config = None if args.skip_dynamo else DynamoConfig()
    runner = TestRunner()
    suite = ApiValidationSuite(api_config, dynamo_config, runner)

    report = suite.run_all()

    # Write report
    with open(args.report_file, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, default=str)
    logger.info("Validation report written to: %s", args.report_file)


if __name__ == "__main__":
    main()
