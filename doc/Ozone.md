# Native Apache Ozone storage

Set `VAULT_STORAGE_IMPL=ozone` to use the native Java RPC client rather than the
S3 Gateway. Metadata and audit data continue to use MongoDB.

Configure `OZONE_OM_ADDRESS` (default `localhost:9862`), `OZONE_VOLUME` (default
`anv`) and `OZONE_BUCKET` (default `content`). Provision the volume and bucket
before starting the application; the service uses the bucket replication policy.
The application must reach the Ozone Manager and the cluster Datanodes.

For HA or secured clusters, provide the matching `ozone-site.xml` and
`core-site.xml` on the runtime classpath and configure the Hadoop/Kerberos
identity for the process. Additional Hadoop/Ozone properties can be supplied as
`application.service.vault.storage.ozone.properties`, using bracketed map keys
such as `"[ozone.om.service.ids]"`. Use client configuration matching your cluster.

The backend supports the existing content encryption switch, plaintext checksums,
and key versions. Uploads stage content in a private temporary file to calculate
checksums and the exact stored length without buffering entire documents in heap.
Allow sufficient temporary disk space for concurrent uploads.

This backend uses Apache Ozone client 2.2.1. Changing the backend does not migrate
existing binaries; validate a separate archive/download/restore cycle before
switching an existing vault.

## Docker development stack

The Ozone variant replaces Moto/S3 with an Ozone Manager, a Storage Container
Manager and one Datanode. It uses the native RPC backend and starts no S3 Gateway.
Compose healthchecks wait for SCM to leave safe mode and for OM to accept RPC
requests before starting the initializer. The initializer creates the configured volume
and bucket if absent before the app starts. The end-to-end smoke tests verify
archiving, downloading and restoring content.
OM and Datanode start while SCM is becoming healthy, since SCM needs a registered
Datanode to leave safe mode.

From the repository root, create the external volumes used by the development
services on the first run:

```bash
docker volume create anv-acs-volume
docker volume create anv-postgres-volume
docker volume create anv-ass-volume
docker volume create anv-mongo-volume
```

Alternatively, `./vault.sh start --ozone` creates the external volumes and starts the stack
with Grafana included. Use `./vault.sh stop --ozone` to stop it.

To start the development stack directly:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.ozone.yml up --build -d
```

Stop it with the same Compose file:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.ozone.yml down
```

Ozone data is retained in Compose-managed volumes. The usual development services
publish their existing ports; the containerized app reaches Ozone through Docker's internal network. The development
stack also publishes OM RPC at `127.0.0.1:9862` for tests running on the host.
Do not run the regular development stack and this variant simultaneously.

`OZONE_IMAGE` and `OZONE_VERSION` in `docker/.env` select the server image (default
`apache/ozone:2.2.1`). `OZONE_VOLUME` and `OZONE_BUCKET` select the application bucket.
This is an unsecured, single-Datanode development setup using RATIS/ONE replication,
not a production durability or availability configuration.

## Recon GUI

Recon starts with the development stack at [http://localhost:9888](http://localhost:9888).
It is excluded from smoke tests. Monitoring data is retained on stop and removed by `purge --ozone`.

## Integration tests

After starting the development stack with `./vault.sh start --ozone`, run:

```bash
mvn test -Pozone
```

The Maven profile selects Ozone for the Spring application started by the tests.
The backend flag passed to `vault.sh` configures containers only; it does not change
Maven's default S3 selection. Use `-Ps3`, `-Pgridfs` or `-Pozone` to match the running stack.

The native client connects to OM at `localhost:9862` and directly to the Datanode
addresses returned by OM. This works with a local Linux Docker Engine whose bridge
network is reachable from the host. Publishing the OM port alone is insufficient
on Docker Desktop or a remote Docker daemon: those environments need routing to the
advertised Datanode addresses, or tests executed inside the Docker network.
The containerized [smoke tests](SmokeTest.md) already run inside that network.

For an externally reachable cluster, override `OZONE_OM_ADDRESS`, `OZONE_VOLUME`
and `OZONE_BUCKET` as needed. The bucket must already exist.

## Ozone smoke test

Run the isolated end-to-end suite with:

```bash
./smoke-tests.sh --ozone
```

On Windows, use `smoke-tests.bat --ozone`. Add `--keep-running` to retain running
services for inspection. The Ozone cases use the shared `anv-smoke` project,
with isolated volumes and container names, without host ports or external data volumes.
It checks plaintext and encrypted archive/download/restore flows, including
empty files, Unicode filenames, checksums and restore retries.

See [SmokeTest.md](SmokeTest.md) for reports, diagnostics and cleanup.
