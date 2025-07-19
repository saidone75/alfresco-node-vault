# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.0.3] - 2025-07-18

### Added
- Node notarization endpoint and scheduled job backed by Ethereum.
- Automatic generation of Ethereum credentials and Ganache docker setup.
- `NodeContentInfo` and `NodeContentStream` models with encryption metadata.
- Metadata support for S3 content storage and notarization check API.

### Changed
- Replaced `EthereumService` with generic `NotarizationService`.
- Refactored content services to use `NodeContentInfo`.
- Renamed notarization classes from Document to Node for consistency.
- Increased notarization job frequency to run every 5 minutes.
- Updated dependencies (AWS SDK, Datafaker, Web3j, etc.).
- Improved Ethereum transaction management and error handling.
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
