#!/usr/bin/env bash
# =============================================================================
# Side-by-side API comparison: monolith vs microservices
# =============================================================================
# Usage: ./compare.sh [monolith_base_url] [gateway_base_url]
#
# Defaults:
#   monolith_base_url = http://localhost:8080
#   gateway_base_url  = http://localhost:9080
# =============================================================================

set -euo pipefail

MONOLITH_URL="${1:-http://localhost:8080}"
GATEWAY_URL="${2:-http://localhost:9080}"
PASS=0
FAIL=0

compare() {
    local endpoint="$1"
    local label="$2"
    
    echo -n "Testing ${label}... "
    
    mono_resp=$(curl -s "${MONOLITH_URL}${endpoint}" 2>/dev/null || echo "ERROR")
    micro_resp=$(curl -s "${GATEWAY_URL}${endpoint}" 2>/dev/null || echo "ERROR")
    
    if [ "$mono_resp" = "$micro_resp" ]; then
        echo "PASS (responses match)"
        PASS=$((PASS + 1))
    else
        echo "DIFF"
        echo "  Monolith:      $(echo "$mono_resp" | head -c 200)"
        echo "  Microservices: $(echo "$micro_resp" | head -c 200)"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== API Comparison: Monolith vs Microservices ==="
echo "Monolith:      ${MONOLITH_URL}"
echo "Microservices: ${GATEWAY_URL}"
echo "================================================="
echo

compare "/api/loans" "GET /api/loans"
compare "/api/loans/LN-2019-00142" "GET /api/loans/{id}"
compare "/api/loans/LN-2019-00142/payments" "GET /api/loans/{id}/payments"
compare "/api/borrowers" "GET /api/borrowers"
compare "/api/borrowers/B-10001" "GET /api/borrowers/{id}"
compare "/api/payments/loan/LN-2019-00142" "GET /api/payments/loan/{loanId}"

echo
echo "================================================="
echo "Results: ${PASS} passed, ${FAIL} failed"
echo "================================================="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
