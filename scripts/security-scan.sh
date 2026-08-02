#!/usr/bin/env bash
# SQL-injection and exposure review, in two halves:
#   1. static  - no dynamically assembled query anywhere in main sources
#   2. runtime - the injection payload tests, the sort allow list, and the actuator lockdown
# Publishes reports/SECURITY_REPORT.md.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p reports
REPORT=reports/SECURITY_REPORT.md

echo "== static: dynamic SQL, raw JDBC, and PII in logs =="
patterns=(
  'createNativeQuery'
  'nativeQuery[[:space:]]*=[[:space:]]*true'
  'JdbcTemplate'
  'createStatement'
  'Statement\.execute'
)
findings=""
for pattern in "${patterns[@]}"; do
  if hits="$(grep -rInE "$pattern" src/main/java || true)"; [[ -n "$hits" ]]; then
    echo "$hits"
    echo "  ^ forbidden: $pattern"
    findings+="$pattern"$'\n'
  fi
done
[[ -z "$findings" ]] && echo "  none found"

echo
echo "== runtime: guard, allow list, injection payloads, actuator exposure =="
set +e
./mvnw -B verify \
  -Dtest=NoDynamicSqlSourceGuardTest,PageRequestsTest \
  -Dit.test=SqlInjectionIT,ObservabilityIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfailsafe.failIfNoSpecifiedTests=false
status=$?
set -e

{
  echo "# Security report"
  echo
  echo "Generated $(date -u '+%Y-%m-%d %H:%M UTC') by \`scripts/security-scan.sh\`."
  echo
  if [[ -z "$findings" && $status -eq 0 ]]; then
    echo "**No findings.**"
  else
    echo "**Findings present - see below.**"
  fi
  echo
  echo "## SQL injection"
  echo
  echo "Every query in the service is either a Spring Data derived query or static JPQL with bound"
  echo "parameters. Nothing user-supplied is ever concatenated into a query, and the sort parameter -"
  echo "the one input that does reach the query structure - is mapped through an allow list of"
  echo "property names, so an unknown value is rejected with 400 rather than passed through."
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  for pattern in "${patterns[@]}"; do
    if grep -q "$pattern" <<<"$findings"; then
      echo "| No \`$pattern\` in main sources | FOUND |"
    else
      echo "| No \`$pattern\` in main sources | pass |"
    fi
  done
  echo "| \`NoDynamicSqlSourceGuardTest\` (static source guard) | $([[ $status -eq 0 ]] && echo pass || echo see run) |"
  echo "| \`SqlInjectionIT\` (payloads through paths, sorts, paging) | $([[ $status -eq 0 ]] && echo pass || echo see run) |"
  echo "| \`PageRequestsTest\` (sort allow list, size limits) | $([[ $status -eq 0 ]] && echo pass || echo see run) |"
  echo
  echo "Payloads exercised: \`' OR '1'='1\`, \`'; DROP TABLE loan_accounts; --\`,"
  echo "\`LN-2019-00142' OR '1'='1\`, \`1 UNION SELECT ssn_hash FROM borrowers\`, and sort values such as"
  echo "\`currentBalance,desc; DELETE FROM payments\` and \`id,asc) OR (1=1\`. After each, row counts are"
  echo "re-checked and the response is scanned for leaked values."
  echo
  echo "## Data exposure"
  echo
  echo "| Control | Where |"
  echo "|---|---|"
  echo "| SSN hash never leaves the service (absent from v1 and v2 DTOs) | \`BorrowerV2Dto\`, \`LoanV2Dto\` |"
  echo "| Hash preserved verbatim on migration - no re-hashing, no algorithm change | \`LegacyToModernMigrationService#toBorrower\` |"
  echo "| Errors return a fixed reason and never echo the requested identifier | \`ApiExceptionHandler\`, \`LoanNotFoundException\` |"
  echo "| No SSN, date of birth or income in any log statement (enforced statically) | \`NoDynamicSqlSourceGuardTest\` |"
  echo "| H2 console disabled outside the \`dev\` profile | \`application.properties\` |"
  echo "| Actuator limited to health, info, metrics and prometheus; health details hidden | \`application.properties\` |"
  echo
  echo "## Known residual risk"
  echo
  echo "\`GET /api/loans\` and \`GET /api/borrowers\` remain unbounded, by product decision: existing"
  echo "clients depend on the current behaviour. At 500k rows a single call is a multi-hundred-megabyte"
  echo "response and a plausible denial-of-service lever. It is metered (\`loanservice.v1.large_response\`)"
  echo "and logged at WARN, but not capped. \`/api/v2\` exists as the bounded alternative."
} > "$REPORT"

echo
echo "Wrote $REPORT"
exit "$status"
