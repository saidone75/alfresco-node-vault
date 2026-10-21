# Vault operation metrics

Exposed at `/actuator/prometheus`. Set `VAULT_OPERATION_METRICS_ENABLED=false`
(default: `true`) to disable them; other application and JVM metrics remain enabled.
In Docker, configure this in `docker/.env` and recreate the vault.

| Metric | Meaning |
|--------|---------|
| `vault_operations_total{operation,outcome}` | Archive, restore and delete requests by result. |
| `vault_operation_duration_seconds{operation,outcome}` | Duration histogram (`_bucket`, `_count`, `_sum`), including locking and cleanup. |
| `vault_archived_bytes_total` | Original bytes newly archived successfully, excluding existing copies and rollbacks. |

`operation`: `archive`, `restore`, `delete`. `outcome`: `success`, `error`, `skipped`
(lock busy). Success includes idempotent retries; internal restore steps count only
as part of delete. Counters reset on restart.

Grafana's **Vault Operations** row shows operations/s, mean/p95 successful request
duration and archived bytes/s, filtered by application and instance.
