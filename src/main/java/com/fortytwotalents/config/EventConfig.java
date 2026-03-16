package com.fortytwotalents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conference / event branding configuration.
 *
 * <p>
 * All properties are bound from the {@code app.event.*} namespace. Override them per
 * Spring profile to support multiple events:
 *
 * <ul>
 * <li>{@code application-vdz.properties} – Voxxed Days Zürich
 * <li>{@code application-vdt.properties} – Voxxed Days Ticino
 * <li>{@code application-vdcern.properties} – Voxxed Days CERN
 * </ul>
 *
 * <p>
 * Example {@code application.properties}:
 *
 * <pre>
 * app.event.name=Voxxed Days Zürich
 * app.event.short-name=VDZ '26
 * app.event.url=https://voxxeddays.com/zurich/
 * app.event.logo-file=event-tile.png
 * </pre>
 */
@ConfigurationProperties(prefix = "app.event")
public class EventConfig {

	/** Full display name shown on banners, e.g. "Voxxed Days Zürich". */
	private String name = "Voxxed Days";

	/** Short / abbreviated name shown on the banner badge, e.g. "VDZ '26". */
	private String shortName = "VDZ '26";

	/** Conference website URL shown in the social banner footer. */
	private String url = "https://voxxeddays.com/";

	/**
	 * Filename (relative to {@code /static/images/}) of the event image tile used as
	 * background in the generated banners. Replace with your event's own tile image.
	 */
	private String logoFile = "event-tile.png";

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getLogoFile() {
		return logoFile;
	}

	public void setLogoFile(String logoFile) {
		this.logoFile = logoFile;
	}

}
