# GitHub Copilot Instructions

## Project Overview

**Speaker Cards Generator** is a Spring Boot 4.1.0 web application that generates speaker cards (PNG banners) for Voxxed Days and similar tech conferences. It imports speaker/session data from the Devoxx CFP API or XLSX files, stores them in PostgreSQL, and renders banners via a Thymeleaf → HTML → OpenHTMLtoPDF → PDFBox → PNG pipeline.

## Repository Layout

```
speaker-cards-generator/
  src/main/java/com/fortytwotalents/speakercardsgenerator/
    config/             # Spring configuration classes
    controller/         # Spring MVC @Controller / @RestController
    model/              # JPA @Entity classes
    repository/         # Spring Data JPA repositories
    service/            # Business-logic @Service classes
    util/               # Utility helpers (e.g. TemplateUtils)
  src/main/resources/
    templates/          # Thymeleaf HTML templates
    static/             # Static assets
    application*.properties  # Application and profile-specific properties
  Dockerfile
  pom.xml
.github/
  workflows/            # CI/CD GitHub Actions workflows
  ISSUE_TEMPLATE/       # Issue form templates
  dependabot.yml
```

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Web / Templates | Spring MVC + Thymeleaf |
| Persistence | Spring Data JPA + PostgreSQL 18 |
| Banner rendering | OpenHTMLtoPDF 1.1.73 → PDFBox 3.0.8 → PNG |
| Data import | Apache POI 5.5.1 (XLSX), Devoxx CFP REST API |
| Frontend | Bootstrap 5.3.8 + Bootstrap Icons 1.13.1 (via WebJars) |
| Java version | 21 |
| Tests | JUnit 5 + H2 in-memory (no PostgreSQL required) |
| Code style | Spring Java Format via spring-javaformat-maven-plugin |

## Build & Quality

```bash
# Build, run all tests, JaCoCo coverage, and Spring Java Format check
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 ./mvnw verify

# Auto-format all Java sources with Spring Java Format
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 ./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:apply

# Run the app against a specific event profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz    # Voxxed Days Zürich
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdt    # Voxxed Days Ticino
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdcern # Voxxed Days CERN
./mvnw spring-boot:run -Dspring-boot.run.profiles=baselone # BaselOne
```

All Maven commands are run from the repository root.

## Code Style

- **Spring Java Format** is enforced via the spring-javaformat-maven-plugin at `validate` phase.
- Run `./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:apply` before committing to avoid CI failures.
- Base package: `com.fortytwotalents.speakercardsgenerator`
- Follow standard Spring Boot conventions: `@Service`, `@Repository`, `@Controller`, constructor injection, etc.

## Testing

- Tests use **H2 in-memory** database — no PostgreSQL or Docker needed.
- Integration tests are annotated with `@SpringBootTest`.
- Place tests under `src/test/java/com/fortytwotalents/speakercardsgenerator/`.

## CI/CD

| Workflow | Trigger | Purpose |
|---|---|---|
| `ci.yml` | push / PR to `main`, `develop` | Build, test, JaCoCo, Spring Java Format check |
| `release.yml` | semantic-version tag push | Build Docker image → GHCR + GitHub Release |

## Key Conventions

- Banner generation entry point: `HtmlToPngConverter` service — Thymeleaf renders the HTML template, OpenHTMLtoPDF converts to PDF, PDFBox rasterises to PNG.
- `TemplateUtils` is a `@Component` registered as `${utils.*}` in Thymeleaf templates.
- Devoxx CFP import endpoint: `POST /api/import/devoxx/{eventId}` (e.g. `vdz26`).
- Event-specific configuration lives in `application-{profile}.properties` files.
