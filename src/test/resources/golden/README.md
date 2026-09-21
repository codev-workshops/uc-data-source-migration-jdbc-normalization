# Golden files (pre-migration parity baseline)

Pretty-printed JSON responses of every REST endpoint, captured while the application
was still reading the legacy CDW tables (`schema-legacy.sql` / `data-legacy.sql`).
They are the parity baseline for the data-source migration: after switching to the
modern schema, every endpoint must return semantically identical JSON.

## Naming scheme

| File                       | Endpoint                          |
|----------------------------|-----------------------------------|
| `loans.json`               | `GET /api/loans`                  |
| `loan_<loanId>.json`       | `GET /api/loans/{loanId}`         |
| `payments_<loanId>.json`   | `GET /api/loans/{loanId}/payments`|
| `borrowers.json`           | `GET /api/borrowers`              |
| `borrower_<borrowerId>.json` | `GET /api/borrowers/{borrowerId}` |

Loan IDs come from `loans.json` (`loanAccountNumber`), borrower IDs from
`borrowers.json` (`id`), so every seeded entity is covered.

## How they were captured / how to regenerate

1. Start the app on the legacy schema: `mvn -B spring-boot:run` (http://localhost:8080).
2. Run `src/test/resources/golden/capture.sh` (needs `curl` and `python3`;
   override the host with `BASE_URL=...`). It fetches each endpoint and writes the
   response through `python3 -m json.tool --indent 2`.

Only regenerate when the *intended* API contract changes, not to make a failing
parity test pass.

## Verification

`GoldenFileParityTest` (`src/test/java/.../golden/`) loads every JSON file in this
directory, maps the file name back to its endpoint, calls it through `MockMvc` and
compares the response with the golden file as JSON trees (key order and number
formatting such as `4.750` vs `4.75` are ignored; values and structure must match).
