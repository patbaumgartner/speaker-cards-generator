package com.fortytwotalents.speakercardsgenerator.config;

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
 * app.event.short-name=VDZ26
 * app.event.url=https://voxxeddays.com/zurich/
 * app.event.logo-file=event-tile.png
 * </pre>
 */
@ConfigurationProperties(prefix = "app.event")
public class EventConfig {

	/** Full display name shown on banners, e.g. "Voxxed Days Zürich". */
	private String name = "Voxxed Days";

	/** Short / abbreviated name shown on the banner badge, e.g. "VDZ26". */
	private String shortName = "VDZ26";

	/** Conference website URL shown in the social banner footer. */
	private String url = "https://voxxeddays.ch/";

	/**
	 * Filename (relative to {@code /static/images/}) of the event image tile used as
	 * background in the generated banners. Replace with your event's own tile image.
	 */
	private String logoFile = "event-tile.png";

	/** Event date displayed on banners, e.g. "March 25, 2026". */
	private String date = "";

	/** Event location displayed on banners, e.g. "Zürich, Switzerland". */
	private String location = "";

	/**
	 * Primary / dark brand colour used on banners (e.g. navy), as a CSS hex value.
	 */
	private String colorPrimary = "#1e2246";

	/**
	 * Accent / highlight brand colour used on banners (e.g. cyan), as a CSS hex value.
	 */
	private String colorAccent = "#40b4e5";

	/** Label shown above the talk title on the talk banner, e.g. "Session". */
	private String sessionLabel = "Session";

	/** Intro text shown above the talk title on the social banner. */
	private String speakingAboutLabel = "I am speaking about";

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

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getColorPrimary() {
		return colorPrimary;
	}

	public void setColorPrimary(String colorPrimary) {
		this.colorPrimary = colorPrimary;
	}

	public String getColorAccent() {
		return colorAccent;
	}

	public void setColorAccent(String colorAccent) {
		this.colorAccent = colorAccent;
	}

	public String getSessionLabel() {
		return sessionLabel;
	}

	public void setSessionLabel(String sessionLabel) {
		this.sessionLabel = sessionLabel;
	}

	public String getSpeakingAboutLabel() {
		return speakingAboutLabel;
	}

	public void setSpeakingAboutLabel(String speakingAboutLabel) {
		this.speakingAboutLabel = speakingAboutLabel;
	}

}
