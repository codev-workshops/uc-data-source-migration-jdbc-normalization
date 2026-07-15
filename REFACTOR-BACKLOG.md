# Refactor Backlog

These findings were intentionally excluded from the approved data-source
migration because changing them could alter externally observable behavior or
unrelated configuration defaults.

## Standardize Not-Found Responses

Missing borrower and loan records currently propagate an uncaught
`RuntimeException` and produce the pre-existing HTTP 500 response. A separate
API change should introduce a documented 404 error model with compatibility
review and updated clients/tests.

## Disable Open EntityManager in View

Spring logs that `spring.jpa.open-in-view` is enabled by default. The migrated
service uses read-only transactions and relationship-aware repository queries,
but changing this framework default should be evaluated separately across all
request paths before it is disabled.
