# End-to-end smoke tests

The suite exercises Alfresco, the vault application and storage services together to check the essential
workflows. It is an end-to-end integration suite used as a smoke check, rather than only a startup check.

With Docker Compose 2.24.4 or later, launch the environment and tests using:

```bash
./smoke-tests.sh
```

On Windows:

```cmd
smoke-tests.bat
```

Both scripts resolve paths relative to their own location, validate the Compose configuration, build the images,
start the required services and run the suite. They return the runner's exit code, or the configuration error code
if validation fails. By default they stop all containers in the smoke stack after the run, including when the
tests fail. Volumes, logs and the JSON report are retained. If the tests pass but stopping the stack fails, the
launcher returns the stop command's error code.

S3 is the default (two cases). Select a backend with `--gridfs`, `--s3` or `--ozone`,
or use `--all` for all six cases. These flags are mutually exclusive. For example:

```bash
./smoke-tests.sh --all
./smoke-tests.sh --gridfs
./smoke-tests.sh --s3
./smoke-tests.sh --ozone
```

The same flags work with `smoke-tests.bat` on Windows. Each backend overlay selects its two
application instances; shared infrastructure is still started, including Moto and Ozone provisioning.

To leave the services running for inspection, use `./smoke-tests.sh --keep-running` or
`smoke-tests.bat --keep-running` on Windows.

To run all backends directly without automatic shutdown, use this Docker command from the repository root:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml run --build --rm smoke
```

The application is built inside Docker and the runner uses Clojure on Babashka, with its built-in HTTP client
and JSON support. No additional Clojure dependencies are downloaded at runtime. The first run downloads
images and Maven dependencies and may take several minutes. Allow enough memory for Alfresco, Solr, Ozone and six
application instances (approximately 12 GiB available to Docker is a useful starting point).

The stack uses its own `anv-smoke` project, container names, managed volumes, databases and S3 buckets. It does not
publish host ports or use the external volumes belonging to the regular development stack. It reuses the service
definitions and image versions from the existing Docker configuration. Run one smoke stack at a time.

## Checks

The runner waits for Alfresco, initializes test-only Vault secrets and Moto buckets, then tests the selected cases from this matrix:

| Storage | Content encryption | Metadata encryption |
|---------|--------------------|---------------------|
| S3      | Disabled           | Disabled            |
| GridFS  | Disabled           | Disabled            |
| S3      | Enabled            | Enabled             |
| GridFS  | Enabled            | Enabled             |
| Ozone RPC | Disabled | Disabled |
| Ozone RPC | Enabled | Enabled |

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

Exit code `0` means all selected cases and their cleanup passed. Any failed check returns a nonzero code. Successful
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

With `--keep-running`, services remain running after the runner exits for inspection. Otherwise the launchers
stop them automatically. After collecting the report, remove the stopped containers and test data (or stop and
remove them if they are still running):

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml down -v
```

This removes the smoke stack's volumes, including the report. Its Vault service runs in development mode, so
its keys are lost when the service stops or its container is recreated. Remove the smoke data volumes before
starting a fresh stack after stopping Vault.

## Scope

These are end-to-end smoke tests, not a full regression suite, load test or backup recovery test. They exercise
the default encryption provider and KDF, with content and metadata encryption enabled together. They do not cover mixed encryption
settings, key rotation, notarization, interrupted restores or application restarts.

Docker Desktop on Windows runs these Linux containers; a separate native Windows run is needed to validate NTFS
ACLs. Backup recovery must also be verified separately, including recovery of the required Vault key versions.

## Native Ozone cases

With `--all`, the suite includes Ozone through the native Java RPC client alongside S3 and GridFS.
All six cases share Alfresco, Mongo and Vault, and write a single JSON report.
The stack starts OM, SCM and one Datanode, without an S3 Gateway. Provisioning waits
for an operational data path before the applications start. Server image settings
come from `OZONE_IMAGE` and `OZONE_VERSION` in `docker/.env`.
This is a functional test with replication ONE, not a load or failover test.

To run only the two Ozone cases, use `./smoke-tests.sh --ozone` or
`smoke-tests.bat --ozone`. Both accept `--keep-running` in either order.
This selects an overlay on the same `anv-smoke` stack and retains the same volumes
and report location. Run one suite at a time; remove the data volumes after stopping Vault
before starting a fresh stack, as described above.

The equivalent direct command is:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml -f docker/smoke/compose-ozone.yml run --build --rm smoke
```

For the regular development variant, see [Ozone.md](Ozone.md#docker-development-stack).
