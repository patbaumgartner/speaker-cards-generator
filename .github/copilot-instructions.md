# GitHub Copilot Instructions

## Project Overview

**Speaker Cards Generator** is a Spring Boot 4.0.3 web application that generates speaker cards (PNG banners) for Voxxed Days and similar tech conferences. It imports speaker/session data from the Devoxx CFP API or XLSX/CSV files, stores them in a PostgreSQL database, and renders per-talk banners via a Thymeleaf → HTML → OpenHTMLtoPDF → PDFBox → PNG pipeline.

## Repository Layout

```
speaker-cards/          # Maven module – the runnable Spring Boot application
  src/main/java/com/fortytwotalents/
    config/             # Spring configuration classes
    controller/         # Spring MVC @Controller / @RestController
    model/              # JPA @Entity classes
    repository/         # Spring Data JPA repositories
    service/            # Business-logic @Service classes
    util/               # Utility helpers (e.g. TemplateUtils)
  src/main/resources/
    templates/          # Thymeleaf HTML templates
    static/             # Static assets
    application*.yml    # Application and profile-specific properties
  pom.xml
.github/
  workflows/            # CI/CD GitHub Actions workflows
  ISSUE_TEMPLATE/       # Issue form templates
  dependabot.yml
```

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.3 |
| Web / Templates | Spring MVC + Thymeleaf |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| Banner rendering | OpenHTMLtoPDF 1.0.10 → PDFBox → PNG |
| Data import | Apache POI 5.3.0 (XLSX), Devoxx CFP REST API |
| Frontend | Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 (via WebJars) |
| Java version | 21 |
| Tests | JUnit 5 + H2 in-memory (no PostgreSQL required) |

## Build & Quality

```bash
# Build, run all tests, JaCoCo coverage, and Spotless format check
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 ./mvnw verify

# Auto-format all Java sources with Google Java Format
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 ./mvnw spotless:apply

# Run the app against a specific event profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz    # Voxxed Days Zürich
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdt    # Voxxed Days Ticino
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdcern # Voxxed Days CERN
```

The Maven wrapper is in `speaker-cards/`. Always run Maven commands from that directory.

## Code Style

- **Google Java Format** is enforced via the Spotless Maven plugin at `verify` phase.
- Run `./mvnw spotless:apply` before committing to avoid CI failures.
- Base package: `com.fortytwotalents`
- Follow standard Spring Boot conventions: `@Service`, `@Repository`, `@Controller`, constructor injection, etc.

## Testing

- Tests use **H2 in-memory** database — no PostgreSQL or Docker needed.
- Integration tests are annotated with `@SpringBootTest`.
- Place tests under `src/test/java/com/fortytwotalents/`.

## CI/CD

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | push / PR to `main`, `develop` | Build, test, JaCoCo, Spotless check |
| `codeql.yml` | push / PR + weekly schedule | CodeQL Java security scan |
| `release.yml` | semantic-version tag push | Build Docker image → GHCR + GitHub Release |

## Key Conventions

- Banner generation entry point: `HtmlToPngConverter` service — Thymeleaf renders the HTML template, OpenHTMLtoPDF converts to PDF, PDFBox rasterises to PNG.
- `TemplateUtils` is a `@Component` registered as `${utils.*}` in Thymeleaf templates.
- Devoxx CFP import endpoint: `GET /api/import/devoxx/{eventId}` (e.g. `vdz26`).
- Event-specific configuration lives in `application-{profile}.yml` files.
