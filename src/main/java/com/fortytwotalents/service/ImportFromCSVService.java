package com.fortytwotalents.service;

import com.fortytwotalents.model.Speaker;
import com.fortytwotalents.model.Talk;
import com.fortytwotalents.repository.SpeakerRepository;
import com.fortytwotalents.repository.TalkRepository;
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
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports speakers and talks from a Sessionize XLSX export file ({@code
 * SelectedWithSchedule.xlsx}).
 *
 * <p>
 * The import is idempotent: existing speakers and talks are looked up by their primary
 * keys and only missing records are inserted. Speaker profile pictures are downloaded to
 * {@code
 * src/main/resources/static/images/speaker/} if not already present.
 *
 * <p>
 * Expected XLSX column layout (0-indexed):
 *
 * <pre>
 *  0  – Session Id
 *  1  – Title
 *  2  – Description
 *  8  – Scheduled At
 *  9  – Scheduled Duration
 * 10  – Live Link
 * 12  – Speaker Id
 * 13  – FirstName
 * 14  – LastName
 * 16  – TagLine
 * 17  – Bio
 * 22  – Profile Picture (URL)
 * </pre>
 */
@Service
public class ImportFromCSVService {

	private static final Logger log = LoggerFactory.getLogger(ImportFromCSVService.class);

	/** URL schemes allowed when downloading profile pictures from external sources. */
	private static final Set<String> ALLOWED_PICTURE_SCHEMES = Set.of("http", "https");

	// XLSX column indices from SelectedWithSchedule.xlsx
	private static final int COL_SESSION_ID = 0;

	private static final int COL_TITLE = 1;

	private static final int COL_DESCRIPTION = 2;

	private static final int COL_SCHEDULED_AT = 8;

	private static final int COL_SCHEDULED_DURATION = 9;

	private static final int COL_LIVE_LINK = 10;

	private static final int COL_SPEAKER_ID = 12;

	private static final int COL_FIRST_NAME = 13;

	private static final int COL_LAST_NAME = 14;

	private static final int COL_TAG_LINE = 16;

	private static final int COL_BIO = 17;

	private static final int COL_PROFILE_PICTURE = 22;

	private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

	private static final ZoneId CET_ZONE = ZoneId.of("Europe/Paris");

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final SpeakerRepository speakerRepository;

	private final TalkRepository talkRepository;

	public ImportFromCSVService(SpeakerRepository speakerRepository, TalkRepository talkRepository) {
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
	}

	/**
	 * Imports all rows from the given XLSX file.
	 * @param xlsxFilePath absolute or relative path to the XLSX file
	 */
	@Transactional
	public void importFromXlsx(String xlsxFilePath) {
		log.info("Starting XLSX import from: {}", xlsxFilePath);

		Path path = Paths.get(xlsxFilePath);
		if (!Files.exists(path)) {
			log.error("XLSX file not found: {}", xlsxFilePath);
			return;
		}

		try (FileInputStream fis = new FileInputStream(xlsxFilePath); Workbook workbook = new XSSFWorkbook(fis)) {

			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.iterator();

			// Skip header row
			if (rowIterator.hasNext()) {
				Row header = rowIterator.next();
				log.info("XLSX header found with {} columns", header.getLastCellNum());
			}

			int rowNumber = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				rowNumber++;
				try {
					String[] fields = extractRowData(row);
					processRow(fields);
				}
				catch (Exception e) {
					log.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
				}
			}
			log.info("XLSX import completed. Processed {} rows", rowNumber);

		}
		catch (Exception e) {
			log.error("Error reading XLSX file: {}", xlsxFilePath, e);
		}
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private String[] extractRowData(Row row) {
		List<String> fields = new ArrayList<>();
		int lastColumn = Math.max(row.getLastCellNum(), 23);
		for (int i = 0; i < lastColumn; i++) {
			fields.add(cellToString(row.getCell(i)));
		}
		return fields.toArray(new String[0]);
	}

