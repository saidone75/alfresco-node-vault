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
