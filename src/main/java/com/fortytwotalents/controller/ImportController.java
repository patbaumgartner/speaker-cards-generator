package com.fortytwotalents.controller;

import com.fortytwotalents.service.DevoxxImportService;
import com.fortytwotalents.service.ImportFromCSVService;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that triggers data import operations.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
 *   GET /api/import/csv              – import from default XLSX file (SelectedWithSchedule.xlsx)
 *   GET /api/import/csv/{path}       – import from a custom XLSX/CSV path
 *   GET /api/import/devoxx           – import from Devoxx API (configured event)
 *   GET /api/import/devoxx/{eventId} – import from Devoxx API for a specific event ID
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

	@GetMapping(value = "/csv", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importCsv() {
		try {
			importFromCSVService.importFromXlsx("SelectedWithSchedule.xlsx");
			return ResponseEntity.ok("XLSX import completed successfully. Check logs for details.");
		}
		catch (Exception e) {
			log.error("Error during XLSX import", e);
			return ResponseEntity.internalServerError().body("Error during XLSX import: " + e.getMessage());
		}
	}

	@GetMapping(value = "/csv/{path:.*}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importCsvFromPath(@PathVariable String path) {
		try {
			Path base = Paths.get("").toAbsolutePath();
			Path resolved = base.resolve(path).normalize();
			if (!resolved.startsWith(base)) {
				log.warn("Rejected path traversal attempt: {}", path);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("Invalid file path: must be within the working directory");
			}
			importFromCSVService.importFromXlsx(resolved.toString());
			return ResponseEntity.ok("XLSX import completed successfully. Check logs for details.");
		}
		catch (Exception e) {
			log.error("Error during XLSX import from path: {}", path, e);
			return ResponseEntity.internalServerError().body("Error during XLSX import: " + e.getMessage());
		}
	}

	@GetMapping(value = "/devoxx", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importFromDevoxx() {
		try {
			int count = devoxxImportService.importSpeakers();
			return ResponseEntity.ok("Devoxx import completed. Imported/updated " + count + " speakers.");
		}
		catch (Exception e) {
			log.error("Error during Devoxx import", e);
			return ResponseEntity.internalServerError().body("Error during Devoxx import: " + e.getMessage());
		}
	}

	@GetMapping(value = "/devoxx/{eventId}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importFromDevoxxEvent(@PathVariable String eventId) {
		try {
			int count = devoxxImportService.importSpeakers(eventId);
			return ResponseEntity
				.ok("Devoxx import completed for event '" + eventId + "'. Imported/updated " + count + " speakers.");
		}
		catch (Exception e) {
			log.error("Error during Devoxx import for event {}", eventId, e);
			return ResponseEntity.internalServerError()
				.body("Error during Devoxx import for event '" + eventId + "': " + e.getMessage());
		}
	}

}
