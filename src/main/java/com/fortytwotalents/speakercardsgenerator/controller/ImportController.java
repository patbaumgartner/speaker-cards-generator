package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService;
import com.fortytwotalents.speakercardsgenerator.service.ImportFromCSVService;
import com.fortytwotalents.speakercardsgenerator.service.ImportFromCSVService.ImportResult;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that triggers data import operations.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
	 *   POST /api/import/csv              – import from default XLSX file (SelectedWithSchedule.xlsx)
	 *   POST /api/import/csv/{path}       – import from a custom XLSX/CSV path
	 *   POST /api/import/devoxx           – import from CFP API (configured event)
	 *   POST /api/import/devoxx/{eventId} – import from CFP API for a specific event ID
 * </pre>
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

	private static final Logger log = LoggerFactory.getLogger(ImportController.class);

	private final ImportFromCSVService importFromCSVService;

	private final DevoxxImportService devoxxImportService;

	public ImportController(ImportFromCSVService importFromCSVService, DevoxxImportService devoxxImportService) {
		this.importFromCSVService = importFromCSVService;
		this.devoxxImportService = devoxxImportService;
	}

	@PostMapping(value = "/csv", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importCsv() {
		try {
			ImportResult result = importFromCSVService.importFromXlsx("SelectedWithSchedule.xlsx");
			return ResponseEntity.ok(importSummary(result));
		}
		catch (Exception e) {
			log.error("Error during XLSX import", e);
			return ResponseEntity.internalServerError().body("XLSX import failed. Check the application logs.");
		}
	}

	@PostMapping(value = "/csv/{path:.*}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importCsvFromPath(@PathVariable String path) {
		try {
			Path base = Path.of("").toAbsolutePath();
			Path resolved = base.resolve(path).normalize();
			if (!resolved.startsWith(base)) {
				log.warn("Rejected path traversal attempt: {}", path);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("Invalid file path: must be within the working directory");
			}
			ImportResult result = importFromCSVService.importFromXlsx(resolved.toString());
			return ResponseEntity.ok(importSummary(result));
		}
		catch (Exception e) {
			log.error("Error during XLSX import from path: {}", path, e);
			return ResponseEntity.internalServerError().body("XLSX import failed. Check the application logs.");
		}
	}

	@PostMapping(value = "/devoxx", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importFromDevoxx() {
		try {
			int count = devoxxImportService.importSpeakers();
			return ResponseEntity.ok("CFP import completed. Imported/updated " + count + " speakers.");
		}
		catch (Exception e) {
			log.error("Error during CFP import", e);
			return ResponseEntity.internalServerError().body("CFP import failed. Check the application logs.");
		}
	}

	@PostMapping(value = "/devoxx/{eventId}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importFromDevoxxEvent(@PathVariable String eventId) {
		try {
			int count = devoxxImportService.importSpeakers(eventId);
			return ResponseEntity
				.ok("CFP import completed for event '" + eventId + "'. Imported/updated " + count + " speakers.");
		}
		catch (Exception e) {
			log.error("Error during CFP import for event {}", eventId, e);
			return ResponseEntity.internalServerError().body("CFP import failed. Check the application logs.");
		}
	}

	private static String importSummary(ImportResult result) {
		return "XLSX import completed. Imported " + result.rowsImported() + " of " + result.rowsRead() + " rows.";
	}

}
