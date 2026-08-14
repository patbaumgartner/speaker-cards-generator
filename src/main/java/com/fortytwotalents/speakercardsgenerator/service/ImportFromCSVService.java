package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
 * Imports speakers and talks from a Sessionize XLSX export file
 * ({@code SelectedWithSchedule.xlsx}).
 *
 * <p>
 * The import is idempotent: existing speakers and talks are looked up by their primary
 * keys and only missing records are inserted. Speaker profile pictures are downloaded to
 * {@code src/main/resources/static/images/speaker/} if not already present.
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

	private final SpeakerPhotoStore photoStore;

	public ImportFromCSVService(SpeakerRepository speakerRepository, TalkRepository talkRepository,
			SpeakerPhotoStore photoStore) {
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
		this.photoStore = photoStore;
	}

	/**
	 * Imports all rows from the given XLSX file.
	 * @param xlsxFilePath absolute or relative path to the XLSX file
	 * @return counts from the completed import
	 * @throws XlsxImportException when the file is missing, malformed, or a row cannot be
	 * persisted; the surrounding transaction is rolled back in that case
	 */
	@Transactional
	public ImportResult importFromXlsx(String xlsxFilePath) {
		log.info("Starting XLSX import from: {}", xlsxFilePath);

		Path path = Path.of(xlsxFilePath);
		if (!Files.isRegularFile(path)) {
			throw new XlsxImportException("XLSX file not found: " + path);
		}

		try (FileInputStream fis = new FileInputStream(path.toFile()); Workbook workbook = new XSSFWorkbook(fis)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new XlsxImportException("XLSX workbook contains no sheets: " + path);
			}

			Sheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = sheet.iterator();

			// Skip header row
			if (rowIterator.hasNext()) {
				Row header = rowIterator.next();
				log.info("XLSX header found with {} columns", header.getLastCellNum());
			}

			int rowsRead = 0;
			int rowsImported = 0;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				rowsRead++;
				String[] fields = extractRowData(row);
				if (processRow(fields)) {
					rowsImported++;
				}
			}
			log.info("XLSX import completed. Imported {} of {} rows", rowsImported, rowsRead);
			return new ImportResult(rowsRead, rowsImported);

		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof XlsxImportException importException) {
				throw importException;
			}
			throw new XlsxImportException("Could not import XLSX file: " + path, ex);
		}
	}

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

	private boolean processRow(String[] fields) {
		Talk talk = createOrUpdateTalk(fields);
		Speaker speaker = createOrUpdateSpeaker(fields);

		if (speaker != null && talk != null) {
			if (!talk.getSpeakers().contains(speaker)) {
				talk.getSpeakers().add(speaker);
				speaker.getTalks().add(talk);
				talkRepository.save(talk);
				speakerRepository.save(speaker);
			}
			return true;
		}
		return false;
	}

	private Speaker createOrUpdateSpeaker(String[] fields) {
		String speakerIdStr = fields[COL_SPEAKER_ID].trim();
		if (speakerIdStr.isEmpty()) {
			return null;
		}
		UUID speakerId = UUID.fromString(speakerIdStr);
		Speaker speaker = speakerRepository.findById(speakerId).orElse(null);
		boolean isNew = speaker == null;

		if (isNew) {
			speaker = new Speaker();
			speaker.setId(speakerId);
		}

		speaker.setFirstName(nullIfBlank(fields[COL_FIRST_NAME]));
		speaker.setLastName(defaultIfBlank(fields[COL_LAST_NAME], "(unknown)"));
		speaker.setTitle(nullIfBlank(fields[COL_TAG_LINE]));
		speaker.setBiography(defaultIfBlank(fields[COL_BIO], "(no biography provided)"));
		speaker.setStar(false);

		speakerRepository.save(speaker);

		photoStore.download(speakerId, nullIfBlank(fields[COL_PROFILE_PICTURE]));

		log.atDebug()
			.addArgument(() -> isNew ? "Persisted" : "Updated")
			.addArgument(speaker.getFirstName())
			.addArgument(speaker.getLastName())
			.addArgument(speakerId)
			.log("{} speaker: {} {} ({})");
		return speaker;
	}

	private Talk createOrUpdateTalk(String[] fields) {
		String sessionIdStr = fields[COL_SESSION_ID].trim();
		if (sessionIdStr.isEmpty()) {
			return null;
		}
		Long sessionId = Long.parseLong(sessionIdStr);
		Talk talk = talkRepository.findById(sessionId).orElse(null);
		boolean isNew = talk == null;

		String title = fields[COL_TITLE].trim();
		if (title.isEmpty()) {
			log.warn("Skipping talk with session ID {}: title is empty", sessionId);
			return null;
		}
		if (isNew) {
			talk = new Talk();
			talk.setId(sessionId);
		}
		talk.setTitle(title);
		talk.setDescription(nullIfBlank(fields[COL_DESCRIPTION]));
		talk.setScheduledDuration(nullIfBlank(fields[COL_SCHEDULED_DURATION]));
		talk.setLiveLink(nullIfBlank(fields[COL_LIVE_LINK]));

		String scheduledAt = fields[COL_SCHEDULED_AT].trim();
		if (!scheduledAt.isEmpty()) {
			try {
				LocalDateTime estDateTime = LocalDateTime.parse(scheduledAt);
				ZonedDateTime estZoned = estDateTime.atZone(EST_ZONE);
				ZonedDateTime cetZoned = estZoned.withZoneSameInstant(CET_ZONE);
				talk.setDate(cetZoned.format(DATE_FORMAT));
				talk.setEstTime(estZoned.format(TIME_FORMAT));
				talk.setCetTime(cetZoned.format(TIME_FORMAT));
			}
			catch (java.time.format.DateTimeParseException ex) {
				log.warn("Could not parse date '{}' for talk {}", scheduledAt, sessionId);
			}
		}
		talkRepository.save(talk);
		log.debug("{} talk: {} ({})", isNew ? "Persisted" : "Updated", talk.getTitle(), sessionId);
		return talk;
	}

	private String nullIfBlank(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String defaultIfBlank(String value, String defaultValue) {
		String normalised = nullIfBlank(value);
		return normalised != null ? normalised : defaultValue;
	}

	public record ImportResult(int rowsRead, int rowsImported) {
	}

	public static class XlsxImportException extends RuntimeException {

		XlsxImportException(String message) {
			super(message);
		}

		XlsxImportException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