	private String cellToString(Cell cell) {
		if (cell == null) {
			return "";
		}
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue().toString()
					: String.valueOf((long) cell.getNumericCellValue());
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			case FORMULA -> cell.getCellFormula();
			default -> "";
		};
	}

	private void processRow(String[] fields) {
		Talk talk = createOrUpdateTalk(fields);
		Speaker speaker = createOrUpdateSpeaker(fields);

		if (speaker != null && talk != null) {
			if (!talk.speakers.contains(speaker)) {
				talk.speakers.add(speaker);
				speaker.talks.add(talk);
				talkRepository.save(talk);
				speakerRepository.save(speaker);
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
			Speaker speaker = speakerRepository.findById(speakerId).orElse(null);
			boolean isNew = speaker == null;

			if (isNew) {
				speaker = new Speaker();
				speaker.id = speakerId;
			}

			speaker.firstName = nullIfBlank(fields[COL_FIRST_NAME]);
			speaker.lastName = nullIfBlank(fields[COL_LAST_NAME]);
			speaker.title = nullIfBlank(fields[COL_TAG_LINE]);
			speaker.biography = nullIfBlank(fields[COL_BIO]);
			speaker.star = false;

			speakerRepository.save(speaker);

			String pictureUrl = nullIfBlank(fields[COL_PROFILE_PICTURE]);
			if (pictureUrl != null) {
				downloadProfilePicture(speakerId, pictureUrl);
			}

			log.debug("{} speaker: {} {} ({})", isNew ? "Persisted" : "Updated", speaker.firstName, speaker.lastName,
					speakerId);
			return speaker;
		}
		catch (Exception e) {
			log.error("Error creating speaker: {}", e.getMessage(), e);
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
			Talk talk = talkRepository.findById(sessionId).orElse(null);
			boolean isNew = talk == null;

			if (isNew) {
				String title = fields[COL_TITLE].trim();
				if (title.isEmpty()) {
					log.warn("Skipping new talk with session ID {}: title is empty", sessionId);
					return null;
				}
				talk = new Talk();
				talk.id = sessionId;
				talk.title = title;
				talk.description = nullIfBlank(fields[COL_DESCRIPTION]);
				talk.scheduledDuration = nullIfBlank(fields[COL_SCHEDULED_DURATION]);
				talk.liveLink = nullIfBlank(fields[COL_LIVE_LINK]);

				String scheduledAt = fields[COL_SCHEDULED_AT].trim();
				if (!scheduledAt.isEmpty()) {
					try {
						LocalDateTime estDateTime = LocalDateTime.parse(scheduledAt);
						ZonedDateTime estZoned = estDateTime.atZone(EST_ZONE);
						ZonedDateTime cetZoned = estZoned.withZoneSameInstant(CET_ZONE);
						talk.date = cetZoned.format(DATE_FORMAT);
						talk.estTime = estZoned.format(TIME_FORMAT);
						talk.cetTime = cetZoned.format(TIME_FORMAT);
					}
					catch (Exception e) {
						log.warn("Could not parse date '{}' for talk {}", scheduledAt, sessionId);
					}
				}
				talkRepository.save(talk);
				log.debug("Persisted talk: {} ({})", talk.title, sessionId);
			}
			else {
				log.debug("Found existing talk: {} ({}) with {} speaker(s)", talk.title, sessionId,
						talk.speakers.size());
			}
			return talk;
		}
		catch (Exception e) {
			log.error("Error creating talk: {}", e.getMessage(), e);
			return null;
		}
	}

	private String nullIfBlank(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Downloads a speaker profile picture from the given URL and stores it under
	 * {@code src/main/resources/static/images/speaker/{speakerId}.{ext}}.
	 *
	 * <p>
	 * Only {@code http} and {@code https} URL schemes are permitted to prevent SSRF
	 * attacks.
	 * @param speakerId speaker UUID (used as filename)
	 * @param pictureUrl URL of the profile picture
	 */
	private void downloadProfilePicture(UUID speakerId, String pictureUrl) {
		if (pictureUrl == null || pictureUrl.isEmpty()) {
			return;
		}
		try {
			URI uri = URI.create(pictureUrl);
			String scheme = uri.getScheme();
			if (scheme == null || !ALLOWED_PICTURE_SCHEMES.contains(scheme.toLowerCase())) {
				log.warn("Skipping profile picture download for speaker {}: disallowed URL scheme in '{}'", speakerId,
						pictureUrl);
				return;
			}

			String extension = "jpg";
			int lastDot = pictureUrl.lastIndexOf('.');
			if (lastDot > 0) {
				String urlExt = pictureUrl.substring(lastDot + 1).toLowerCase();
				int queryIdx = urlExt.indexOf('?');
				if (queryIdx > 0) {
					urlExt = urlExt.substring(0, queryIdx);
				}
				if (urlExt.equals("jpg") || urlExt.equals("jpeg") || urlExt.equals("png") || urlExt.equals("gif")) {
					extension = urlExt;
				}
			}

			Path resourcesPath = Paths.get("src/main/resources/static/images/speaker");
			Files.createDirectories(resourcesPath);

			Path imagePath = resourcesPath.resolve(speakerId + "." + extension);
			if (Files.exists(imagePath)) {
				log.debug("Profile picture already exists for speaker {}, skipping download", speakerId);
				return;
			}

			log.info("Downloading profile picture for speaker {} from {}", speakerId, pictureUrl);
			try (InputStream in = uri.toURL().openStream()) {
				Files.copy(in, imagePath);
				log.info("Downloaded profile picture for speaker {} to {}", speakerId, imagePath);
			}
		}
		catch (Exception e) {
			log.error("Error downloading profile picture for speaker {} from {}", speakerId, pictureUrl, e);
		}
	}

}
