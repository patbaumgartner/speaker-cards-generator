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
	 * build the CFP API URL as {@code https://{eventId}.cfp.dev/api/public/speakers}.
	 */
	private String eventId = "vdz26";

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

}
