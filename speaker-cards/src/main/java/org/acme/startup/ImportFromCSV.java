package org.acme.startup;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ImportFromCSV {

    private static final Logger LOG = Logger.getLogger(ImportFromCSV.class);

    // XLSX column indices from SelectedWithSchedule.xlsx
    private static final int COL_SESSION_ID = 0; // Session Id
    private static final int COL_TITLE = 1; // Title
    private static final int COL_DESCRIPTION = 2; // Description
    private static final int COL_SCHEDULED_AT = 8; // Scheduled At
    private static final int COL_SCHEDULED_DURATION = 9; // Scheduled Duration
    private static final int COL_LIVE_LINK = 10; // Live Link
    private static final int COL_SPEAKER_ID = 12; // Speaker Id
    private static final int COL_FIRST_NAME = 13; // FirstName
    private static final int COL_LAST_NAME = 14; // LastName
    private static final int COL_TAG_LINE = 16; // TagLine
    private static final int COL_BIO = 17; // Bio
    private static final int COL_PROFILE_PICTURE = 22; // Profile Picture

    // EST timezone (America/New_York)
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    // CET timezone (Europe/Paris)
    private static final ZoneId CET_ZONE = ZoneId.of("Europe/Paris");

    // Time format for database: "HH:mm"
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    // Date format for database: "yyyy-MM-dd"
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    void onStart(@Observes StartupEvent ev) {
        LOG.info("XLSX Import startup observer initialized. Import will run when explicitly triggered.");
    }

    @Transactional
    public void importFromCSV(String xlsxFilePath) {
        LOG.infof("Starting XLSX import from: %s", xlsxFilePath);

        Path path = Paths.get(xlsxFilePath);
        if (!Files.exists(path)) {
            LOG.errorf("XLSX file not found: %s", xlsxFilePath);
            return;
        }

        try (FileInputStream fis = new FileInputStream(xlsxFilePath);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                LOG.infof("XLSX header found with %d columns", headerRow.getLastCellNum());
            }

            int rowNumber = 0;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNumber++;

                try {
                    String[] fields = extractRowData(row);
                    processRow(fields);
                } catch (Exception e) {
                    LOG.errorf(e, "Error processing row %d: %s", rowNumber, e.getMessage());
                }
            }

            LOG.infof("XLSX import completed. Processed %d rows", rowNumber);
        } catch (Exception e) {
            LOG.errorf(e, "Error reading XLSX file: %s", xlsxFilePath);
        }
    }

    private String[] extractRowData(Row row) {
        List<String> fields = new ArrayList<>();
        // We need at least 23 columns (0-22) for Profile Picture
        int lastColumn = Math.max(row.getLastCellNum(), 23);

        for (int i = 0; i < lastColumn; i++) {
            Cell cell = row.getCell(i);
            fields.add(getCellValueAsString(cell));
        }

        return fields.toArray(new String[0]);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Return ISO format date-time string
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private void processRow(String[] fields) {
        Talk talk = createOrUpdateTalk(fields);
        Speaker speaker = createOrUpdateSpeaker(fields);

        // Establish bidirectional relationship
        if (speaker != null && talk != null) {
            if (!talk.speakers.contains(speaker)) {
                talk.speakers.add(speaker);
                speaker.talks.add(talk);
                talk.persist();
                speaker.persist();
            }
        }
    }

    private Speaker createOrUpdateSpeaker(String[] fields) {
        String speakerIdStr = fields[COL_SPEAKER_ID].trim();
        if (speakerIdStr.isEmpty()) {
            return null;
        }

        try {
            UUID speakerId = UUID.fromString(speakerIdStr);
            Speaker speaker = Speaker.findById(speakerId);
            boolean isNew = (speaker == null);

            if (isNew) {
                speaker = new Speaker();
                speaker.id = speakerId;
            }

            speaker.firstName = emptyToNull(fields[COL_FIRST_NAME]);
            speaker.lastName = emptyToNull(fields[COL_LAST_NAME]);
            speaker.title = emptyToNull(fields[COL_TAG_LINE]);
            speaker.biography = emptyToNull(fields[COL_BIO]);
            speaker.star = false;

            if (isNew) {
                speaker.persist();
            }

            // Download profile picture if URL is provided
            String profilePictureUrl = emptyToNull(fields[COL_PROFILE_PICTURE]);
            if (profilePictureUrl != null) {
                downloadProfilePicture(speakerId, profilePictureUrl);
            }

            LOG.debugf("%s speaker: %s %s (%s)", isNew ? "Persisted" : "Updated",
                    speaker.firstName, speaker.lastName, speakerId);
            return speaker;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating speaker: %s", e.getMessage());
            return null;
        }
    }

    private Talk createOrUpdateTalk(String[] fields) {
        String sessionIdStr = fields[COL_SESSION_ID].trim();

        if (sessionIdStr.isEmpty()) {
            return null;
        }

        try {
            Long sessionId = Long.parseLong(sessionIdStr);
            Talk talk = Talk.findById(sessionId);
            boolean isNew = (talk == null);

            if (isNew) {
                // For new talks, title is required
                String title = fields[COL_TITLE].trim();
                if (title.isEmpty()) {
                    LOG.warnf("Skipping new talk with session ID %d: title is empty", sessionId);
                    return null;
                }

                talk = new Talk();
                talk.id = sessionId;
                talk.title = title;
                talk.description = emptyToNull(fields[COL_DESCRIPTION]);
                talk.scheduledDuration = emptyToNull(fields[COL_SCHEDULED_DURATION]);
                talk.liveLink = emptyToNull(fields[COL_LIVE_LINK]);

                String scheduledAt = fields[COL_SCHEDULED_AT].trim();
                if (!scheduledAt.isEmpty()) {
                    try {
                        // Parse ISO format date-time from Excel
                        LocalDateTime estDateTime = LocalDateTime.parse(scheduledAt);
                        ZonedDateTime estZoned = estDateTime.atZone(EST_ZONE);
                        ZonedDateTime cetZoned = estZoned.withZoneSameInstant(CET_ZONE);
                        talk.date = cetZoned.format(DATE_FORMAT);
                        talk.estTime = estZoned.format(TIME_FORMAT);
                        talk.cetTime = cetZoned.format(TIME_FORMAT);
                    } catch (Exception e) {
                        LOG.warnf("Could not parse date '%s' for talk %d", scheduledAt, sessionId);
                    }
                }

                talk.persist();
                LOG.debugf("Persisted talk: %s (%d)", talk.title, sessionId);
            } else {
                // For existing talks, just return the found talk
                LOG.debugf("Found existing talk: %s (%d) with %d speaker(s)", talk.title, sessionId,
                        talk.speakers.size());
            }

            return talk;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating talk: %s", e.getMessage());
            return null;
        }
    }

    private String emptyToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void downloadProfilePicture(UUID speakerId, String profilePictureUrl) {
        if (profilePictureUrl == null || profilePictureUrl.isEmpty()) {
            return;
        }

        try {
            // Determine file extension from URL
            String extension = "jpg"; // default
            int lastDot = profilePictureUrl.lastIndexOf('.');
            if (lastDot > 0) {
                String urlExt = profilePictureUrl.substring(lastDot + 1).toLowerCase();
                // Remove query parameters if any
                int queryIndex = urlExt.indexOf('?');
                if (queryIndex > 0) {
                    urlExt = urlExt.substring(0, queryIndex);
                }
                if (urlExt.equals("jpg") || urlExt.equals("jpeg") || urlExt.equals("png") || urlExt.equals("gif")) {
                    extension = urlExt;
                }
            }

            // Target path: src/main/resources/META-INF/speaker/{Speaker Id}.{ext}
            Path resourcesPath = Paths.get("src/main/resources/META-INF/speaker");
            Files.createDirectories(resourcesPath);

            Path imagePath = resourcesPath.resolve(speakerId.toString() + "." + extension);

            // Check if file already exists
            if (Files.exists(imagePath)) {
                LOG.debugf("Profile picture already exists for speaker %s, skipping download", speakerId);
                return;
            }

            // Download image
            LOG.infof("Downloading profile picture for speaker %s from %s", speakerId, profilePictureUrl);
            URI uri = URI.create(profilePictureUrl);

            try (InputStream in = uri.toURL().openStream()) {
                Files.copy(in, imagePath);
                LOG.infof("Downloaded profile picture for speaker %s to %s", speakerId, imagePath);
            }

        } catch (Exception e) {
            LOG.errorf(e, "Error downloading profile picture for speaker %s from %s", speakerId, profilePictureUrl);
        }
    }
}
