#!/usr/bin/env bash
# Captures pretty-printed JSON responses of every REST endpoint into this directory.
# Requires the app running (mvn -B spring-boot:run) and python3 on PATH.
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"

fetch() { # fetch <path> <file>
  curl -sf "$BASE_URL$1" | python3 -m json.tool --indent 2 > "$DIR/$2"
  echo "captured $1 -> $2"
}

ids() { python3 -c "import json,sys; [print(o['$1']) for o in json.load(sys.stdin)]"; }

fetch /api/loans loans.json
for id in $(ids loanAccountNumber < "$DIR/loans.json"); do
  fetch "/api/loans/$id" "loan_$id.json"
  fetch "/api/loans/$id/payments" "payments_$id.json"
done

fetch /api/borrowers borrowers.json
for id in $(ids id < "$DIR/borrowers.json"); do
  fetch "/api/borrowers/$id" "borrower_$id.json"
done
