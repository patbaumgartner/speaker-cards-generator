# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Fixed
- Load the highest-resolution stored portrait when several image variants are available
- Import CFP photos served as `binary/octet-stream` by detecting the image format from the response bytes
- Always render a real speaker image by replacing the Duke fallback with a branded local avatar
- Default to the `vdz` Voxxed Days Zürich Spring profile when no profile is active
- Report invalid CFP event IDs as a clear bad request instead of an opaque server error
- Store imported speaker photos in configurable runtime storage so they work from executable JARs and containers
- Prevent SSRF and unbounded downloads through CFP profile-picture URLs
- Generate banners in-process instead of calling the application back over HTTP
- Fetch talk speakers eagerly for ZIP generation, preventing silently missing talk banners
- Define stable entity identity and null-safe deterministic ordering

### Security
- Move import and filesystem-generation operations from GET to POST
- Stop exposing internal exception details in HTTP responses
- Migrate to maintained OpenHTMLtoPDF 1.1.114 and PDFBox 3.0.8

### Quality
- Add end-to-end banner rendering, ZIP, photo storage, importer, and entity tests
- Raise the JaCoCo line coverage floor from 25% to 60%
- Add the CodeQL workflow referenced by the security policy

### Removed
- Legacy spreadsheet import endpoint, service, tests, and dependencies
- Legacy third-party conference profile and its static logo asset
- Devoxx CFP enable/disable toggle, now redundant with the three Voxxed Days profiles
- Unused demo images that were not referenced by any template or configuration

### Changed
- Normalise displayed portraits with a light accent-color wash using the active event theme, without requiring a re-import
- Scale banner exports to social-friendly 1280×720 and 1080×1080 PNGs
- Refreshed the VDZ, VDT, and VDCern logos from the official voxxeddays.ch logo library
- Renamed base package from `com.fortytwotalents` to `com.fortytwotalents.speakercardsgenerator` to align namespace with the project name
- Renamed Maven `artifactId` and `<name>` from `speaker-cards` to `speaker-cards-generator` to match the repository name
- Used the microphone cyan as the button accent and icon color while keeping solid actions navy for readability
- Refined action icons with bolder filled glyphs, consistent sizing, and decorative icons hidden from assistive technology
- Shown the optional CFP Bearer token as plain text and documented how to obtain it with browser developer tools
- Updated the footer to “Powered by Voxxed Days Switzerland”

### Added
- CI/CD pipeline with GitHub Actions (build, test, CodeQL, release)
- Dependabot configuration for automated dependency updates
- Community health files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`
- Issue templates for bug reports and feature requests
- Pull request template
- Docker multi-stage `Dockerfile` and `compose.yml` for local development
- `.editorconfig` for consistent editor formatting across IDEs
- JaCoCo code-coverage reporting integrated into the Maven build
- Maven Enforcer plugin enforcing Java 21+ and Maven 3.9+
- Spring Java Format plugin (spring-javaformat-maven-plugin) for consistent code style

---

## [1.0.0] – 2026-03-16

### Added
- Migrated full application from Quarkus 3 / Renarde to **Spring Boot 4.0.3**
- Renamed base package from `org.acme` to `com.fortytwotalents`
- Multi-event support via Spring profiles (`vdz`, `vdt`, `vdcern`) for
  Voxxed Days Zürich, Voxxed Days Ticino, and Voxxed Days CERN
- `DevoxxImportService` – import speakers directly from the Devoxx CFP API
  (`https://{eventId}.cfp.dev/api/public/speakers`)
- `BannerGenerationService` – bulk PNG generation for speaker, talk, and
  social-media banners
- `HtmlToPngConverter` – Thymeleaf → HTML → OpenHTMLtoPDF → PDFBox → PNG pipeline
- Thymeleaf templates replacing Qute: `speakerBanner`, `talkBanner`, `speakerSocial`
- Bootstrap 5.3.3 + Bootstrap Icons web UI for the speaker directory
- Spring Data JPA repositories replacing Panache
- Configurable event branding via `app.event.*` properties
- Sample `event-tile.png` placeholder for the conference background image
- Context-load integration test using H2 in-memory database

[Unreleased]: https://github.com/patbaumgartner/speaker-cards-generator/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/patbaumgartner/speaker-cards-generator/releases/tag/v1.0.0
