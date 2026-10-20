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

Archiving, notarization and integrity sweeps have separate job locks. Manual and
scheduled sweeps share the same lock. Busy jobs are skipped. Background operations
log and skip busy nodes; retry through a later scheduled run or an explicit request.
Archive events are not automatically retried by the handler, and bulk key rotation
must be repeated to include skipped nodes. A busy integrity check counts as an
error without marking the node as corrupted. Asynchronous API responses acknowledge
the request; later conflicts are logged by the worker.

## Configuration

| Environment variable | Default |
|----------------------|---------|
| `VAULT_NODE_LOCK_AT_MOST_FOR` | `2m` |
| `VAULT_JOB_LOCK_AT_MOST_FOR` | `1h` |
| `VAULT_JOB_LOCK_AT_LEAST_FOR` | `1m` |

Leases renew halfway through their duration on a dedicated executor. Locks are
released when work finishes, subject to the minimum job duration. Lease durations
must be at least 30 seconds; the minimum job duration must be between zero and its
lease duration. Do not delete active lock records or add a TTL index.

## Limits

MongoDB acquisition failures prevent protected work from starting. After a crash,
locks become available when their last lease expires. If renewal fails or a process
pauses beyond expiry, its work can overlap a new owner: ShedLock cannot stop
in-flight storage or Alfresco requests. Interrupted operations may still require
manual reconciliation; locks do not provide atomic writes across systems or safe
rollback by themselves. Ethereum transactions for different nodes sharing an
account also need separate nonce coordination before full active-active support.
