# Alfresco Node Vault

<img width="25%" src="vault.svg">

_"Who controls the past controls the future: who controls the present controls the past."_

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
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

## The Solution: Alfresco Node Vault

A Spring Boot application designed for long-term storage of Alfresco nodes. Unlike traditional archiving solutions,
Alfresco Node Vault completely removes nodes from Alfresco and its database, freeing resources while maintaining
document accessibility.

The application can archive nodes on-demand through a behavior or using a scheduled job.

Nodes and binaries are archived on MongoDB with GridFS, ensuring efficient and scalable storage.

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

- Clean and elegant code architecture
- Minimal memory footprint
- On-demand or scheduled archiving
- MongoDB storage with GridFS
- REST API proxy capabilities

Pull requests are welcome!

## Build

Java and Maven required

`mvn package -DskipTests -Dlicense.skip=true`

look at the `build.sh` or `build.bat` scripts for creating a convenient distribution package.

## Application global config

Global configuration is stored in `application.yml` file, the relevant parameters are:

| Parameter/env variable        | Default value                              | Purpose                                      |
|-------------------------------|--------------------------------------------|----------------------------------------------|
| ALFRESCO_BASE_PATH            | http://localhost:8080                      | Scheme, host and port of the Alfresco server |
| ALFRESCO_USERNAME             | admin                                      | Alfresco user                                |
| ALFRESCO_PASSWORD             | admin                                      | password for the Alfresco user               |
| ACTIVE_MQ_URL                 | tcp://localhost:61616                      | ActiveMQ broker URL                          |
| EVENT_HANDLER_ENABLED         | false                                      | Event based archive behaviour switch         |
| ARCHIVING_JOB_ENABLED         | true                                       | Archiving scheduled job switch               |
| ARCHIVING_JOB_CRON_EXPRESSION | 0 0/5 2-6 * * ?                            | Scheduled job cron expression                |
| ARCHIVING_JOB_QUERY           | TYPE:'cm:content' AND ASPECT:'anv:archive' | Query for selecting documents to be archived |

## Testing

For integration tests just change configuration and point it to an existing Alfresco installation, or use
`alfresco.(sh|bat)` script to start it with docker.

## Run

`$ java -jar anv.jar`

## License

Copyright (c) 2025 Saidone

Distributed under the GNU General Public License v3.0
