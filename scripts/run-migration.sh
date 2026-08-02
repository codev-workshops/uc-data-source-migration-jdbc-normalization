#!/usr/bin/env bash
# Runs the legacy -> modern backfill on a throwaway application instance and prints the report.
#
# Usage: scripts/run-migration.sh [strict|lenient] [chunk-size]
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-strict}"
CHUNK="${2:-1000}"

./mvnw -B -q spring-boot:run \
  -Dspring-boot.run.arguments="--loanservice.migration.mode=${MODE} --loanservice.migration.chunk-size=${CHUNK} --loanservice.migration.exit-after-migration=true"
