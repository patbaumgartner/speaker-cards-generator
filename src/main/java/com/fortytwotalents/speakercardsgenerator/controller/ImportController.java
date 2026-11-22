package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that triggers speaker imports from the Devoxx CFP API.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
 *   POST /api/import/devoxx           – import from CFP API (configured event)
 *   POST /api/import/devoxx/{eventId} – import from CFP API for a specific event ID
 * </pre>
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

	private static final Logger log = LoggerFactory.getLogger(ImportController.class);

	private final DevoxxImportService devoxxImportService;

	public ImportController(DevoxxImportService devoxxImportService) {
		this.devoxxImportService = devoxxImportService;
	}

	@PostMapping(value = "/devoxx", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> importFromDevoxx() {
		try {
			int count = devoxxImportService.importSpeakers();
			return ResponseEntity.ok("CFP import completed. Imported/updated " + count + " speakers.");
		}
		catch (IllegalArgumentException e) {
			log.warn("CFP import rejected: {}", e.getMessage());
			return ResponseEntity.badRequest().body("CFP import failed: " + e.getMessage());
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
		catch (IllegalArgumentException e) {
			log.warn("CFP import rejected for event {}: {}", eventId, e.getMessage());
			return ResponseEntity.badRequest().body("CFP import failed: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Error during CFP import for event {}", eventId, e);
			return ResponseEntity.internalServerError().body("CFP import failed. Check the application logs.");
		}
	}

}
