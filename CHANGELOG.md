# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- Advanced search API (`POST /api/v1/vault/advanced-search`) on node metadata: name with `*` and `?` wildcards, node type, creation and modification dates, mimetype, size, aspects and custom properties, combining values and conditions with `ANY`/`ALL`. Available only when metadata encryption is disabled.
- Delete node API (`DELETE /api/v1/vault/nodes/{nodeId}`), disabled by default (`${VAULT_DELETE_NODE_API_ENABLED:false}`).
- Integrity sweep, scheduled or on demand, with hybrid mode and configurable coverage, batching, persistence of corrupted-node lists and Grafana dashboard.
- Separate flags for content and metadata encryption (`${VAULT_ENCRYPT_CONTENT}` and `${VAULT_ENCRYPT_METADATA}`); non-encrypted metadata is stored as a BSON document.
- Caching of successful API authorizations (`${VAULT_AUTH_CACHE_TTL:60s}`).
- Loki and Promtail in the Docker setup for log aggregation.

### Changed
- **Breaking:** REST APIs are now versioned under `/api/v1` (`/api/v1/vault` and `/api/v1/audit`).
- Reviewed API error responses: invalid parameters, request bodies and pagination return `400` instead of `500`; checking the notarization of a node that is not notarized, or whose hash does not match, returns `409` instead of `500`; all plain-text responses use the `text/plain` content type.
- Node restore is idempotent.
- The content key version is recorded on archived nodes, so key rotation also finds nodes with plain metadata.
- Blocking operations no longer run on the Netty event loop.
- Upgraded to Spring Boot 4.1.1 and Spring Cloud 2025.1.3, Alfresco Java starters 7.3.3, AWS SDK 2.55.5, web3j 6.0.0, SpringDoc WebFlux UI 3.1.1, Bouncy Castle 1.86 and Datafaker 2.7.0.
- Fixed Maven dependency convergence by importing the AWS SDK BOM and pinning transitive versions.
- Docker: Java 25 runtime (Azul Zulu), Moto instead of LocalStack, modular Compose files; the vault image build copies only the build inputs.
- CI: Maven dependency submission replaces OWASP Dependency-Check and GitHub Actions are pinned to commit SHAs.

### Fixed
- Reflected XSS in plain-text API responses.
- S3 content is decrypted only when marked as encrypted, so content stored before enabling encryption can still be read.
- A failed archiving rollback no longer deletes valid vault data.
- GridFS lookup key and duplicate files per node; the content type is stored under the `ct` metadata key.
- S3 user metadata is persisted on content upload.
- Invalid API credentials return `401` instead of `500`.
- Startup with notarization disabled.
- Notarization waits for transactions to be successfully mined.
- Checking whether a node is on the vault no longer decrypts its metadata.

## [0.9.0] - 2026-01-12

### Added
- New `delete-on-alfresco` flag (`${VAULT_DELETE_ON_ALFRESCO:true}`) in configuration.

### Changed
- Updated Spring Boot parent to 3.5.9 and aligned with Spring Cloud 2025.0.1 for the latest platform fixes.
- Refreshed AWS SDK dependencies (S3, transfer manager, and SDK core) to 2.41.1.
- Bumped supporting libraries including Commons IO 2.21.0 and SpringDoc WebFlux UI 2.8.14.
- Updated build and test tooling (Datafaker 2.5.3, JaCoCo Maven Plugin 0.8.14).
- CI now uploads build artifacts using `actions/upload-artifact@v5`.

## [0.0.3] - 2025-07-21

### Added
- Introduced node notarization: records content checksums on the blockchain to ensure tamper-proof integrity.
- `NodeContentInfo` and `NodeContentStream` models with metadata encryption.

### Changed
- Refactored content services to use `NodeContentInfo`.
- Updated dependencies (AWS SDK, Datafaker, Web3j, etc.).
- Added numerous Javadoc comments and documentation improvements.

### Fixed
- Corrected encryption flag parsing in `GridFsContentService`.
- Fixed typos and Javadoc placement issues.

## [0.0.2] - 2025-07-07

### Added
- **Audit trail**: incoming requests and outgoing responses are now audited and stored in a MongoDB time series collection. Entries are accessible via the new `/api/audit` endpoint.
- **S3 storage**: binaries can be stored on Amazon S3 or compatible services as an alternative to GridFS.

### Changed
- Introduced a `ContentService` abstraction with implementations for GridFS and S3.
- Added LocalStack configuration and Docker setup to easily test S3 integration.
- Improved authentication by centralizing logic in `AuthenticationService`.
- Enhanced error handling in REST controllers.
- Updated `vault.sh` script with an optional `novault` start parameter and split monitoring stack.
- Bumped dependencies (Spring Boot 3.5.3, Spring Cloud 2025.0.0, AWS SDK 2.31.x, etc.).
- Added numerous Javadoc comments and updated documentation.

## [0.0.1] - 2025-06-02

### Added
- Archive Alfresco nodes either on-demand or via scheduled jobs.
- Optional strong encryption for both content and metadata.
- Store documents in MongoDB with GridFS.
- Transparent access via REST proxy, even after deletion from Alfresco.
- Supports automatic document restoration if needed.
- Monitoring with Prometheus.
- Vault integration for secure secrets storage.
- Docker setup for easy development.
