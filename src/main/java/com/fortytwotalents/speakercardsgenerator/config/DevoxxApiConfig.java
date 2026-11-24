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
 * app.devoxx.api.event-id=vdz27
 * </pre>
 */
@ConfigurationProperties(prefix = "app.devoxx.api")
public class DevoxxApiConfig {

	/**
	 * Event identifier, e.g. {@code vdz27} (Voxxed Days Zürich 2027), {@code vdt27}
	 * (Voxxed Days Ticino 2027), or {@code vdcern27} (Voxxed Days CERN 2027). Used to
	 * build the CFP API URL as {@code https://{eventId}.cfp.dev/api/public/speakers}.
	 * Defaults to {@code vdz27} when no event profile is active.
	 */
	private String eventId = "vdz27";

	/**
	 * Bearer token for the authenticated CFP API. Required for imports so the importer
	 * can fetch speaker details from {@code /api/speakers/{id}}, which includes the
	 * {@code jobTitle} field not available on the public endpoint.
	 */
	private volatile String apiToken;

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

}
