# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Changed
- Updated Spring Boot parent to 3.5.9 and aligned with Spring Cloud 2025.0.1 for the latest platform fixes.
- Refreshed AWS SDK dependencies (S3, transfer manager, and SDK core) to 2.41.1.
- Bumped supporting libraries including Commons IO 2.21.0 and SpringDoc WebFlux UI 2.8.14.
- Updated build and test tooling (Datafaker 2.5.3, JaCoCo Maven Plugin 0.8.14).
- CI now uploads build artifacts using `actions/upload-artifact@v5`.

## [0.0.3] - 2025-07-21

### Added
- Introduced node notarization: records content checksums on the blockchain to ensure tamper-proof integrity.
- `NodeContentInfo` and `NodeContentStream` models with encryption metadata.

### Changed
- Refactored content services to use `NodeContentInfo`.
- Updated dependencies (AWS SDK, Datafaker, Web3j, etc.).
- Added numerous Javadoc comments and documentation improvements.

### Fixed
- Corrected encryption flag parsing in `GridFsContentService`.
- Fixed typos and Javadoc placement issues.

## [0.2.0] - 2025-07-07

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
