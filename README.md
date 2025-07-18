# Alfresco Node Vault

![Vault](images/vault.png)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Javadoc](https://img.shields.io/badge/Javadoc-API-blue.svg)](https://saidone75.github.io/alfresco-node-vault/javadoc/)
![Java CI](https://github.com/saidone75/alfresco-node-vault/actions/workflows/build.yml/badge.svg)
![CodeQL](https://github.com/saidone75/alfresco-node-vault/actions/workflows/codeql.yml/badge.svg)

## The Long-term Document Management Challenge

Over the years, many Alfresco installations inevitably transform into increasingly slow and difficult-to-manage
repositories. The primary cause is the progressive accumulation of millions of nodes that burden the database, slow down
Solr queries, and compromise the overall performance of the system.

This situation is particularly common in organizations where:

- Effective corporate policies for archiving or deleting obsolete documents are missing
- Documents are retained indefinitely due to regulatory or compliance obligations
- Repository sizes grow exponentially year after year
- Users complain about increasingly longer response times in daily operations
- Infrastructure costs continually increase to support ever-larger repositories
- Maintenance operations (backup, indexing, upgrades) become progressively more complex and time-consuming

Repositories with hundreds of millions of nodes can require days for complete reindexing operations, or weeks for
migrations during version upgrades.

Many enterprises find themselves in a difficult position: business units demand that all documents remain accessible,
while IT departments struggle with managing increasingly unresponsive Alfresco instances. The technical debt
accumulates, making each upgrade more complex than the last. Site collections become sluggish, search operations time
out, and what was once a highly efficient ECM solution becomes a burden on daily operations.

## The solution: Alfresco Node Vault

A Spring Boot application designed for long-term storage of Alfresco nodes. Unlike traditional archiving solutions,
Alfresco Node Vault completely removes nodes from Alfresco and its database, freeing resources while maintaining
document accessibility.

The application can archive nodes on-demand through a behavior or using a scheduled job.

Nodes metadata are archived on MongoDB while binaries can be stored either on GridFS or on AWS S3, ensuring efficiency 
and scalability.

The application can also act as a proxy to allow applications using REST APIs to retrieve nodes that no longer exist in
Alfresco, ensuring operational continuity without the need to modify legacy client applications.

Furthermore, documents can also be automatically restored in Alfresco if required.

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
- Fully FOSS, released under an open license
- Scheduled or on-demand archiving
- MongoDB storage for metadata and audit trail 
- GridFS or S3 storage for binaries
- Optional blockchain node notarization to ensure tamper-proof integrity
- Secure and decoupled design that suits zero trust environments
- Focus on [strong encryption](doc/Encryption.md) option for both content and metadata
- Passwords securely stored in a secret engine
- REST API with Alfresco proxy support
- Engineered for flexibility and bespoke integrations
- No installation or changes required on Alfresco
- 100% test coverage
- Prometheus monitoring support for metrics and health checks
- Custom upload and download methods
- Minimal memory footprint
- Robust, enterprise-grade reliability and performance

Pull requests are welcome!

## Architecture

![Architecture](images/architecture.png)

## Application global config

Global configuration is stored in `application.yml` file, the relevant parameters are:

| Parameter/env variable           | Default value                              | Purpose                                                            |
|----------------------------------|--------------------------------------------|--------------------------------------------------------------------|
| ALFRESCO_BASE_PATH               | http://localhost:8080                      | Scheme, host and port of the Alfresco server                       |
| ALFRESCO_USERNAME                | admin                                      | Alfresco user                                                      |
| ALFRESCO_PASSWORD                | admin                                      | Password for the Alfresco user                                     |
| ACTIVE_MQ_URL                    | tcp://localhost:61616                      | ActiveMQ broker URL                                                |
| MONGODB_URL                      | mongodb://localhost:27017                  | MongoDB connection string                                          |
| S3_ENDPOINT                      | http://localhost:4566                      | S3 object storage endpoint                                         |
| S3_KEY                           | test                                       | Key for S3 authentication                                          |
| S3_SECRET                        | test                                       | Secret for S3 authentication                                       |
| EVENT_HANDLER_ENABLED            | false                                      | Event based archive behaviour switch                               |
| ARCHIVING_JOB_ENABLED            | true                                       | Archiving scheduled job switch                                     |
| ARCHIVING_JOB_CRON_EXPRESSION    | 0 0/5 2-6 * * ?                            | Scheduled job cron expression                                      |
| ARCHIVING_JOB_QUERY              | TYPE:'cm:content' AND ASPECT:'anv:archive' | Query for selecting documents to be archived                       |
| VAULT_HASH_ALGORITHM             | SHA-256                                    | Hash stored as metadata on GridFS or S3 object metadata            |
| VAULT_DOUBLE_CHECK               | true                                       | Double check content integrity before removing document on Alfresco |
| VAULT_ENCRYPTION_ENABLED         | true                                       | Enable content encryption                                          |
| VAULT_ENCRYPT_METADATA           | true                                       | Encrypt also metadata                                              |
| VAULT_URL                        | http://localhost:8200                      | Vault secret engine URI                                            |
| VAULT_ENCRYPTION_KV_MOUNT        | secret                                     | Vault secret engine mount path                                     |
| VAULT_ENCRYPTION_SECRET_PATH     | AlfrescoNodeVault                          | Path of encryption secret in Vault secret engine                   |
| VAULT_ENCRYPTION_SECRET_KEY      | anv.secret                                 | Key name of encryption secret                                      |
| AUDIT_ENABLED                    | false                                      | Enable web request auditing                                        |
| NOTARIZATION_ENABLED             | true                                       | Enable notarization service                                        |
| NOTARIZATION_JOB_ENABLED         | false                                      | Document notarization job switch                                   |
| NOTARIZATION_JOB_CRON_EXPRESSION | 0 0/30 * * * ?                             | Cron expression for notarization job                               |
| ETH_RPC_URL                      | http://localhost:8545                      | Ethereum RPC endpoint                                              |
| ETH_PRIVATE_KEY                  | 4f3edf983ac636a65a842ce7c78d9aa7...        | Private key used to sign transactions                              |

## Build

Java and Maven required. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions.

`mvn package -DskipTests -Dlicense.skip=true`

To create a distribution package execute the build script for your platform:

```bash
./build.sh      # Linux / macOS
```

```bat
build.bat       # Windows
```

The script creates an `anv/` directory containing `anv.jar` together with
`config/` and `log/` folders.

## Testing

For integration tests you can use `vault.(sh|bat)` script to start all needed containers, then

`mvn test`

will run the integration tests, and

`mvn test -Dtests=massive`

will run a load test.

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

## Documentation

Javadoc is available at the following address: https://saidone75.github.io/alfresco-node-vault/javadoc/

## License

Copyright (c) 2025 Saidone

Distributed under the GNU General Public License v3.0
