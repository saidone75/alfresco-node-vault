# Alfresco Node Vault

Alfresco Node Vault is an open-source solution for archiving historical documents outside Alfresco,
reducing the active repository while keeping documents accessible through REST APIs and allowing
them to be restored when needed. Designed for administrators and integrators managing growing
repositories, it supports scheduled and on-demand archiving, storing metadata in MongoDB and
content in GridFS or S3. Its Alfresco API proxy helps existing applications retrieve archived
documents, while optional encryption and integrity checks protect the archived data.

![Vault](images/vault.png)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Javadoc](https://img.shields.io/badge/Javadoc-API-blue.svg)](https://saidone75.github.io/alfresco-node-vault/javadoc/)
![Java CI](https://github.com/saidone75/alfresco-node-vault/actions/workflows/build.yml/badge.svg)

[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=saidone75_alfresco-node-vault&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=saidone75_alfresco-node-vault)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=saidone75_alfresco-node-vault&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=saidone75_alfresco-node-vault)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=saidone75_alfresco-node-vault&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=saidone75_alfresco-node-vault)

## The Long-term Document Management Challenge

Over time, many Alfresco installations become slower and more difficult to manage as their repositories grow.
The accumulation of millions of documents puts increasing pressure on the database and search index,
slowing down queries and affecting overall system performance.

This situation is particularly common in organizations where:

- Effective corporate policies for archiving or deleting obsolete documents are missing
- Documents are retained indefinitely due to regulatory or compliance obligations
- Repository sizes grow exponentially year after year
- Users experience increasingly long response times in daily operations
- Infrastructure costs continually increase to support ever-larger repositories
- Maintenance operations (backup, indexing, upgrades) become progressively more complex and time-consuming

Repositories with hundreds of millions of documents can require days for complete reindexing operations, or weeks for
migrations during version upgrades.

Many enterprises find themselves in a difficult position: business units demand that all documents remain accessible,
while IT departments struggle to manage increasingly unresponsive Alfresco instances. Technical debt
accumulates, making each upgrade more complex than the last. Site collections become sluggish, search operations time
out, and what was once a highly efficient ECM solution becomes a burden on daily operations.

## The solution: Alfresco Node Vault

Alfresco Node Vault is a Spring Boot application designed for long-term document storage. Archived documents
are removed from Alfresco and its database, freeing resources while keeping those documents accessible.

The application supports on-demand, event-driven and scheduled document archiving.

Document metadata is stored in MongoDB, while content can be stored in either GridFS or AWS S3,
providing a flexible foundation for long-term storage.

The application can also act as an Alfresco REST API proxy, allowing existing applications to retrieve
archived documents through supported endpoints and maintain access to historical content.

Documents can also be automatically restored to Alfresco when needed.

By implementing Alfresco Node Vault, organizations can:

- Maintain high performance in their active Alfresco repository
- Keep all historical documents accessible when needed
- Significantly reduce backup and maintenance windows
- Lower infrastructure costs by optimizing resource usage
- Simplify upgrade processes by reducing the volume of live data

This approach bridges the gap between complete document purging (often unacceptable for business or compliance reasons)
and the indefinite retention of all documents in the active repository (unsustainable from a performance perspective).

## Key Features

- Lean and elegant code architecture
- Free and open-source software, released under GPL v3 or later
- Scheduled or on-demand archiving
- MongoDB storage for document metadata and the audit trail
- GridFS or S3 storage for document content
- Optional blockchain document notarization to provide verifiable evidence of integrity
- Scheduled and on-demand [integrity sweeps](doc/IntegritySweep.md) to verify notarized documents
- Secure and decoupled design that suits zero trust environments
- Optional [strong encryption](doc/Encryption.md) for both content and metadata
- Passwords securely stored in a secret engine
- [REST API](doc/RestApi.md) with Alfresco proxy support and metadata search
- Engineered for flexibility and bespoke integrations
- No installation or changes required within Alfresco
- Prometheus monitoring support for metrics and health checks
- Custom upload and download methods
- Minimal memory footprint
- Robust, enterprise-grade reliability and performance

Pull requests are welcome!

## Architecture

![Architecture](images/architecture.png)

## Application global config

Global configuration is stored in `application.yml`. The main parameters are:

