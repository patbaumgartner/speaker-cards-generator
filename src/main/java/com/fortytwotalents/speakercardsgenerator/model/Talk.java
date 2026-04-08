package com.fortytwotalents.speakercardsgenerator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * JPA entity representing a conference talk / session.
 *
 * <p>
 * Talks are associated with one or more {@link Speaker speakers} through a many-to-many
 * join table ({@code talk_speaker}).
 */
@Entity
public class Talk implements Comparable<Talk> {

	/** Primary key – session ID assigned by the CFP tool. */
	@Id
	private Long id;

	/** Session title. */
	private String title;

	@JdbcTypeCode(Types.LONGVARCHAR)
	@Length(max = 10000)
	private String description;

	/** Scheduled date in {@code yyyy-MM-dd} format (CET timezone). */
	private String date;

	/** Scheduled start time in CET timezone ({@code HH:mm}). */
	private String cetTime;

	/** Scheduled start time in EST timezone ({@code HH:mm}). */
	private String estTime;

	/** Scheduled duration in minutes (as a string). */
	private String scheduledDuration;

	/** Optional live-stream URL for this session. */
	@URL
	private String liveLink;

	/**
	 * Manually formatted title with HTML line-breaks ({@code <br>
	 * }) for custom text wrapping on banners. When {@code null}, the raw {@link #title}
	 * is used instead.
	 */
	@JdbcTypeCode(Types.LONGVARCHAR)
	@Length(max = 1000)
	private String formattedTitle;

	@JoinTable(name = "talk_speaker", joinColumns = @JoinColumn(name = "talk_id"),
			inverseJoinColumns = @JoinColumn(name = "speakers_id"))
	@ManyToMany
	private List<Speaker> speakers = new ArrayList<>();

	@Override
	public int compareTo(Talk other) {
		return Comparator.nullsLast(String::compareTo).compare(this.title, other.title);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getCetTime() {
		return cetTime;
	}

	public void setCetTime(String cetTime) {
		this.cetTime = cetTime;
	}

	public String getEstTime() {
		return estTime;
	}

	public void setEstTime(String estTime) {
		this.estTime = estTime;
	}

	public String getScheduledDuration() {
		return scheduledDuration;
	}

	public void setScheduledDuration(String scheduledDuration) {
		this.scheduledDuration = scheduledDuration;
	}

	public String getLiveLink() {
		return liveLink;
	}

	public void setLiveLink(String liveLink) {
		this.liveLink = liveLink;
	}

	public String getFormattedTitle() {
		return formattedTitle;
	}

	public void setFormattedTitle(String formattedTitle) {
		this.formattedTitle = formattedTitle;
	}

	public List<Speaker> getSpeakers() {
		return speakers;
	}

	public void setSpeakers(List<Speaker> speakers) {
		this.speakers = speakers;
	}

}
