#!/usr/bin/env bash
# =============================================================================
# API response capture: save responses from each microservice for review
# =============================================================================
# Usage: ./api-compare.sh [output_dir]
#
# This script calls each microservice directly (not through the gateway)
# and saves the JSON responses to files for manual comparison.
# =============================================================================

set -euo pipefail

OUTPUT_DIR="${1:-./api-responses}"
mkdir -p "${OUTPUT_DIR}"

BORROWER_URL="http://localhost:8081"
LOAN_URL="http://localhost:8082"
PAYMENT_URL="http://localhost:8083"
GATEWAY_URL="http://localhost:8080"

echo "=== Capturing API Responses ==="
echo "Output directory: ${OUTPUT_DIR}"
echo

capture() {
    local url="$1"
    local filename="$2"
    local label="$3"
    
    echo -n "Capturing ${label}... "
    if curl -s "${url}" -o "${OUTPUT_DIR}/${filename}" 2>/dev/null; then
        echo "OK ($(wc -c < "${OUTPUT_DIR}/${filename}") bytes)"
    else
        echo "FAILED"
    fi
}

echo "--- Borrower Service (port 8081) ---"
capture "${BORROWER_URL}/api/borrowers" "borrower-all.json" "GET /api/borrowers"
capture "${BORROWER_URL}/api/borrowers/B-10001" "borrower-B-10001.json" "GET /api/borrowers/B-10001"

echo
echo "--- Loan Service (port 8082) ---"
capture "${LOAN_URL}/api/loans" "loan-all.json" "GET /api/loans"
capture "${LOAN_URL}/api/loans/LN-2019-00142" "loan-LN-2019-00142.json" "GET /api/loans/LN-2019-00142"

echo
echo "--- Payment Service (port 8083) ---"
capture "${PAYMENT_URL}/api/payments/loan/LN-2019-00142" "payment-LN-2019-00142.json" "GET /api/payments/loan/LN-2019-00142"

echo
echo "--- API Gateway (port 8080) ---"
capture "${GATEWAY_URL}/api/borrowers" "gateway-borrowers.json" "GET /api/borrowers (via gateway)"
capture "${GATEWAY_URL}/api/loans" "gateway-loans.json" "GET /api/loans (via gateway)"
capture "${GATEWAY_URL}/api/payments/loan/LN-2019-00142" "gateway-payments.json" "GET /api/payments/loan/LN-2019-00142 (via gateway)"

echo
echo "=== All responses saved to ${OUTPUT_DIR} ==="
