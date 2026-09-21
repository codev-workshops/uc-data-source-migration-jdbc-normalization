# Proof artifacts: golden (before) vs live (after) parity

- `golden_hash_comparison.xlsx` — per-endpoint SHA-256 comparison of the 17 C0 golden
  files (legacy-backed baseline) against live responses of the modern-repository-backed
  app. Sheet `hash_comparison` has normalized hash before/after, raw hash before/after,
  MATCH/MISMATCH per endpoint and a summary row; sheet `method` documents the
  normalization (`python3 -m json.tool --sort-keys` equivalent) and base URL.
- `generate_hash_comparison.py` — regenerates the workbook. Requires the app running
  (`mvn -B spring-boot:run`) and `pip install openpyxl`.
- `verify_endpoint.sh <path> <golden-file>` — curls one endpoint, diffs the normalized
  body against the golden file and prints both SHA-256 hashes (used in the screen
  recording).

Latest run (base `df91fb0`): 17/17 endpoints MATCH.
