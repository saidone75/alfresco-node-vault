# REST API

Alfresco Node Vault exposes a versioned REST API under `/api/v1`:

- `/api/v1/vault` to search, retrieve, archive, restore, notarize and re-encrypt nodes and to run
  [integrity sweeps](IntegritySweep.md)
- `/api/v1/audit` to read the audit trail

The reference of every endpoint, with its parameters, request and response schemas, is the OpenAPI documentation
served by the application at `/swagger` (e.g. http://localhost:8086/swagger). This page covers what is not part of it.

## Authentication

All endpoints require HTTP Basic authentication with the credentials of an Alfresco **administrator**. Credentials are
verified against Alfresco and successful checks are cached for `VAULT_AUTH_CACHE_TTL` (default `60s`).

## Alfresco proxy

Requests to the Alfresco REST API for nodes that are archived in the vault are served by the vault itself, so that
existing clients keep working without changes:

- `GET /alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}`
- `GET /alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}/content`

Any other request under `/alfresco/**` is forwarded to Alfresco. These routes are handled by the gateway and are not
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
| `401`  | Missing or invalid credentials                                                                  |
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
