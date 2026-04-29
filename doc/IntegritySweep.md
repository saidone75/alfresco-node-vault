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

## Hybrid mode + controlled coverage for large notarized datasets

When the number of notarized nodes exceeds a threshold, Integrity Sweep switches to a reduced strategy designed to keep runtime bounded while preserving coverage over time:

- **Controlled coverage batch**: scans one deterministic page of notarized nodes (ordered by `_id`), rotating the page index daily.
- **Random addon batch (optional)**: adds a small random sample in the same run to increase anomaly discovery.

- `INTEGRITY_SWEEP_HYBRID_ENABLED` (default: `true`)
- `INTEGRITY_SWEEP_RANDOM_BATCH_ENABLED` (default: `true`)
- `INTEGRITY_SWEEP_RANDOM_BATCH_THRESHOLD` (default: `10000`)
- `INTEGRITY_SWEEP_BATCH_SIZE` defines the size of the deterministic coverage page
- `INTEGRITY_SWEEP_RANDOM_ADDON_SIZE` defines how many random nodes are added in hybrid mode

If threshold is not reached, the sweep keeps the original full pagination behavior across all notarized nodes.
