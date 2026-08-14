# Speaker Cards Generator

[![CI](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)

> Generate professional **speaker and talk banner images** (PNG) for conference events such as
> **Voxxed Days Zürich**, **Voxxed Days Ticino**, **Voxxed Days CERN**, and **BaselOne**.

---

## 🖼️ Preview

**Speaker card** (1920 × 1080 px)

![Speaker card example](docs/images/speaker-card-example.png)

**Talk card** (1920 × 1080 px)

![Talk card example](docs/images/talk-card-example.png)

---

## ✨ Features

- 🎨 **Banner generation** – speaker card (16:9), talk card (16:9), and social card (1:1 square)
- 📥 **Speaker import** – from [Sessionize](https://sessionize.com/) XLSX export or the [Devoxx CFP API](https://m.devoxx.com)
- 🎛️ **Multi-event profiles** – switch between events by activating a Spring profile
- 🌐 **Web UI** – Bootstrap-based speaker directory with one-click download
- 🐳 **Docker-ready** – `Dockerfile` + `compose.yml` included
- 🔄 **Auto-start database** – Spring Boot Docker Compose starts PostgreSQL automatically during local development

---

## 🚀 Quick Start

### With Docker Compose – full-stack (recommended for production-like setup)

```shell
# Start PostgreSQL + the application in Docker
docker compose --profile deploy up -d
```

Open <http://localhost:8080> in your browser.

### Local development (database auto-starts)

Prerequisites: Java 21+, Maven 3.9+, Docker

Spring Boot automatically starts PostgreSQL via Docker Compose (`compose.yml`) when you run:

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz
```

No manual `docker compose up` step is needed — the database container is started and stopped
together with the application.

---

## 🎯 Conference Profiles

| Profile | Event |
|---------|-------|
| `vdz` | Voxxed Days Zürich |
| `vdt` | Voxxed Days Ticino |
| `vdcern` | Voxxed Days CERN |
| `baselone` | BaselOne |

```shell
# Activate a profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdt

# BaselOne profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=baselone
```

Adding support for a new event takes less than a minute –
create a new `application-{profile}.properties` file with the event-specific branding.

---

## 📥 Import Speakers

```shell
# From the Devoxx CFP API
curl -X POST http://localhost:8080/api/import/devoxx/vdz26

# From a Sessionize XLSX export
curl -X POST http://localhost:8080/api/import/csv
```

---

## 🖼️ Generate Banners

```shell
# Generate all banners and save to ./speaker-banners/
curl -X POST "http://localhost:8080/api/banners/generate-all?outputDir=./speaker-banners"
```

---

## 🏗️ Project Structure

```
speaker-cards-generator/
├── .github/
│   ├── workflows/       – CI, CodeQL, Release pipelines
│   ├── ISSUE_TEMPLATE/  – Bug report & feature request forms
│   └── pull_request_template.md
├── src/                 – Spring Boot application source
│   ├── main/java/com/fortytwotalents/speakercardsgenerator/
│   │   ├── config/      – Spring configuration classes
│   │   ├── controller/  – Spring MVC controllers
│   │   ├── model/       – JPA entity classes
│   │   ├── repository/  – Spring Data JPA repositories
│   │   ├── service/     – Business-logic services
│   │   └── util/        – Utility helpers
│   └── main/resources/
│       ├── templates/   – Thymeleaf HTML templates
│       ├── static/      – Static assets (CSS, fonts, images)
│       └── application*.properties – App and profile-specific config
├── compose.yml          – Docker Compose (db auto-start for dev; full-stack with --profile deploy)
├── Dockerfile           – Multi-stage Docker build
├── pom.xml
├── CHANGELOG.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
└── SECURITY.md
```

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before
opening a pull request.

---

## 📜 License

This project is licensed under the [Apache License 2.0](LICENSE).
