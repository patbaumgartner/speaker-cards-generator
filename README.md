# Speaker Cards Generator

[![CI](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/ci.yml)
[![CodeQL](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/codeql.yml/badge.svg)](https://github.com/patbaumgartner/speaker-cards-generator/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

> Generate professional **speaker and talk banner images** (PNG) for conference events such as
> **Voxxed Days Zürich**, **Voxxed Days Ticino**, and **Voxxed Days CERN**.

---

## ✨ Features

- 🎨 **Banner generation** – speaker card (16:9), talk card (16:9), and social card (1:1 square)
- 📥 **Speaker import** – from [Sessionize](https://sessionize.com/) XLSX export or the [Devoxx CFP API](https://m.devoxx.com)
- 🎛️ **Multi-event profiles** – switch between events by activating a Spring profile
- 🌐 **Web UI** – Bootstrap-based speaker directory with one-click download
- 🐳 **Docker-ready** – `Dockerfile` + `docker-compose.yml` included

---

## 🚀 Quick Start

### With Docker Compose (recommended)

```shell
# Start PostgreSQL + the application
docker compose up -d
```

Open <http://localhost:8080> in your browser.

### Without Docker

Prerequisites: Java 21+, Maven 3.9+, PostgreSQL 15+

```shell
cd speaker-cards
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz
```

Full setup instructions are in [`speaker-cards/README.md`](speaker-cards/README.md).

---

## 🎯 Conference Profiles

| Profile | Event |
|---------|-------|
| `vdz` | Voxxed Days Zürich |
| `vdt` | Voxxed Days Ticino |
| `vdcern` | Voxxed Days CERN |

```shell
# Activate a profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdt
```

Adding support for a new event takes less than a minute –
see [Adding a New Event](speaker-cards/README.md#adding-a-new-event).

---

## 📥 Import Speakers

```shell
# From the Devoxx CFP API
curl http://localhost:8080/api/import/devoxx/vdz26

# From a Sessionize XLSX export
curl http://localhost:8080/api/import/csv
```

---

## 🖼️ Generate Banners

```shell
# Generate all banners and save to ./speaker-banners/
curl "http://localhost:8080/api/banners/generate-all?outputDir=./speaker-banners"
```

---

## 🏗️ Project Structure

```
speaker-cards-generator/
├── .github/
│   ├── workflows/       – CI, CodeQL, Release pipelines
│   ├── ISSUE_TEMPLATE/  – Bug report & feature request forms
│   └── pull_request_template.md
├── speaker-cards/       – Spring Boot application module
│   ├── src/
│   ├── Dockerfile
│   └── README.md        – Full developer documentation
├── docker-compose.yml   – Local development stack
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
