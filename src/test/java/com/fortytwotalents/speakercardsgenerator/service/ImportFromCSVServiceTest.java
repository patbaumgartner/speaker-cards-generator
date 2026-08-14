package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.StorageConfig;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImportFromCSVServiceTest {

	private static final UUID SPEAKER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

	@TempDir
	Path tempDir;

	@Autowired
	private ImportFromCSVService service;

	@Autowired
	private SpeakerRepository speakerRepository;

	@Autowired
	private TalkRepository talkRepository;

	@BeforeEach
	void clearDatabase() {
		this.talkRepository.deleteAll();
		this.speakerRepository.deleteAll();
	}

	@Test
	void missingFileFailsInsteadOfReportingSuccess() {
		assertThatThrownBy(() -> this.service.importFromXlsx(this.tempDir.resolve("missing.xlsx").toString()))
			.isInstanceOf(ImportFromCSVService.XlsxImportException.class)
			.hasMessageContaining("not found");
	}

	@Test
	void malformedWorkbookFailsInsteadOfReportingSuccess() throws IOException {
		Path malformed = Files.writeString(this.tempDir.resolve("malformed.xlsx"), "not a workbook");

		assertThatThrownBy(() -> this.service.importFromXlsx(malformed.toString()))
			.isInstanceOf(ImportFromCSVService.XlsxImportException.class)
			.hasMessageContaining("Could not import");
	}

	@Test
	void importsAndUpdatesSpeakerAndTalkData() throws IOException {
		Path workbook = workbook("Original title", "2026-03-24T14:30:00", "Jane", "Doe");

		ImportFromCSVService.ImportResult first = this.service.importFromXlsx(workbook.toString());
		assertThat(first.rowsRead()).isEqualTo(1);
		assertThat(first.rowsImported()).isEqualTo(1);
		assertThat(this.speakerRepository.findById(SPEAKER_ID)).hasValueSatisfying(speaker -> {
			assertThat(speaker.displayName()).isEqualTo("Jane Doe");
			assertThat(speaker.getBiography()).isEqualTo("Biography");
		});
		assertThat(this.talkRepository.findWithSpeakersById(1001L)).hasValueSatisfying(talk -> {
			assertThat(talk.getTitle()).isEqualTo("Original title");
			assertThat(talk.getEstTime()).isEqualTo("14:30");
			assertThat(talk.getCetTime()).isEqualTo("19:30");
			assertThat(talk.getSpeakers()).hasSize(1);
		});

		workbook("Updated title", "2026-03-25T10:00:00", "Janet", "Doe", workbook);
		ImportFromCSVService.ImportResult second = this.service.importFromXlsx(workbook.toString());

		assertThat(second.rowsImported()).isEqualTo(1);
		assertThat(this.speakerRepository.findById(SPEAKER_ID).orElseThrow().getFirstName()).isEqualTo("Janet");
		assertThat(this.talkRepository.findById(1001L).map(Talk::getTitle)).hasValue("Updated title");
		assertThat(this.talkRepository.findWithSpeakersById(1001L).orElseThrow().getSpeakers()).hasSize(1);
	}

	@Test
	void skipsRowsWithoutIdentifiersOrTitlesAndReportsAccurateCounts() throws IOException {
		Path workbook = this.tempDir.resolve("skips.xlsx");
		try (XSSFWorkbook xlsx = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
			Sheet sheet = xlsx.createSheet();
			sheet.createRow(0);
			Row noSession = sheet.createRow(1);
			noSession.createCell(12).setCellValue(SPEAKER_ID.toString());
			Row noTitle = sheet.createRow(2);
			noTitle.createCell(0).setCellValue(1002);
			noTitle.createCell(12).setCellValue(SPEAKER_ID.toString());
			xlsx.write(out);
		}

		ImportFromCSVService.ImportResult result = this.service.importFromXlsx(workbook.toString());

		assertThat(result.rowsRead()).isEqualTo(2);
		assertThat(result.rowsImported()).isZero();
		assertThat(this.talkRepository.count()).isZero();
	}

	@Test
	void invalidSpeakerUuidRollsBackTheWholeImport() throws IOException {
		Path workbook = workbook("Title", "2026-03-24T14:30:00", "Jane", "Doe");
		try (XSSFWorkbook xlsx = new XSSFWorkbook(Files.newInputStream(workbook));
				OutputStream out = Files.newOutputStream(workbook)) {
			xlsx.getSheetAt(0).getRow(1).getCell(12).setCellValue("not-a-uuid");
			xlsx.write(out);
		}

		assertThatThrownBy(() -> this.service.importFromXlsx(workbook.toString()))
			.isInstanceOf(ImportFromCSVService.XlsxImportException.class);
		assertThat(this.talkRepository.count()).isZero();
		assertThat(this.speakerRepository.count()).isZero();
	}

	private Path workbook(String title, String scheduledAt, String firstName, String lastName) throws IOException {
		return workbook(title, scheduledAt, firstName, lastName, this.tempDir.resolve("import.xlsx"));
	}

	private Path workbook(String title, String scheduledAt, String firstName, String lastName, Path path)
			throws IOException {
		try (XSSFWorkbook xlsx = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(path)) {
			Sheet sheet = xlsx.createSheet();
			sheet.createRow(0);
			Row row = sheet.createRow(1);
			row.createCell(0).setCellValue(1001);
			row.createCell(1).setCellValue(title);
			row.createCell(2).setCellValue("Description");
			row.createCell(8).setCellValue(scheduledAt);
			row.createCell(9).setCellValue("45");
			row.createCell(12).setCellValue(SPEAKER_ID.toString());
			row.createCell(13).setCellValue(firstName);
			row.createCell(14).setCellValue(lastName);
			row.createCell(17).setCellValue("Biography");
			xlsx.write(out);
		}
		return path;
	}

}