| Parameter/env variable           | Default value                              | Purpose                                                             |
|----------------------------------|--------------------------------------------|---------------------------------------------------------------------|
| ALFRESCO_BASE_PATH               | http://localhost:8080                      | Scheme, host and port of the Alfresco server                        |
| ALFRESCO_USERNAME                | admin                                      | Alfresco user                                                       |
| ALFRESCO_PASSWORD                | admin                                      | Password for the Alfresco user                                      |
| ACTIVE_MQ_URL                    | tcp://localhost:61616                      | ActiveMQ broker URL                                                 |
| MONGODB_URL                      | mongodb://localhost:27017                  | MongoDB connection string                                           |
| S3_ENDPOINT                      | http://localhost:5000                      | S3 object storage endpoint                                          |
| S3_KEY                           | testing                                    | Key for S3 authentication                                           |
| S3_SECRET                        | testing                                    | Secret for S3 authentication                                        |
| EVENT_HANDLER_ENABLED            | false                                      | Event based archive behaviour switch                                |
| ARCHIVING_JOB_ENABLED            | true                                       | Archiving scheduled job switch                                      |
| ARCHIVING_JOB_CRON_EXPRESSION    | 0 0/5 2-6 * * ?                            | Scheduled job cron expression                                       |
| ARCHIVING_JOB_QUERY              | TYPE:'cm:content' AND ASPECT:'anv:archive' | Query for selecting documents to be archived                        |
| VAULT_HASH_ALGORITHM             | SHA-256                                    | Hash stored as metadata on GridFS or S3 object metadata             |
| VAULT_DOUBLE_CHECK               | true                                       | Verify content integrity before removing a document from Alfresco |
| VAULT_DELETE_ON_ALFRESCO         | true                                       | Delete the document from Alfresco after archiving                         |
| VAULT_DELETE_NODE_API_ENABLED    | false                                      | Enable the delete node API                                          |
| VAULT_AUTH_CACHE_TTL             | 60s                                        | Cache duration of successful API authentications                    |
| VAULT_ENCRYPT_CONTENT            | false                                      | Enable binary content encryption (S3, GridFS)                       |
| VAULT_ENCRYPT_METADATA           | false                                      | Enable document metadata encryption in MongoDB                          |
| VAULT_URL                        | http://localhost:8200                      | Vault secret engine URI                                             |
| VAULT_ENCRYPTION_KV_MOUNT        | secret                                     | Vault secret engine mount path                                      |
| VAULT_ENCRYPTION_SECRET_PATH     | AlfrescoNodeVault                          | Path of encryption secret in Vault secret engine                    |
| VAULT_ENCRYPTION_SECRET_KEY      | anv.secret                                 | Key name of encryption secret                                       |
| AUDIT_TTL_DAYS                   | 60                                         | Retention of audit entries in days                                  |
| NOTARIZATION_ENABLED             | true                                       | Enable notarization service                                         |
| NOTARIZATION_JOB_ENABLED         | false                                      | Document notarization job switch                                    |
| NOTARIZATION_JOB_CRON_EXPRESSION | 0 0/5 * * * ?                              | Cron expression for notarization job                                |
| INTEGRITY_SWEEP_ENABLED          | false                                      | Enable scheduled integrity sweep job                                |
| INTEGRITY_SWEEP_CRON_EXPRESSION  | 0 0 2 * * ?                                | Cron expression for integrity sweep job                             |
| INTEGRITY_SWEEP_BATCH_SIZE       | 200                                        | Number of notarized documents processed per sweep page                  |
| ETH_RPC_URL                      | http://localhost:8545                      | Ethereum RPC endpoint                                               |
| ETH_PRIVATE_KEY                  | 4f3edf983ac636a65a842ce7c78d9aa7...        | Private key used to sign transactions                               |

## Build

Java and Maven are required. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions.

`mvn package -DskipTests -Dlicense.skip=true`

To create a distribution package, run the build script for your platform:

```bash
./build.sh      # Linux / macOS
```

```bat
build.bat       # Windows
```

The script creates an `anv/` directory containing `anv.jar` together with
`config/` and `log/` folders.

## Testing

For a containerized check of archiving, downloading and restoring on S3 and GridFS, with and without encryption,
run `./smoke-tests.sh` (Linux/macOS) or `smoke-tests.bat` (Windows).
These [end-to-end smoke tests](doc/SmokeTest.md) check content hashes and restore retries in an isolated Docker stack.
The launchers stop the smoke stack afterwards; pass `--keep-running` to leave it active for inspection.

For integration tests, use `vault.sh` (Linux/macOS) or `vault.bat` (Windows) to start the required containers, then

`mvn test`

runs the integration tests, while

`mvn test -Dtests=massive`

runs a load test.

## Docker configuration

The `docker` directory contains a `.env` file used by `docker-compose`.
Copy this file to create your own overrides:

```bash
cp docker/.env docker/.env.local
# edit docker/.env.local and override the desired variables
```

Both `docker-compose` and the helper scripts will automatically pick up
variables from `docker/.env.local` if present.  Use this mechanism to
adjust image versions or swap services when moving from local
development to a production setup. Key variables include:

- `ACS_IMAGE` – Alfresco Content Services image name
- `MONGO_IMAGE` – MongoDB image name
- `HASHICORP_VAULT_IMAGE` – HashiCorp Vault image name

For local development you may rely on the defaults, while in
production you will likely point to hardened images or custom deployments.

## Run

```
cd anv
java -jar anv.jar
```

## REST API

The REST API is versioned under `/api/v1` and requires Basic authentication with Alfresco administrator credentials:

- `/api/v1/vault` to search, retrieve, archive, restore, notarize and re-encrypt documents and to run integrity sweeps
- `/api/v1/audit` to read the audit trail

Requests to the Alfresco REST API for archived documents are transparently served by the vault. The OpenAPI documentation
served at `/swagger` provides the reference for each endpoint, while [RestApi.md](doc/RestApi.md) covers authentication, the
Alfresco proxy and the common conventions.

## Known limitations

- **Enabling encryption** on a vault that already holds data is supported: existing content and metadata stay
  readable and newly archived documents are encrypted, but existing documents are not re-encrypted.
- **Disabling encryption** when encrypted data is already stored is not supported: encrypted content would be returned
  as is and encrypted metadata could not be read.
- **Changing the encryption implementation or its parameters** (`impl`, KDF, salt and nonce/IV lengths) is not supported
  when encrypted data is already stored, since they are not recorded with the data and are required for decryption.
  Use [key rotation](doc/Encryption.md#key-rotation) to change the secret.
- **Advanced search** is available only when metadata encryption is disabled.

## Documentation

See the [Javadoc API documentation](https://saidone75.github.io/alfresco-node-vault/javadoc/).

## License

Copyright (C) 2025-2026 Saidone

Licensed under the GNU General Public License, version 3 or later.
See [LICENSE](LICENSE) for details.
