# Native Apache Ozone storage

Set `VAULT_STORAGE_IMPL=ozone` to store content through the native Java RPC client.
Metadata and audit data remain in MongoDB. Content encryption, checksums and key
versions are supported. Switching backend does not migrate existing content.

| Variable                         | Default          | Purpose                                                                    |
|----------------------------------|------------------|----------------------------------------------------------------------------|
| `OZONE_OM_ADDRESS`               | `localhost:9862` | Ozone Manager RPC address                                                  |
| `OZONE_VOLUME`                   | `anv`            | Storage volume                                                             |
| `OZONE_BUCKET`                   | `content`        | Content bucket                                                             |
| `OZONE_CLIENT_BUFFER_SIZE`       | `1MB`            | Size of the chunks sent to the datanodes                                   |
| `OZONE_CLIENT_BUFFER_FLUSH_SIZE` | `2MB`            | Buffered data that triggers a partial flush; a multiple of the buffer size |
| `OZONE_CLIENT_BUFFER_MAX_SIZE`   | `4MB`            | Maximum data buffered in memory per upload; a multiple of the flush size   |

The client buffer defaults favour low memory use, at most 4MB per upload in progress
instead of the 32MB of the Ozone client defaults (`4MB`, `16MB`, `32MB`). Larger
buffers mean fewer round trips and can speed up uploads of large files on fast networks.

For an external cluster, provision the volume and bucket first. Additional Hadoop/Ozone
settings belong under `application.service.vault.storage.ozone.properties`.

## Authentication

The development stack disables Ozone security and requires no credentials.
For secured clusters, the native RPC backend uses Kerberos authentication, as described
in the official [Ozone Kerberos configuration guide](https://ozone.apache.org/docs/administrator-guide/configuration/security/kerberos/).
`S3_KEY` and `S3_SECRET` apply only to the S3 backend and are not used by this client.

Configure the cluster for secure operation and provide the application with matching
Hadoop/Ozone client settings, including:

```yaml
application:
  service:
    vault:
      storage:
        ozone:
          properties:
            "[ozone.security.enabled]": "true"
            "[hadoop.security.authentication]": kerberos
            "[ozone.om.kerberos.principal]": om/_HOST@EXAMPLE.COM
```

Replace the example OM principal with the cluster's service principal. These settings
describe the cluster's authentication requirements; they do not log the application in.
The application process also needs Kerberos realm/KDC configuration (typically
`/etc/krb5.conf`) and valid credentials for its own principal, with permission to access
the configured volume and bucket.

The current application does not expose Ozone username/password or principal/keytab
environment variables, perform an explicit keytab login, or manage ticket renewal.
A secured deployment must arrange credential acquisition and renewal for the JVM's
Hadoop security context. Secure-cluster authentication is not exercised by the
development stack or smoke tests.

See [How Ozone Uses Kerberos](https://ozone.apache.org/docs/system-internals/security/kerberos/)
for the authentication model. HTTP consoles use a separate
[SPNEGO configuration](https://ozone.apache.org/docs/administrator-guide/configuration/security/https/);
those settings do not configure this application's RPC authentication.

## Development stack

```bash
./vault.sh start --ozone
./vault.sh stop
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
