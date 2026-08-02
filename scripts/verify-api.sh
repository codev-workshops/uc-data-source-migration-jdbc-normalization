#!/usr/bin/env bash
# Smoke-checks both API generations against a running instance.
#
# v1 must be byte-for-byte what it always was; v2 must be bounded and reject anything that is not on
# the sort allow-list. Usage: scripts/verify-api.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"
fail=0

check() {
  local description="$1" expected="$2" url="$3"
  local actual
  actual="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${url}")"
  if [[ "$actual" == "$expected" ]]; then
    printf 'ok    %-58s %s\n' "$description" "$actual"
  else
    printf 'FAIL  %-58s expected %s got %s\n' "$description" "$expected" "$actual"
    fail=1
  fi
}

check "v1 loans (unbounded, unchanged)"            200 "/api/loans"
check "v1 loan by id"                              200 "/api/loans/LN-2019-00142"
check "v1 payments for loan"                       200 "/api/loans/LN-2019-00142/payments"
check "v1 borrowers"                               200 "/api/borrowers"
check "v1 borrower by id"                          200 "/api/borrowers/B-10001"
check "v1 unknown loan is 404, not 500"            404 "/api/loans/LN-nope"
check "v2 loans (bounded)"                         200 "/api/v2/loans?size=5"
check "v2 keyset page"                             200 "/api/v2/loans?afterId=0&size=5"
check "v2 count opt-in"                            200 "/api/v2/loans?size=5&count=true"
check "v2 rejects sort outside the allow list"     400 "/api/v2/loans?sort=ssnHash,asc"
check "v2 rejects a negative page size"            400 "/api/v2/loans?size=-1"
check "actuator health"                            200 "/actuator/health"
check "actuator env is not exposed"                404 "/actuator/env"
check "h2 console is not reachable"                404 "/h2-console"

echo
echo "v1 default page size is unbounded by design; v2 caps at 100."
exit "$fail"
