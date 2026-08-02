#!/usr/bin/env bash
# Generates the 500k-loan / 2M-payment H2 file database used by the load test.
#
# It is written once and reused: building it takes minutes, and the load test should measure the
# service, not the data generator. Usage: scripts/generate-load-data.sh [output-dir]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-perf/data}"
mkdir -p "$OUT"

./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" perf/GenerateLoadData.java "$OUT"
