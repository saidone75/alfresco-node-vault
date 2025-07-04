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

To start all services except the Vault container, append `novault` to the
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
- **LocalStack** for AWS S3 object storage
- **HashiCorp Vault** (plus a small provisioner container)
- **Prometheus**, **Grafana** and **Nginx** for monitoring
- **Alfresco Node Vault** application itself

If you need to rebuild only the `anv-vault` container without
restarting the entire stack, use the helper scripts:

```bash
./update-vault.sh
```

On Windows run `update-vault.bat` instead.

## Configuration

All environment variables are listed in the [configuration table](README.md#application-global-config) in the README.

Pull requests are welcome!
