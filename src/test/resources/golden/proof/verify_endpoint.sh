#!/usr/bin/env bash
# verify_endpoint.sh <path> <golden-file>
# curls the live app, prints the body, then diffs it (normalized with
# `python3 -m json.tool --sort-keys`) against the golden file and prints hashes.
set -uo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"
path="$1"; golden="$DIR/$2"
echo "=================================================================="
echo "GET $BASE_URL$path      (golden: $2)"
echo "=================================================================="
live=$(curl -s "$BASE_URL$path")
echo "$live" | python3 -m json.tool --sort-keys | head -n "${HEAD:-40}"
echo "..."
if diff <(echo "$live" | python3 -m json.tool --sort-keys) \
        <(python3 -m json.tool --sort-keys "$golden"); then
  echo "diff: identical"
else
  echo "diff: DIFFERENT"
fi
hb=$(python3 -m json.tool --sort-keys "$golden" | sha256sum | cut -c1-64)
ha=$(echo "$live" | python3 -m json.tool --sort-keys | sha256sum | cut -c1-64)
echo "sha256 before (golden): $hb"
echo "sha256 after  (live)  : $ha"
[ "$hb" = "$ha" ] && echo ">>> MATCH  $path" || echo ">>> MISMATCH  $path"
