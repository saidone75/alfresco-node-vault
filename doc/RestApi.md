# REST API

Alfresco Node Vault exposes a versioned REST API under `/api/v1`:

- `/api/v1/vault` to search, retrieve, archive, restore, notarize and re-encrypt nodes and to run
  [integrity sweeps](IntegritySweep.md)
- `/api/v1/audit` to read the audit trail

The reference of every endpoint, with its parameters, request and response schemas, is the OpenAPI documentation
served by the application at `/swagger` (e.g. http://localhost:8086/swagger). This page covers what is not part of it.

## Authentication

Vault APIs require HTTP Basic authentication verified against Alfresco:

- **Alfresco administrators** always have access, without needing readers-group membership.
- **Members of any configured reader group** can search archived documents (`GET /api/v1/vault/search` and
  `POST /api/v1/vault/advanced-search`), read node metadata and download content.
- All other APIs, including archive, restore, delete, key rotation, notarization, integrity sweeps
  and audit, remain restricted to administrators.

`VAULT_READERS_GROUPS` accepts comma-separated Alfresco group IDs (default `GROUP_ANV_READERS`,
the Alfresco group `ANV_READERS`). Membership in **any one** of the listed groups is sufficient.
For example: `VAULT_READERS_GROUPS=GROUP_ANV_READERS,GROUP_ARCHIVISTS`. Whitespace around IDs
and duplicate IDs are ignored; empty lists or blank IDs are rejected at startup.
Create the groups and assign members in Alfresco; the vault does not provision them. In Docker, set the variable in
`docker/.env` and recreate the application container. Missing groups grant no reader access;
administrators retain access.

**The vault currently does not preserve Alfresco ACLs for document access.** Group members can consult
all archived documents, regardless of permissions on the original Alfresco nodes. Permission metadata
may be stored and optionally restored to Alfresco, but is not used to authorize vault reads.

Successful administrator and reader checks are cached separately for `VAULT_AUTH_CACHE_TTL`
(default `60s`). Revocations can take up to that duration to take effect; set `0s` to disable caching.
Missing credentials, invalid credentials and insufficient privileges return `401`, following the
existing API convention. Actuator endpoints use separate monitoring credentials.

## Alfresco proxy

The following Alfresco REST API requests for archived nodes are served by the vault. Clients must
address the vault and send Basic credentials of an administrator or a member of any configured reader group:

- `GET /alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}`
- `GET /alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}/content`

These two routes apply the vault access policy, including access to all archived documents for readers;
they do not enforce original Alfresco ACLs. Alfresco tickets and nonmember user credentials do not grant vault access.

Any other request under `/alfresco/**` is forwarded to Alfresco and uses Alfresco authorization. These routes are handled by the gateway and are not
listed in the OpenAPI documentation.

## Conventions

- Paginated endpoints accept `page` (zero-based, default `0`) and `size` (default `20`).
- Dates are ISO-8601 instants, e.g. `2026-01-01T00:00:00Z`.
- Search and metadata endpoints return JSON, with nodes wrapped in an `entry` object as in the Alfresco REST API.
- Operations return a plain-text (`text/plain`) confirmation message, and errors return a plain-text description.
- Endpoints of features disabled by configuration (notarization, encryption, delete node API, advanced search with
  encrypted metadata) return `403`.

| Status | Meaning                                                                                         |
|--------|-------------------------------------------------------------------------------------------------|
| `400`  | Invalid parameters or request body                                                              |
| `401`  | Missing or invalid credentials, or insufficient privileges                                                                  |
| `403`  | The feature is disabled by configuration                                                        |
| `404`  | Node not found on the vault or on Alfresco                                                      |
| `409`  | Conflicting state: another operation on the same node is in progress, node already notarized, node not notarized or hashes not matching the notarized ones |
| `500`  | Internal server error                                                                           |

## Restore retries

A completed restore returns the existing Alfresco node ID while that node still exists.
If uploading content or saving the restored node ID fails, the vault attempts to permanently delete only the
node created by that attempt, then reports the original error. After successful cleanup, a retry creates a new node.
A process interruption, a lost creation response or failed cleanup may leave a node requiring manual reconciliation.
Operations on the same node share a MongoDB-backed ShedLock lock across cooperating instances.
Synchronous requests return `409` if the lock is unavailable. Asynchronous requests report later
conflicts in worker logs. See [concurrency](Concurrency.md) for lease durations and recovery limitations.

## Deleting archived documents

`DELETE /api/v1/vault/nodes/{nodeId}` requires administrator credentials and
`VAULT_DELETE_NODE_API_ENABLED=true` (default `false`). It removes vault content and metadata.

If neither the original Alfresco node nor its recorded restored copy exists, the default behavior
first restores the document without permissions, then moves the new copy to the Alfresco trash.
`VAULT_DELETE_NODE_RESTORE_ON_ALFRESCO` and `VAULT_DELETE_NODE_DELETE_ON_ALFRESCO` control these steps
(both default `true`). If either Alfresco copy already exists, both steps are skipped and that copy is retained.
Set both flags to `false` to delete only from the vault. Disabling restore alone can make deletion fail
when the original node is missing.

These flags are separate from archive-time deletion, which permanently deletes the Alfresco source
by default (`VAULT_DELETE_ON_ALFRESCO=true`, `application.service.alfresco.permanently-delete-nodes=true`).

## Re-encryption

The update-key APIs use the latest existing Vault secret version; they do not create or rotate secrets.
Create the new version in Vault first, then call:

- `POST /api/v1/vault/nodes/{nodeId}/update-key` to re-encrypt one outdated node; an up-to-date node is unchanged.
- `POST /api/v1/vault/nodes/update-keys?keyVersion=<source version>` to re-encrypt nodes using that source version.
  The bulk returns `202 Accepted`; follow completion in the logs and do not rotate secrets while it is pending or running.

See [rotation and retention](Encryption.md#key-rotation-and-retention) for the operational procedure.

## Advanced search example

`POST /api/v1/vault/advanced-search` searches the archived nodes by their metadata; the meaning of each criterion is
described in the OpenAPI documentation. For example, the PDF documents created in the first half of 2026 with the
Dublin Core aspect and published by ACME or Initech:

```json
{
  "mimeType": "application/pdf",
  "createdFrom": "2026-01-01T00:00:00Z",
  "createdTo": "2026-06-30T23:59:59Z",
  "aspects": ["cm:dublincore"],
  "properties": [
    { "name": "cm:publisher", "values": ["ACME", "Initech"] }
  ]
}
```

The advanced search is available only when metadata encryption is disabled (`VAULT_ENCRYPT_METADATA=false`). Nodes
whose metadata was stored encrypted are never returned.
