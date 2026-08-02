#!/usr/bin/env bash
# Everything: unit tests, integration tests, JaCoCo coverage, and the static SQL source guard.
# Publishes reports/TEST_REPORT.md from the results.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B clean verify "$@"
python3 scripts/generate-test-report.py
echo
echo "Reports:"
echo "  summary      reports/TEST_REPORT.md"
echo "  unit         target/surefire-reports"
echo "  integration  target/failsafe-reports"
echo "  coverage     target/site/jacoco/index.html"
