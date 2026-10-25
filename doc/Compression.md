# Content compression

Set `VAULT_COMPRESS_CONTENT=true` to compress new binary content with Zstandard on S3,
GridFS and Ozone. The application default is `false`; development Docker and smoke tests enable it.
`VAULT_COMPRESSION_LEVEL` selects a level from
1 to 9 (default `3`); higher levels trade CPU time for compression ratio.

Compression is streaming, before optional encryption, without an additional staging file.
Download, restore, checksum verification and notarization use the original bytes.
Node metadata is not compressed, preserving BSON searches when metadata encryption is disabled.

Stored object metadata records `compression=zstd-v1` and `original-length`. Reads use these
fields independently of the current write flag. Objects without a compression field remain
readable as before; file signatures alone never trigger decompression. Decoding checks the
Zstandard checksum and original length and limits the decoder window to 8 MiB.
Read to EOF to detect truncation, checksum errors and encrypted payload authentication failures.

Disabling compression affects subsequent writes only; existing compressed content stays readable.
Changing the flag does not migrate archived objects. A later re-encryption rewrites content using
the current compression setting.
Back up content storage metadata with the binaries.

Every binary written while enabled is compressed, including already-compressed formats;
PDF, Office, image and ZIP files may save little space or grow slightly. Measure representative
documents before enabling it for an entire archive.

For a locally started application, export the variables or set
`application.service.vault.compression.enabled` and `.level` in application configuration.
For development Docker, set the variables in `docker/.env` and rebuild/recreate the application
container with `./update-vault.sh` (or `update-vault.bat`) if it is running.

The single-node and bulk compression APIs still return `501 Not Implemented`.
