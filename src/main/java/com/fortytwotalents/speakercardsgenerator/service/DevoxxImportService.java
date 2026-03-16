package com.fortytwotalents.speakercardsgenerator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Imports speakers from the Devoxx / Voxxed Days mobile CFP API.
 *
 * <p>
 * The API endpoint format is:
 *
 * <pre>
 *   GET {baseUrl}/events/{eventId}/speakers
 * </pre>
 *
 * <p>
 * The import is idempotent: existing speakers (matched by UUID) are updated in place;
 * profile pictures are downloaded once and cached.
 */
@Service
public class DevoxxImportService {

	private static final Logger log = LoggerFactory.getLogger(DevoxxImportService.class);

	/**
	 * Allowlist of characters in a Devoxx event ID (alphanumeric, hyphen, underscore, ≤50
	 * chars).
	 */
	private static final Pattern SAFE_EVENT_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

	/** URL schemes allowed when downloading profile pictures from external sources. */
	private static final Set<String> ALLOWED_PICTURE_SCHEMES = Set.of("http", "https");

	private final DevoxxApiConfig devoxxApiConfig;

	private final SpeakerRepository speakerRepository;

	private final RestClient restClient;

	public DevoxxImportService(DevoxxApiConfig devoxxApiConfig, SpeakerRepository speakerRepository) {
		this.devoxxApiConfig = devoxxApiConfig;
		this.speakerRepository = speakerRepository;
		this.restClient = RestClient.create();
	}

	@Transactional
	public int importSpeakers() {
		return importSpeakers(devoxxApiConfig.getEventId());
	}

	/**
	 * Fetches speakers for the given Devoxx event ID.
	 * @param eventId Devoxx event identifier (e.g. {@code vdz26}); must contain only
	 * alphanumeric characters, hyphens, or underscores (max 50 characters)
	 * @return number of speakers imported or updated
	 * @throws IllegalArgumentException if the eventId does not match the safe pattern
	 */
	@Transactional
	public int importSpeakers(String eventId) {
		if (eventId == null || !SAFE_EVENT_ID.matcher(eventId).matches()) {
			throw new IllegalArgumentException(
					"Invalid event ID – must be alphanumeric/hyphen/underscore, max 50 chars: " + eventId);
		}

		// eventId is validated above – safe to concatenate directly
		URI uri = URI.create(devoxxApiConfig.getBaseUrl() + "/events/" + eventId + "/speakers");
		log.info("Importing speakers from Devoxx API: {}", uri);

		try {
			DevoxxSpeakerDto[] speakers = restClient.get().uri(uri).retrieve().body(DevoxxSpeakerDto[].class);

			if (speakers == null || speakers.length == 0) {
				log.warn("No speakers returned from Devoxx API for event: {}", eventId);
				return 0;
			}

			int count = 0;
			for (DevoxxSpeakerDto dto : speakers) {
				try {
					saveSpeaker(dto);
					count++;
				}
				catch (Exception e) {
					log.error("Error saving speaker {} {}: {}", dto.firstName, dto.lastName, e.getMessage(), e);
				}
			}
			log.info("Imported {} speakers from Devoxx API for event {}", count, eventId);
			return count;

		}
		catch (Exception e) {
			log.error("Failed to import speakers from Devoxx API: {}", e.getMessage(), e);
			throw new RuntimeException("Devoxx API import failed for event " + eventId, e);
		}
	}

