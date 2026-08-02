#!/usr/bin/env bash
# Runs the load profiles and writes reports/LOAD_TEST_REPORT.md.
#
# This measures one JVM against H2 on whatever machine it runs on. Numbers are that machine's
# capacity, not a production projection.
set -euo pipefail
cd "$(dirname "$0")/.."

./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -XX:+UseG1GC -Xmx2g -cp "target/classes:$(cat target/cp.txt)" perf/LoadTest.java "$@"
