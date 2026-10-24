# Concurrent operations

[ShedLock](https://github.com/lukas-krecan/ShedLock) stores locks in the `shedLock`
MongoDB collection. Participating instances must share the vault database, content
storage, Alfresco repository and encryption configuration, use compatible versions
and have synchronized clocks.

## Behavior

Archiving, restore, deletion, key rotation, notarization and notarization checks
share a `node:<nodeId>` lock. Different nodes can run concurrently. Synchronous
requests return `409 Conflict` when the node is busy; ordinary metadata and content
reads do not acquire this lock.

Archiving, notarization, integrity sweeps and node lock cleanup have separate job locks. Manual and
scheduled sweeps share the same lock. Busy jobs are skipped. Background operations
log and skip busy nodes; retry through a later scheduled run or an explicit request.
Archive events are not automatically retried by the handler, and bulk key rotation
must be repeated to include skipped nodes. A busy integrity check counts as an
error without marking the node as corrupted. Asynchronous API responses acknowledge
the request; later conflicts are logged by the worker.

All bulk key updates share `job:bulk-key-update`, regardless of source version.
Overlapping runs are skipped and logged. Do not create new secret versions in HashiCorp
Vault while a bulk is queued or running; wait for completion in the logs. This operational
rule also applies to external administrators and automation: the ANV lock cannot block Vault writes.

Archiving uses a ten-second fixed delay in the `dev` and `docker` profiles: each
instance waits ten seconds after its run finishes (or is skipped) before trying
again. Other profiles use the configured cron window. Select the mode with
`ARCHIVING_JOB_SCHEDULING_MODE` (`cron` or `fixed-delay`), set the delay with
`ARCHIVING_JOB_FIXED_DELAY`, or the cron with `ARCHIVING_JOB_CRON_EXPRESSION`.
Both modes acquire the same distributed job lock.

## Configuration

| Environment variable | Default |
|----------------------|---------|
| `VAULT_NODE_LOCK_AT_MOST_FOR` | `2m` |
| `VAULT_JOB_LOCK_AT_MOST_FOR` | `2m` |
| `VAULT_JOB_LOCK_AT_LEAST_FOR` | `10s` |

Leases renew halfway through their duration on a dedicated executor. Locks are
released when work finishes, subject to the minimum job duration. The two-minute
job lease renews every minute, so jobs can run longer than two minutes; after a
crash, another instance can acquire the lock once the last lease expires. Lease durations
must be at least 30 seconds; the minimum job duration must be between zero and its
lease duration. Do not delete active lock records or add a TTL index.

## Node lock cleanup

An enabled-by-default job runs hourly and removes only `node:` records with
`lockUntil` at least one hour in the past. Job locks, active node locks and recently
expired node locks remain. Configure `NODE_LOCK_CLEANUP_ENABLED` and
`NODE_LOCK_CLEANUP_CRON_EXPRESSION` to disable or reschedule it. Cleanup runs under
`job:node-lock-cleanup`, so cooperating instances skip overlapping cleanup runs.

The deletion includes both the name prefix and expiration cutoff in its MongoDB
filter, so a lock renewed or reacquired before deletion no longer matches. The
MongoDB provider uses upsert to recreate a missing lock on its next acquisition.
This cleanup depends on that provider behavior; reassess it if changing providers.
It does not fix the expired-owner limitations described below.

## Limits

MongoDB acquisition failures prevent protected work from starting. After a crash,
locks become available when their last lease expires. If renewal fails or a process
pauses beyond expiry, its work can overlap a new owner: ShedLock cannot stop
in-flight storage or Alfresco requests. Interrupted operations may still require
manual reconciliation; locks do not provide atomic writes across systems or safe
rollback by themselves. Ethereum transactions for different nodes sharing an
account also need separate nonce coordination before full active-active support.
