# Integrity Sweep

Integrity Sweep is a verification feature that periodically re-checks notarized nodes and stores each run outcome in MongoDB.
It helps detect tampering, missing proofs, or unexpected verification errors over time.

## Execution modes

You can run Integrity Sweep in two complementary ways:

- **Scheduled mode** with `INTEGRITY_SWEEP_ENABLED=true` and a cron defined in `INTEGRITY_SWEEP_CRON_EXPRESSION`
- **On-demand mode** through REST API `POST /api/vault/integrity-sweeps/run`

## Run results and monitoring

Each sweep run stores:

- status
- start/end timestamps
- duration
- counters: `scanned`, `passed`, `failed`, `errors`

Historical runs are available via `GET /api/vault/integrity-sweeps`.