	private void saveSpeaker(DevoxxSpeakerDto dto) {
		if (dto.uuid == null || dto.uuid.isBlank()) {
			log.warn("Skipping speaker with blank UUID: {} {}", dto.firstName, dto.lastName);
			return;
		}

		UUID speakerId;
		try {
			speakerId = UUID.fromString(dto.uuid);
		}
		catch (IllegalArgumentException e) {
			log.warn("Invalid UUID '{}' for speaker {} {}", dto.uuid, dto.firstName, dto.lastName);
			return;
		}

		Speaker speaker = speakerRepository.findById(speakerId).orElse(null);
		boolean isNew = speaker == null;

		if (isNew) {
			speaker = new Speaker();
			speaker.id = speakerId;
		}

		speaker.firstName = blankToNull(dto.firstName);
		speaker.lastName = blankToNull(dto.lastName);
		speaker.biography = blankToNull(dto.bio);
		speaker.company = blankToNull(dto.company);
		speaker.twitterAccount = blankToNull(dto.twitter);
		speaker.linkedInAccount = normaliseLinkedIn(dto.linkedIn);
		speaker.blogURL = blankToNull(dto.blog);

		if (speaker.biography == null) {
			speaker.biography = "(no biography provided)";
		}
		if (speaker.lastName == null) {
			speaker.lastName = "(unknown)";
		}

		speakerRepository.save(speaker);
		log.debug("{} speaker: {} {} ({})", isNew ? "Saved" : "Updated", speaker.firstName, speaker.lastName,
				speakerId);

		if (dto.imageUrl != null && !dto.imageUrl.isBlank()) {
			downloadProfilePicture(speakerId, dto.imageUrl);
		}
	}

	/**
	 * Extracts just the LinkedIn handle from a full URL if necessary. Returns
	 * {@code null} if the input is blank.
	 */
	private static String normaliseLinkedIn(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		// Strip trailing slash then extract last path segment if it's a URL
		String trimmed = value.trim().replaceAll("/$", "");
		if (trimmed.contains("/")) {
			return trimmed.substring(trimmed.lastIndexOf('/') + 1);
		}
		return trimmed;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	/**
	 * Downloads a profile picture from the given URL and stores it under
	 * {@code src/main/resources/static/images/speaker/{speakerId}.{ext}}.
	 *
	 * <p>
	 * Only {@code http} and {@code https} URL schemes are permitted to prevent SSRF
	 * attacks.
	 * @param speakerId speaker UUID (used as file name)
	 * @param pictureUrl URL of the profile image
	 */
	private void downloadProfilePicture(UUID speakerId, String pictureUrl) {
		try {
			URI uri = URI.create(pictureUrl);
			String scheme = uri.getScheme();
			if (scheme == null || !ALLOWED_PICTURE_SCHEMES.contains(scheme.toLowerCase())) {
				log.warn("Skipping profile picture download for speaker {}: disallowed URL scheme in '{}'", speakerId,
						pictureUrl);
				return;
			}

			String extension = deriveExtension(pictureUrl);
			Path dir = Paths.get("src/main/resources/static/images/speaker");
			Files.createDirectories(dir);

			Path target = dir.resolve(speakerId + "." + extension);
			if (Files.exists(target)) {
				log.debug("Profile picture already exists for speaker {}, skipping", speakerId);
				return;
			}
			log.info("Downloading profile picture for speaker {} from {}", speakerId, pictureUrl);
			try (InputStream in = uri.toURL().openStream()) {
				Files.copy(in, target);
				log.info("Downloaded profile picture to {}", target);
			}
		}
		catch (Exception e) {
			log.error("Error downloading profile picture for speaker {} from {}", speakerId, pictureUrl, e);
		}
	}

	private static String deriveExtension(String url) {
		int lastDot = url.lastIndexOf('.');
		if (lastDot > 0) {
			String ext = url.substring(lastDot + 1).toLowerCase();
			int query = ext.indexOf('?');
			if (query > 0) {
				ext = ext.substring(0, query);
			}
			if (List.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
				return ext;
			}
		}
		return "jpg";
	}

	/**
	 * JSON DTO for a speaker returned by the Devoxx mobile API. Unknown fields are
	 * silently ignored to remain forward-compatible.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxSpeakerDto {

		public String uuid;

		public String firstName;

		public String lastName;

		public String bio;

		public String company;

		/** Profile picture URL. */
		public String imageUrl;

		/** Twitter / X handle (without the {@literal @} prefix). */
		public String twitter;

		/** LinkedIn profile URL or handle. */
		public String linkedIn;

		/** Personal blog or website URL. */
		public String blog;

	}

}
