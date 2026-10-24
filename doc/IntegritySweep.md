# Integrity Sweep

Integrity Sweep is a verification feature that periodically re-checks notarized nodes and stores each run outcome in MongoDB.
It helps detect tampering, missing proofs, or unexpected verification errors over time.

## Execution modes

You can run Integrity Sweep in two complementary ways:

- **Scheduled mode** with `INTEGRITY_SWEEP_ENABLED=true` and a cron defined in `INTEGRITY_SWEEP_CRON_EXPRESSION`
- **On-demand mode** through REST API `POST /api/v1/vault/integrity-sweeps/run`

## Run results and monitoring

Each sweep run stores:

- status
- start/end timestamps
- duration
- counters: `scanned`, `passed`, `failed`, `errors`

| Counter | Meaning |
|---------|---------|
| `scanned` | Node checks counted as passed, failed or errors. |
| `passed` | Verification succeeded; any previous corruption verdict is cleared. |
| `failed` | Evidence of corruption or missing proof/content; the node is marked corrupted. |
| `errors` | Verification could not complete, for example due to an unavailable backend or busy node; the previous verdict is preserved. |

Nodes deleted from the vault during a sweep are skipped without increasing these counters.
`COMPLETED` means the run finished, even if some checks failed or encountered errors;
`FAILED` means a run-level exception interrupted it. Inspect the counters to assess integrity.

Historical runs are available via `GET /api/v1/vault/integrity-sweeps`; current corruption verdicts
via `GET /api/v1/vault/integrity-sweeps/corrupted-nodes`.

## Hybrid mode + controlled coverage for large notarized datasets

When the number of notarized nodes exceeds a threshold, Integrity Sweep switches to a reduced strategy designed to keep runtime bounded while preserving coverage over time:

- **Controlled coverage batch**: scans one deterministic page of notarized nodes (ordered by `_id`), rotating the page index daily using the UTC date.
- **Random addon batch (optional)**: adds a small random sample in the same run to increase anomaly discovery.

- `INTEGRITY_SWEEP_HYBRID_ENABLED` (default: `true`)
- `INTEGRITY_SWEEP_RANDOM_BATCH_ENABLED` (default: `true`)
- `INTEGRITY_SWEEP_RANDOM_BATCH_THRESHOLD` (default: `10000`)
- `INTEGRITY_SWEEP_BATCH_SIZE` defines the size of the deterministic coverage page
- `INTEGRITY_SWEEP_RANDOM_ADDON_SIZE` defines how many random nodes are added in hybrid mode

If threshold is not reached, the sweep keeps the original full pagination behavior across all notarized nodes.

Reduced mode is selected solely by the node-count threshold, even when
`INTEGRITY_SWEEP_HYBRID_ENABLED=false`; that flag and
`INTEGRITY_SWEEP_RANDOM_BATCH_ENABLED` control the random addon.
`INTEGRITY_SWEEP_RANDOM_ADDON_SIZE` defaults to `50`.

Notarization must be enabled. The manual endpoint returns immediately; a shared
lock prevents overlapping manual and scheduled sweeps, and busy runs are skipped.
A busy node increments `errors` without marking it corrupted. See
[Concurrency.md](Concurrency.md) for coordination and recovery limits.
