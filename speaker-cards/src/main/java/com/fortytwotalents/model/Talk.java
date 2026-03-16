package com.fortytwotalents.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * JPA entity representing a conference talk / session.
 *
 * <p>Talks are associated with one or more {@link Speaker speakers} through a many-to-many join
 * table ({@code talk_speaker}).
 */
@Entity
public class Talk implements Comparable<Talk> {

  /** Primary key – session ID assigned by the CFP tool. */
  @Id public Long id;

  /** Session title. */
  public String title;

  @JdbcTypeCode(Types.LONGVARCHAR)
  @Length(max = 10000)
  public String description;

  /** Scheduled date in {@code yyyy-MM-dd} format (CET timezone). */
  public String date;

  /** Scheduled start time in CET timezone ({@code HH:mm}). */
  public String cetTime;

  /** Scheduled start time in EST timezone ({@code HH:mm}). */
  public String estTime;

  /** Scheduled duration in minutes (as a string). */
  public String scheduledDuration;

  /** Optional live-stream URL for this session. */
  @URL public String liveLink;

  @JoinTable(
      name = "talk_speaker",
      joinColumns = @JoinColumn(name = "talk_id"),
      inverseJoinColumns = @JoinColumn(name = "speakers_id"))
  @ManyToMany
  public List<Speaker> speakers = new ArrayList<>();

  @Override
  public int compareTo(Talk other) {
    return this.title.compareTo(other.title);
  }
}
