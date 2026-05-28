package com.fortytwotalents.speakercardsgenerator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Imports speakers from the Voxxed Days / CFP public API.
 *
 * <p>
 * The API endpoint format is:
 *
 * <pre>
 *   GET https://{eventId}.cfp.dev/api/public/speakers
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
	 * Allowlist of characters in a CFP event ID (alphanumeric, hyphen, underscore, ≤50
	 * chars).
	 */
	private static final Pattern SAFE_EVENT_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

	/**
	 * URL schemes allowed when downloading profile pictures from external sources.
	 */
	private static final Set<String> ALLOWED_PICTURE_SCHEMES = Set.of("http", "https");

	private final DevoxxApiConfig devoxxApiConfig;

	private final SpeakerRepository speakerRepository;

	private final TalkRepository talkRepository;

	private final RestClient restClient;

	public DevoxxImportService(DevoxxApiConfig devoxxApiConfig, SpeakerRepository speakerRepository,
			TalkRepository talkRepository, RestClient.Builder restClientBuilder) {
		this.devoxxApiConfig = devoxxApiConfig;
		this.speakerRepository = speakerRepository;
		this.talkRepository = talkRepository;
		this.restClient = configureBuilder(restClientBuilder).build();
	}

	/**
	 * Configures the given {@link RestClient.Builder} to add a
	 * {@link JacksonJsonHttpMessageConverter} that also accepts {@code text/html}
	 * responses. Some CFP API endpoints return JSON with a {@code text/html} content-type
	 * header; the additional media-type mapping allows Jackson to deserialise such
	 * responses correctly.
	 * <p>
	 * Package-private for use in tests.
	 */
	static RestClient.Builder configureBuilder(RestClient.Builder builder) {
		JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
		List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
		mediaTypes.add(MediaType.TEXT_HTML);
		converter.setSupportedMediaTypes(mediaTypes);
		return builder.messageConverters(converters -> {
			converters.removeIf(c -> c instanceof AbstractJacksonHttpMessageConverter);
			converters.add(0, converter);
		});
	}

	@Transactional
	public int importSpeakers() {
		if (!devoxxApiConfig.isDevoxxApiEnabled()) {
			throw new IllegalStateException(
					"Devoxx CFP API is not configured for this event profile (event-id is none/empty).");
		}
		return importSpeakers(devoxxApiConfig.getEventId());
	}

	/**
	 * Fetches speakers for the given CFP event ID.
	 * @param eventId CFP event identifier (e.g. {@code vdz26}); must contain only
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

		String baseUrl = "https://" + eventId + ".cfp.dev/api/public";

		// Fetch all speakers (request large page to avoid pagination)
		URI speakersUri = URI.create(baseUrl + "/speakers?page=0&size=1000");
		log.info("Importing speakers from CFP API: {}", speakersUri);

		try {
			DevoxxSpeakerDto[] speakers = restClient.get().uri(speakersUri).retrieve().body(DevoxxSpeakerDto[].class);

			if (speakers == null || speakers.length == 0) {
				log.warn("No speakers returned from CFP API for event: {}", eventId);
				return 0;
			}

			// When an API token is configured, enrich each speaker with data
			// from the authenticated endpoint (which includes jobTitle)
			String apiToken = devoxxApiConfig.getApiToken();
			boolean useAuthApi = apiToken != null && !apiToken.isBlank();

			// Save speakers and build a map of CFP integer ID → Speaker UUID
			Map<Integer, UUID> cfpIdToUuid = new HashMap<>();
			int count = 0;
			for (DevoxxSpeakerDto dto : speakers) {
				try {
					if (useAuthApi && dto.id != null) {
						enrichSpeakerFromAuthApi(dto, eventId, apiToken);
					}
					if (saveSpeaker(dto, eventId)) {
						count++;
						if (dto.id != null) {
							cfpIdToUuid.put(dto.id, resolveSpeakerId(dto, eventId));
						}
					}
				}
				catch (Exception e) {
					log.error("Error saving speaker {} {}: {}", dto.firstName, dto.lastName, e.getMessage(), e);
				}
			}
			log.info("Imported {} speakers from CFP API for event {}", count, eventId);

			// Fetch and import talks
			importTalks(baseUrl, eventId, cfpIdToUuid);

			return count;

		}
		catch (Exception e) {
			log.error("Failed to import speakers from CFP API: {}", e.getMessage(), e);
			throw new RuntimeException("CFP API import failed for event " + eventId, e);
		}
	}

	private void importTalks(String baseUrl, String eventId, Map<Integer, UUID> cfpIdToUuid) {
		URI talksUri = URI.create(baseUrl + "/talks");
		log.info("Importing talks from CFP API: {}", talksUri);

		try {
			DevoxxTalkDto[] talks = restClient.get().uri(talksUri).retrieve().body(DevoxxTalkDto[].class);

			if (talks == null || talks.length == 0) {
				log.warn("No talks returned from CFP API for event: {}", eventId);
				return;
			}

			int talkCount = 0;
			for (DevoxxTalkDto dto : talks) {
				try {
					saveTalk(dto, eventId, cfpIdToUuid);
					talkCount++;
				}
				catch (Exception e) {
					log.error("Error saving talk '{}': {}", dto.title, e.getMessage(), e);
				}
			}
			log.info("Imported {} talks from CFP API for event {}", talkCount, eventId);
		}
		catch (Exception e) {
			log.error("Failed to import talks from CFP API: {}", e.getMessage(), e);
		}
	}

	private void saveTalk(DevoxxTalkDto dto, String eventId, Map<Integer, UUID> cfpIdToUuid) {
		if (dto.id == null) {
			return;
		}

		Talk talk = talkRepository.findById(dto.id).orElse(null);
		if (talk == null) {
			talk = new Talk();
			talk.setId(dto.id);
		}

		talk.setTitle(blankToNull(dto.title));
		talk.setDescription(blankToNull(dto.description));

		// Extract scheduling from timeSlots if available
		if (dto.timeSlots != null && !dto.timeSlots.isEmpty()) {
			DevoxxTimeSlotDto slot = dto.timeSlots.get(0);
			talk.setDate(blankToNull(slot.date));
			talk.setCetTime(blankToNull(slot.startTime));
		}

		// Link speakers to this talk
		if (dto.speakers != null) {
			List<Speaker> talkSpeakers = new ArrayList<>();
			for (DevoxxTalkSpeakerDto speakerRef : dto.speakers) {
				UUID speakerUuid = cfpIdToUuid.get(speakerRef.id);
				if (speakerUuid != null) {
					speakerRepository.findById(speakerUuid).ifPresent(talkSpeakers::add);
				}
			}
			talk.setSpeakers(talkSpeakers);
		}

		talkRepository.save(talk);
	}

	/**
	 * Enriches a speaker DTO with fields from the authenticated CFP API (e.g.
	 * {@code jobTitle}) that are not available on the public endpoint.
	 */
	private void enrichSpeakerFromAuthApi(DevoxxSpeakerDto dto, String eventId, String apiToken) {
		URI uri = URI.create("https://" + eventId + ".cfp.dev/api/speakers/" + dto.id);
		try {
			DevoxxSpeakerDto detail = restClient.get()
				.uri(uri)
				.header("Authorization", "Bearer " + apiToken)
				.retrieve()
				.body(DevoxxSpeakerDto.class);
			if (detail != null && detail.jobTitle != null && !detail.jobTitle.isBlank()) {
				dto.jobTitle = detail.jobTitle;
			}
		}
		catch (Exception e) {
			log.debug("Could not fetch authenticated speaker detail for id {}: {}", dto.id, e.getMessage());
		}
	}

	private boolean saveSpeaker(DevoxxSpeakerDto dto, String eventId) {
		UUID speakerId = resolveSpeakerId(dto, eventId);
		if (speakerId == null) {
			return false;
		}

		Speaker speaker = speakerRepository.findById(speakerId).orElse(null);
		boolean isNew = speaker == null;

		if (isNew) {
			speaker = new Speaker();
			speaker.setId(speakerId);
		}

		speaker.setFirstName(blankToNull(dto.firstName));
		speaker.setLastName(blankToNull(dto.lastName));
		speaker.setBiography(blankToNull(dto.bio));
		speaker.setCompany(blankToNull(dto.company));
		speaker.setTitle(blankToNull(dto.jobTitle));
		speaker.setTwitterAccount(blankToNull(dto.twitterHandle));
		speaker.setLinkedInAccount(normaliseLinkedIn(dto.linkedInUsername));
		speaker.setBlueskyAccount(blankToNull(dto.blueskyUsername));
		speaker.setMastodonAccount(blankToNull(dto.mastodonUsername));
		speaker.setBlogURL(blankToNull(dto.blog));

		if (speaker.getBiography() == null) {
			speaker.setBiography("(no biography provided)");
		}
		if (speaker.getLastName() == null) {
			speaker.setLastName("(unknown)");
		}

		speakerRepository.save(speaker);
		log.atDebug()
			.addArgument(() -> isNew ? "Saved" : "Updated")
			.addArgument(speaker.getFirstName())
			.addArgument(speaker.getLastName())
			.addArgument(speakerId)
			.log("{} speaker: {} {} ({})");

		if (dto.imageUrl != null && !dto.imageUrl.isBlank()) {
			downloadProfilePicture(speakerId, dto.imageUrl);
		}
		return true;
	}

	/**
	 * Resolves the speaker UUID from the DTO. Supports both the legacy {@code uuid} field
	 * and the current {@code id} (integer) field. When only an integer ID is available, a
	 * deterministic UUID is generated using the event ID as namespace.
	 */
	private static UUID resolveSpeakerId(DevoxxSpeakerDto dto, String eventId) {
		if (dto.uuid != null && !dto.uuid.isBlank()) {
			try {
				return UUID.fromString(dto.uuid);
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid UUID '{}' for speaker {} {}", dto.uuid, dto.firstName, dto.lastName);
			}
		}
		if (dto.id != null) {
			String seed = eventId + ":" + dto.id;
			return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
		}
		log.warn("Skipping speaker with no id: {} {}", dto.firstName, dto.lastName);
		return null;
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
		return value == null || value.isBlank() ? null : value.trim();
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
			Path dir = Path.of("src/main/resources/static/images/speaker");
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
	 * JSON DTO for a speaker returned by the CFP public API. Unknown fields are silently
	 * ignored to remain forward-compatible.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxSpeakerDto {

		/** Legacy UUID field (older CFP API versions). */
		public String uuid;

		/** Numeric speaker ID (current CFP API). */
		public Integer id;

		public String firstName;

		public String lastName;

		public String bio;

		public String company;

		/** Job title / professional role. */
		public String jobTitle;

		/** Profile picture URL. */
		public String imageUrl;

		/** Twitter / X handle. */
		@JsonProperty("twitterHandle")
		public String twitterHandle;

		/** LinkedIn username. */
		@JsonProperty("linkedInUsername")
		public String linkedInUsername;

		/** Bluesky username. */
		@JsonProperty("blueskyUsername")
		public String blueskyUsername;

		/** Mastodon username. */
		@JsonProperty("mastodonUsername")
		public String mastodonUsername;

		/** Personal blog or website URL. */
		public String blog;

	}

	/** JSON DTO for a talk returned by the CFP public API. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxTalkDto {

		public Long id;

		public String title;

		public String description;

		public List<DevoxxTalkSpeakerDto> speakers;

		public List<DevoxxTimeSlotDto> timeSlots;

	}

	/** Embedded speaker reference within a talk DTO. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxTalkSpeakerDto {

		public Integer id;

	}

	/** Time slot information for a scheduled talk. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxTimeSlotDto {

		public String date;

		public String startTime;

		public String endTime;

	}

}
