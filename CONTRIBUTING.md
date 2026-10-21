# Contributing and Onboarding

This guide explains how to set up the project for local development.

## Prerequisites

Make sure these tools are installed on your workstation:

- **Java 21**
- **Maven**
- **Docker** (with Docker Compose)
- **Git**

## Build

To build the application without running the tests, execute:

```bash
mvn package -DskipTests -Dlicense.skip=true
```

## Start the full environment

Use the helper script to build the Docker images and start all services:

```bash
./vault.sh build_start
```

On Windows the equivalent command is:

```cmd
vault.bat build_start
```

To start all services except the Alfresco Node Vault application container, append `novault` to the
command. This works with both `build_start` and `start`:

```bash
./vault.sh build_start novault
```

The script spins up the following services using Docker Compose:

- **Alfresco Content Services** and **Share**
- **PostgreSQL** database
- **Alfresco Search Services (Solr)**
- **ActiveMQ** broker
- **MongoDB** with **Mongo Express** UI
- **Moto** for AWS S3 object storage
- **Ganache** for Ethereum blockchain simulation
- **HashiCorp Vault** (plus a small provisioner container)
- **Prometheus**, **Grafana** and **Nginx** for monitoring
- **Alfresco Node Vault** application itself

To rebuild and restart the selected development stack, run
`./vault.sh build_start --s3` (or `--gridfs` / `--ozone`).
The legacy `update-vault.sh` and `update-vault.bat` scripts use only the base
Compose file and do not preserve the complete backend configuration.

## Configuration

The main environment variables are listed in the [configuration table](README.md#application-global-config).
See `src/main/resources/application.yml` for the complete application configuration,
and the storage, encryption and concurrency guides for feature-specific settings.

Pull requests are welcome!
