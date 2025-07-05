# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [0.1.0] - 2025-07-05

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

