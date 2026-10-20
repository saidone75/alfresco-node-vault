# Native Apache Ozone storage

Set `VAULT_STORAGE_IMPL=ozone` to store content through the native Java RPC client.
Metadata and audit data remain in MongoDB. Content encryption, checksums and key
versions are supported. Switching backend does not migrate existing content.

| Variable | Default | Purpose |
|----------|---------|---------|
| `OZONE_OM_ADDRESS` | `localhost:9862` | Ozone Manager RPC address |
| `OZONE_VOLUME` | `anv` | Storage volume |
| `OZONE_BUCKET` | `content` | Content bucket |

For an external cluster, provision the volume and bucket first. Additional Hadoop/Ozone
settings belong under `application.service.vault.storage.ozone.properties`.
Secured clusters also require matching Hadoop configuration and a Kerberos identity.

## Development stack

```bash
./vault.sh start --ozone
./vault.sh stop --ozone
```

The launcher prepares the Docker volumes and starts OM, SCM, one Datanode and Recon.
Healthchecks gate initialization; the storage volume and bucket are created automatically.
Recon is available at [http://localhost:9888](http://localhost:9888).

Containers use `anv-ozone-*` names. Data survives a stop;
`./vault.sh purge --ozone` removes the stack's data volumes.
Stop the current development stack before switching backend.

`OZONE_IMAGE` and `OZONE_VERSION` in `docker/.env` select the server image
(default `apache/ozone:2.2.1`). This development setup uses RATIS/ONE replication
without security.

## Tests

With the development stack running:

```bash
mvn test -Pozone
```

The Maven profile must match the running backend. Host tests connect to OM at
`localhost:9862` and directly to Datanode addresses on the Docker network.
A local Linux Docker Engine supports this; Docker Desktop or remote Docker requires
additional routing or running the tests inside the Docker network.

For isolated, containerized smoke tests:

```bash
./smoke-tests.sh --ozone
```

On Windows, use `smoke-tests.bat --ozone`. Add `--keep-running` for inspection
or `--all` to test every backend. Recon is excluded from smoke tests.
See [SmokeTest.md](SmokeTest.md) for checks, reports and cleanup.
