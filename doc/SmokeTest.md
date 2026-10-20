# End-to-end smoke tests

Requires Docker Compose 2.24.4+. Run from the repository root:

```bash
./smoke-tests.sh            # S3 (default)
./smoke-tests.sh --s3
./smoke-tests.sh --gridfs
./smoke-tests.sh --ozone
./smoke-tests.sh --all      # All three backends
```

On Windows, use `smoke-tests.bat` with the same flags. Select one backend flag;
add `--keep-running` to leave services running for inspection.

The launcher builds the application, starts the required services and stops them
after the run, including on failure. Data and reports are retained. Exit code `0`
means all selected checks, cleanup and shutdown succeeded.

The isolated `anv-smoke` stack shares Alfresco, Mongo and Vault across backends,
with separate storage for each case, no published ports and no development volumes.
Run one suite at a time. The first run may take several minutes; `--all` needs
roughly 12 GiB available to Docker.

## Checks

Every backend runs two cases: content and metadata encryption both disabled,
then both enabled. Each uses an empty file, a PDF with a non-ASCII filename
and a deterministic binary file (8 MiB by default).

The suite checks upload integrity, archiving and source deletion, node metadata,
download and restore sizes and SHA-256 hashes, restore permissions, and repeat
restores without duplicates. Unauthenticated requests must return `401`.
Only records and folders created by each case are removed; cleanup failures fail the suite.

Set `SMOKE_LARGE_FILE_MB` (1–256) or `SMOKE_WAIT_SECONDS` (default 900) in the host
environment to change the binary size or startup timeout.

## Results and cleanup

These commands apply to every backend. Export the JSON report:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml run --rm --no-deps --entrypoint cat smoke /reports/report.json > smoke-report.json
```

Inspect logs:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml logs --tail=200
```

Remove containers and test data after collecting the report:

```console
docker compose --env-file docker/.env -f docker/smoke/compose.yml down -v
```

This also deletes the report. Remove these volumes before a fresh run after stopping
Vault: its development-mode encryption keys are lost when the service stops.

The suite covers essential workflows, not load, failover or backup recovery.
Mixed encryption settings, key rotation, notarization and interrupted restores are outside its scope.
