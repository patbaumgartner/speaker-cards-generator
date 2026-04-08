package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing runtime configuration such as the Devoxx CFP API token.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

	private final DevoxxApiConfig devoxxApiConfig;

	public ConfigController(DevoxxApiConfig devoxxApiConfig) {
		this.devoxxApiConfig = devoxxApiConfig;
	}

	/**
	 * Returns whether an API token is currently configured (without exposing the token
	 * value).
	 */
	@GetMapping(value = "/api-token/status", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Boolean> tokenStatus() {
		String token = devoxxApiConfig.getApiToken();
		return Map.of("configured", token != null && !token.isBlank());
	}

	/**
	 * Sets the Devoxx CFP API token at runtime.
	 */
	@PostMapping(value = "/api-token", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> setToken(@RequestBody Map<String, String> body) {
		String token = body.get("token");
		if (token == null || token.isBlank()) {
			return ResponseEntity.badRequest().body("Token must not be empty.");
		}
		devoxxApiConfig.setApiToken(token);
		return ResponseEntity.ok("API token configured.");
	}

	/**
	 * Clears the Devoxx CFP API token.
	 */
	@DeleteMapping(value = "/api-token", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> clearToken() {
		devoxxApiConfig.setApiToken(null);
		return ResponseEntity.ok("API token cleared.");
	}

}
