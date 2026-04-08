package com.fortytwotalents.speakercardsgenerator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * JPA entity representing a conference speaker.
 *
 * <p>
 * Speakers are imported either via the Sessionize XLSX export ({@code /api/import/csv})
 * or directly from the Devoxx CFP API ({@code /api/import/devoxx}).
 */
@Entity
public class Speaker implements Comparable<Speaker> {

	/** Primary key – UUID assigned by the CFP tool. */
	@Id
	private UUID id;

	private String firstName;

	@NotBlank
	private String lastName;

	/** Professional title / tag-line (e.g. "Staff Engineer @ Acme"). */
	private String title;

	@JdbcTypeCode(Types.LONGVARCHAR)
	@NotBlank
	@Length(max = 10000)
	private String biography;

	private String company;

	@URL
	private String companyURL;

	@URL
	private String blogURL;

	private String twitterAccount;

	private String linkedInAccount;

	private String blueskyAccount;

	private String mastodonAccount;

	private String githubAccount;

	private String email;

	/**
	 * External CFP system identifier used during import. Retained for reference; no
	 * longer used for lookups.
	 */
	private String importId;

	/** Whether this speaker should be featured on the home page / main list. */
	private boolean star;

	@ManyToMany(mappedBy = "speakers")
	private List<Talk> talks = new ArrayList<>();

	private String phone;

	private Date lastUpdated;

	@PreUpdate
	@PrePersist
	public void prePersist() {
		lastUpdated = Date.from(Instant.now());
	}

	@Override
	public String toString() {
		if (firstName == null || firstName.isBlank()) {
			return lastName;
		}
		return firstName + " " + lastName;
	}

	@Override
	public int compareTo(Speaker other) {
		return toString().compareTo(other.toString());
	}

	/**
	 * Returns a newline-separated string with all talk titles formatted for use in a
	 * Twitter/X post.
	 * @return formatted talk titles prefixed with a microphone emoji
	 */
	public String getTalksForTwitter() {
		StringBuilder sb = new StringBuilder();
		for (Talk talk : talks) {
			sb.append("🎙️«").append(talk.getTitle()).append("»\n");
		}
		return sb.toString();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBiography() {
		return biography;
	}

	public void setBiography(String biography) {
		this.biography = biography;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getCompanyURL() {
		return companyURL;
	}

	public void setCompanyURL(String companyURL) {
		this.companyURL = companyURL;
	}

	public String getBlogURL() {
		return blogURL;
	}

	public void setBlogURL(String blogURL) {
		this.blogURL = blogURL;
	}

	public String getTwitterAccount() {
		return twitterAccount;
	}

	public void setTwitterAccount(String twitterAccount) {
		this.twitterAccount = twitterAccount;
	}

	public String getLinkedInAccount() {
		return linkedInAccount;
	}

	public void setLinkedInAccount(String linkedInAccount) {
		this.linkedInAccount = linkedInAccount;
	}

	public String getBlueskyAccount() {
		return blueskyAccount;
	}

	public void setBlueskyAccount(String blueskyAccount) {
		this.blueskyAccount = blueskyAccount;
	}

	public String getMastodonAccount() {
		return mastodonAccount;
	}

	public void setMastodonAccount(String mastodonAccount) {
		this.mastodonAccount = mastodonAccount;
	}

	public String getGithubAccount() {
		return githubAccount;
	}

	public void setGithubAccount(String githubAccount) {
		this.githubAccount = githubAccount;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getImportId() {
		return importId;
	}

	public void setImportId(String importId) {
		this.importId = importId;
	}

	public boolean isStar() {
		return star;
	}

	public void setStar(boolean star) {
		this.star = star;
	}

	public List<Talk> getTalks() {
		return talks;
	}

	public void setTalks(List<Talk> talks) {
		this.talks = talks;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

}
