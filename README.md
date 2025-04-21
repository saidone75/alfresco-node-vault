# Alfresco Node Vault

_"Who controls the past controls the future: who controls the present controls the past."_


[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Java CI](https://github.com/saidone75/alfresco-node-vault/actions/workflows/build.yml/badge.svg)
![CodeQL](https://github.com/saidone75/alfresco-node-vault/actions/workflows/codeql.yml/badge.svg)

A Spring Boot application designed for long-term storage of Alfresco nodes that no longer need to be kept online,
helping to reduce the load on Alfresco's database and Solr indices.

The application can archive nodes on-demand through a behavior or using a scheduled job.

Nodes and binaries are archived on MongoDB with GridFS.

The application can also act as a proxy to allow applications using REST APIs to retrieve nodes that no longer exist in
Alfresco.

Pull requests are welcome!

## Build
Java and Maven required

`mvn package -DskipTests -Dlicense.skip=true`

look at the `build.sh` or `build.bat` scripts for creating a convenient distribution package.
## Application global config
Global configuration is stored in `application.yml` file, the relevant parameters are:

| Parameter/env variable | Default value         | Purpose                                                                        |
|------------------------|-----------------------|--------------------------------------------------------------------------------|
| ALFRESCO_BASE_PATH     | http://localhost:8080 | scheme, host and port of the Alfresco server                                   |
| ALFRESCO_USERNAME      | admin                 | Alfresco user                                                                  |
| ALFRESCO_PASSORD       | admin                 | password for the Alfresco user                                                 |

## Testing
For integration tests just change configuration and point it to an existing Alfresco installation, or use `alfresco.(sh|bat)` script to start it with docker.
## Run
`$ java -jar anv.jar`
## License
Copyright (c) 2025 Saidone

Distributed under the GNU General Public License v3.0
