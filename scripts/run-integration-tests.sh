#!/usr/bin/env bash
# Integration tests only (*IT): full application context, real H2 databases, real migration.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B verify -DskipUTs=true "$@"
