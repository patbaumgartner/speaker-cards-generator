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

	/**
	 * Imports speakers and talks from the default XLSX file
	 * ({@code SelectedWithSchedule.xlsx}) located in the working directory.
	 * @return plain-text confirmation message
	 */
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

	/**
	 * Imports speakers and talks from a custom XLSX/CSV file path.
	 *
	 * <p>
	 * The path is resolved relative to the working directory. Paths that would escape the
	 * working directory (e.g. {@code ../../etc/passwd}) are rejected with a 400 response.
	 * @param path file path relative to the working directory (URL-encoded slashes
	 * allowed)
	 * @return plain-text confirmation message
	 */
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

	/**
	 * Imports speakers from the Devoxx API using the event ID configured in {@code
	 * application.properties} ({@code app.devoxx.api.event-id}).
	 * @return plain-text confirmation with the number of speakers imported
	 */
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

	/**
	 * Imports speakers from the Devoxx API for a specific event ID.
	 *
	 * <p>
	 * Example event IDs:
	 *
	 * <ul>
	 * <li>{@code vdz26} – Voxxed Days Zürich 2026
	 * <li>{@code vdt26} – Voxxed Days Ticino 2026
	 * <li>{@code vdcern26} – Voxxed Days CERN 2026
	 * </ul>
	 * @param eventId Devoxx event identifier
	 * @return plain-text confirmation with the number of speakers imported
	 */
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
