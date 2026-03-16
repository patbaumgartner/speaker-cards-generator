# Speaker Cards Generator

A **Spring Boot 4.0.3** application that generates professional speaker and talk
banner images (PNG) for conference events such as:

- **Voxxed Days Zürich** (`vdz`)
- **Voxxed Days Ticino** (`vdt`)
- **Voxxed Days CERN** (`vdcern`)

Speakers can be imported either from a **Sessionize XLSX export** or directly
from the **Devoxx CFP API** (`https://m.devoxx.com/events/{eventId}/speakers`).

---

## Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| PostgreSQL | 15+ |

### 1. Set up the database

```sql
CREATE DATABASE speakercards;
CREATE USER speakercards WITH PASSWORD 'speakercards';
GRANT ALL PRIVILEGES ON DATABASE speakercards TO speakercards;
```

### 2. Run in development mode

```shell
cd speaker-cards
./mvnw spring-boot:run
```

The application starts at <http://localhost:8080>.

### 3. Select a conference profile

Pass the Spring profile matching the target event:

```shell
# Voxxed Days Zürich (default)
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdz

# Voxxed Days Ticino
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdt

# Voxxed Days CERN
./mvnw spring-boot:run -Dspring-boot.run.profiles=vdcern
```

---

## Importing Speakers

### From Devoxx CFP API

Fetch speakers for the configured event directly from the Devoxx API:

```shell
# Uses app.devoxx.api.event-id from the active profile
curl "http://localhost:8080/api/import/devoxx"

# Fetch speakers for a specific event ID
curl "http://localhost:8080/api/import/devoxx/vdz26"
curl "http://localhost:8080/api/import/devoxx/vdt26"
curl "http://localhost:8080/api/import/devoxx/vdcern26"
```

### From Sessionize XLSX Export

Place the XLSX file (e.g. `SelectedWithSchedule.xlsx`) in the application
working directory, then:

```shell
# Import from default file (SelectedWithSchedule.xlsx)
curl "http://localhost:8080/api/import/csv"

# Import from a custom path
curl "http://localhost:8080/api/import/csv/path/to/SelectedWithSchedule.xlsx"
```

---

## Generating Banners

### In the browser

Open <http://localhost:8080> to browse the speaker list.  Each row has
**View** and **Download** buttons for the speaker and talk banners.

### Via API

```shell
# Generate all banners (speaker + talk + social) and save to ./speaker-banners/
curl "http://localhost:8080/api/banners/generate-all?outputDir=./speaker-banners"

# Generate banners for specific speaker IDs
curl -X POST "http://localhost:8080/api/banners/generate-speakers" \
  -H "Content-Type: application/json" \
  -d '["<speakerUUID1>", "<speakerUUID2>"]'
```

### Direct PNG URLs

| Banner type | URL |
|---|---|
| Speaker banner (HTML preview) | `/speaker-banner/{uuid}` |
| Speaker banner (PNG) | `/speaker-banner/{uuid}.png` |
| Speaker social banner (PNG, 1080×1080) | `/speaker-social/{uuid}.png` |
| Talk banner (HTML preview) | `/talk-banner/{id}` |
| Talk banner (PNG) | `/talk-banner/{id}.png` |
| Speaker photo | `/speaker-photo/{uuid}` |

---

## Configuration

All settings live in `src/main/resources/application.properties`.
Event-specific overrides go in the corresponding profile file.

| Property | Default | Description |
|---|---|---|
| `app.event.name` | `Voxxed Days Zürich` | Full event name shown on banners |
| `app.event.short-name` | `VDZ '26` | Short name shown on the banner badge |
| `app.event.url` | `https://voxxeddays.com/zurich/` | Conference website URL |
| `app.event.logo-file` | `event-tile.png` | Background image in `static/images/` |
| `app.devoxx.api.base-url` | `https://m.devoxx.com` | Devoxx API base URL |
| `app.devoxx.api.event-id` | `vdz26` | Devoxx event identifier |

### Event Image Tile

Replace `src/main/resources/static/images/event-tile.png` with the official
event banner/tile image provided by the conference organisers.  The image is
used as the background for all generated speaker and talk cards.

---

## Project Structure

```
com.fortytwotalents
├── SpeakerCardsApplication.java      – Spring Boot entry point
├── config/
│   ├── EventConfig.java              – Event branding (name, URL, logo)
│   └── DevoxxApiConfig.java          – Devoxx API connection settings
├── controller/
│   ├── BannerController.java         – Banner HTML preview & PNG endpoints
│   ├── ImportController.java         – XLSX / Devoxx import endpoints
│   └── WebsiteController.java        – Speaker directory web UI
├── model/
│   ├── Speaker.java                  – Speaker JPA entity
│   ├── Talk.java                     – Talk / session JPA entity
│   └── TalkWithoutSlotDTO.java       – Lightweight talk DTO
├── repository/
│   ├── SpeakerRepository.java        – Spring Data JPA repository
│   └── TalkRepository.java           – Spring Data JPA repository
├── service/
│   ├── BannerGenerationService.java  – Bulk banner generation
│   ├── BannerGenerationResult.java   – Generation result model
│   ├── DevoxxImportService.java      – Import from Devoxx CFP API
│   └── ImportFromCSVService.java     – Import from Sessionize XLSX
└── util/
    ├── HtmlToPngConverter.java       – Thymeleaf → HTML → OpenHTMLtoPDF → PNG
    └── TemplateUtils.java            – Date/time formatting helpers
```

---

## Building and Running

### Package as JAR

```shell
./mvnw package
java -jar target/speaker-cards-1.0.0-SNAPSHOT.jar
```

### Run tests

```shell
./mvnw test
```

Tests use an **in-memory H2** database – no PostgreSQL required.

---

## Adding a New Event

1. Create `src/main/resources/application-{id}.properties` (copy an existing profile).
2. Set `app.event.*` and `app.devoxx.api.event-id`.
3. Replace `event-tile.png` (or point to a new file via `app.event.logo-file`).
4. Start with `--spring.profiles.active={id}`.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.3 |
| Web | Spring MVC |
| Templates | Thymeleaf 3 |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL (H2 for tests) |
| Banner rendering | OpenHTMLtoPDF 1.0.10 + Apache PDFBox |
| XLSX import | Apache POI 5 |
| UI components | Bootstrap 5.3.3 + Bootstrap Icons |
| Java | 21 |
