# Docker smoke test

Run this from the repository root with Docker Compose 2.24.4 or later:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml run --build --rm smoke
```

The application is built inside Docker and the runner uses Clojure on Babashka, with its built-in HTTP client
and JSON support. No additional Clojure dependencies are downloaded at runtime. The first run downloads
images and Maven dependencies and may take several minutes. Allow enough memory for Alfresco, Solr and four
application instances (approximately 8 GiB available to Docker is a useful starting point).

The stack uses its own `anv-smoke` project, container names, managed volumes, databases and S3 buckets. It does not
publish host ports or use the external volumes belonging to the regular development stack. It reuses the service
definitions and image versions from the existing Docker configuration. Run one smoke stack at a time.

## Checks

The runner waits for Alfresco, initializes test-only Vault secrets and Moto buckets, then tests these cases:

| Storage | Content encryption | Metadata encryption |
|---------|--------------------|---------------------|
| S3      | Disabled           | Disabled            |
| GridFS  | Disabled           | Disabled            |
| S3      | Enabled            | Enabled             |
| GridFS  | Enabled            | Enabled             |

Each case uses a new Alfresco folder containing an empty file, the repository's sample PDF with a non-ASCII
filename, and a deterministic binary file (8 MiB by default). For every file it verifies:

1. The source upload contains the expected bytes.
2. Archiving succeeds and the source node is removed from Alfresco.
3. The vault returns the expected node ID and filename.
4. Downloaded vault content has the same size and SHA-256 hash as the source.
5. Restoring with permissions succeeds and the restored content has the same size and SHA-256 hash.
6. A second restore returns the same node ID, and the folder contains no extra nodes.

The runner also checks that an unauthenticated vault request returns `401`. At the end of each case it removes
only the vault records and Alfresco folder created by that case. Cleanup errors fail the smoke test. No global
database or bucket purge is performed by the runner.

Exit code `0` means all four cases and their cleanup passed. Any failed check returns a nonzero code. Successful
checks print `PASS` lines with file size and hash; a JSON report includes the result and node IDs for each case.

## Results and diagnostics

The JSON report is stored in the stack's `reports` volume. To copy it to the host after a run:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml run --rm --no-deps --entrypoint cat smoke /reports/report.json > smoke-report.json
```

Inspect application and infrastructure logs with:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml logs --tail=200
```

To change file size or startup timeout, set `SMOKE_LARGE_FILE_MB` (1–256) or `SMOKE_WAIT_SECONDS` (default 900)
in the host environment before running Compose. For example, in PowerShell:

```powershell
$env:SMOKE_LARGE_FILE_MB = "32"
```

The services remain running after the runner exits, so logs and storage can be inspected. After collecting the
report, stop the stack and remove its test data:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml down -v
```

This removes the smoke stack's volumes, including the report. Its Vault service runs in development mode, so
its keys are lost when the service stops or its container is recreated. Remove the smoke data volumes before
starting a fresh stack after stopping Vault.

## Scope

This is a release smoke test, not a load test or a backup recovery test. It exercises the default encryption
provider and KDF, with content and metadata encryption enabled together. It does not cover mixed encryption
settings, key rotation, notarization, interrupted restores or application restarts.

Docker Desktop on Windows runs these Linux containers; a separate native Windows run is needed to validate NTFS
ACLs. Backup recovery must also be verified separately, including recovery of the required Vault key versions.
