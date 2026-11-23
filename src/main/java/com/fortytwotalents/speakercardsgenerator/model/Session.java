package com.fortytwotalents.speakercardsgenerator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * JPA entity representing a conference session.
 *
 * <p>
 * Sessions are associated with one or more {@link Speaker speakers} through the existing
 * {@code talk_speaker} join table. The table remains named {@code talk} for compatibility
 * with existing databases.
 */
@Entity
@Table(name = "talk")
public class Session implements Comparable<Session> {

	/** Primary key – session ID assigned by the CFP tool. */
	@Id
	private Long id;

	/** Session title. */
	private String title;

	@JdbcTypeCode(Types.LONGVARCHAR)
	@Length(max = 10000)
	private String description;

	private String sessionType;

	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean workshop;

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
	 *  }) for custom text wrapping on banners. When {@code null}, the raw {@link #title}
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
	public int compareTo(Session other) {
		int byTitle = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(this.title, other.title);
		if (byTitle != 0) {
			return byTitle;
		}
		return Comparator.nullsLast(Long::compareTo).compare(this.id, other.id);
	}

	@Override
	public boolean equals(Object candidate) {
		if (this == candidate) {
			return true;
		}
		if (!(candidate instanceof Session session)) {
			return false;
		}
		return this.id != null && this.id.equals(session.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.id);
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

	public String getSessionType() {
		return sessionType;
	}

	public void setSessionType(String sessionType) {
		this.sessionType = sessionType;
	}

	public String getSessionTypeLabel() {
		return workshop ? "Workshop" : sessionType;
	}

	public boolean isWorkshop() {
		return workshop;
	}

	public void setWorkshop(boolean workshop) {
		this.workshop = workshop;
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
