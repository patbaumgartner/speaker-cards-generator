package com.fortytwotalents.speakercardsgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CFP API configuration.
 *
 * <p>
 * Bound from the {@code app.devoxx.*} namespace. The event identifier is used by
 * {@link com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService} to fetch
 * speakers from the public CFP API at
 * {@code https://{eventId}.cfp.dev/api/public/speakers}.
 *
 * <p>
 * Example:
 *
 * <pre>
 * app.devoxx.api.event-id=vdz26
 * </pre>
 */
@ConfigurationProperties(prefix = "app.devoxx.api")
public class DevoxxApiConfig {

	/**
	 * Event identifier, e.g. {@code vdz26} (Voxxed Days Zürich 2026), {@code vdt26}
	 * (Voxxed Days Ticino 2026), or {@code vdcern26} (Voxxed Days CERN 2026). Used to
	 * build the CFP API URL as {@code https://{eventId}.cfp.dev/api/public/speakers}. Set
	 * to {@code none} to disable API import for a profile.
	 */
	private String eventId;

	/**
	 * Optional Bearer token for the authenticated CFP API. When set, the import fetches
	 * speaker details from {@code /api/speakers/{id}} which includes the {@code jobTitle}
	 * field not available on the public endpoint.
	 */
	private String apiToken;

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getApiToken() {
		return apiToken;
	}

	public void setApiToken(String apiToken) {
		this.apiToken = apiToken;
	}

	/**
	 * Returns {@code true} when the Devoxx CFP API is enabled for this profile.
	 *
	 * <p>
	 * The API is considered disabled when {@code eventId} is {@code null}, blank, or the
	 * sentinel value {@code "none"} (case-insensitive). Use {@code none} in a profile
	 * properties file to explicitly disable CFP import without leaving the property
	 * empty.
	 */
	public boolean isDevoxxApiEnabled() {
		return this.eventId != null && !this.eventId.isBlank() && !"none".equalsIgnoreCase(this.eventId);
	}

}
