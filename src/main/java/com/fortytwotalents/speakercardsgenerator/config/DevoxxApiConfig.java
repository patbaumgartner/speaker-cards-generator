package com.fortytwotalents.speakercardsgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Devoxx CFP API configuration.
 *
 * <p>
 * Bound from the {@code app.devoxx.*} namespace. The API base URL and event identifier
 * are used by
 * {@link com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService} to fetch
 * speakers directly from the Devoxx mobile API.
 *
 * <p>
 * Example:
 *
 * <pre>
 * app.devoxx.api.base-url=https://m.devoxx.com
 * app.devoxx.api.event-id=vdz26
 * </pre>
 */
@ConfigurationProperties(prefix = "app.devoxx.api")
public class DevoxxApiConfig {

	/** Base URL of the Devoxx mobile API. */
	private String baseUrl = "https://m.devoxx.com";

	/**
	 * Devoxx event identifier, e.g. {@code vdz26} (Voxxed Days Zürich 2026),
	 * {@code vdt26} (Voxxed Days Ticino 2026), or {@code vdcern26} (Voxxed Days CERN
	 * 2026).
	 */
	private String eventId = "vdz26";

	// -------------------------------------------------------------------------
	// Getters / setters
	// -------------------------------------------------------------------------

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

}
