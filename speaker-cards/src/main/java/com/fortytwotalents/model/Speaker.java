package com.fortytwotalents.model;

import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;

/**
 * JPA entity representing a conference speaker.
 *
 * <p>Speakers are imported either via the Sessionize XLSX export
 * ({@code /api/import/csv}) or directly from the Devoxx CFP API
 * ({@code /api/import/devoxx}).
 */
@Entity
public class Speaker implements Comparable<Speaker> {

    /** Primary key – UUID assigned by the CFP tool. */
    @Id
    public UUID id;

    public String firstName;

    @NotBlank
    public String lastName;

    /** Professional title / tag-line (e.g. "Staff Engineer @ Acme"). */
    public String title;

    @JdbcTypeCode(Types.LONGVARCHAR)
    @NotBlank
    @Length(max = 10000)
    public String biography;

    public String company;

    @URL
    public String companyURL;

    @URL
    public String blogURL;

    public String twitterAccount;
    public String linkedInAccount;
    public String githubAccount;

    public String email;

    /**
     * External CFP system identifier used during import.
     * Retained for reference; no longer used for lookups.
     */
    public String importId;

    /**
     * Whether this speaker should be featured on the home page / main list.
     */
    public boolean star;

    @ManyToMany(mappedBy = "speakers")
    public List<Talk> talks = new ArrayList<>();

    public String phone;

    public Date lastUpdated;

    @PreUpdate
    @PrePersist
    public void prePersist() {
        lastUpdated = Date.from(Instant.now());
    }

    @Override
    public String toString() {
        return firstName + " " + lastName;
    }

    @Override
    public int compareTo(Speaker other) {
        return toString().compareTo(other.toString());
    }

    /**
     * Returns a newline-separated string with all talk titles formatted for
     * use in a Twitter/X post.
     *
     * @return formatted talk titles prefixed with a microphone emoji
     */
    public String getTalksForTwitter() {
        StringBuilder sb = new StringBuilder();
        for (Talk talk : talks) {
            sb.append("🎙️«").append(talk.title).append("»\n");
        }
        return sb.toString();
    }
}
