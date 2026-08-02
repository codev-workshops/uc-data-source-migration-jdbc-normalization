#!/usr/bin/env bash
# Unit tests only: pure logic (parsers, code translation, v1 formatting, paging rules) with no
# Spring context, so this is the fast feedback loop.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B test "$@"
