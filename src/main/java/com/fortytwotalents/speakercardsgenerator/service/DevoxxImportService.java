package com.fortytwotalents.speakercardsgenerator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Session;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.SessionRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 * profile pictures are refreshed on every import.
 */
@Service
public class DevoxxImportService {

	private static final Logger log = LoggerFactory.getLogger(DevoxxImportService.class);

	/**
	 * Allowlist of characters in a CFP event ID (alphanumeric, hyphen, underscore, ≤50
	 * chars).
	 */
	private static final Pattern SAFE_EVENT_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

	private final DevoxxApiConfig devoxxApiConfig;

	private final SpeakerRepository speakerRepository;

	private final SessionRepository sessionRepository;

	private final RestClient restClient;

	private final SpeakerPhotoStore photoStore;

	public DevoxxImportService(DevoxxApiConfig devoxxApiConfig, SpeakerRepository speakerRepository,
			SessionRepository sessionRepository, RestClient.Builder restClientBuilder, SpeakerPhotoStore photoStore) {
		this.devoxxApiConfig = devoxxApiConfig;
		this.speakerRepository = speakerRepository;
		this.sessionRepository = sessionRepository;
		this.restClient = configureBuilder(restClientBuilder).build();
		this.photoStore = photoStore;
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
		return builder.configureMessageConverters(converters -> converters.configureMessageConvertersList(list -> {
			list.removeIf(c -> c instanceof AbstractJacksonHttpMessageConverter);
			list.add(0, converter);
		}));
	}

	@Transactional
	public int importSpeakers() {
		return importSpeakers(devoxxApiConfig.getEventId());
	}

	/**
	 * Fetches speakers for the given CFP event ID.
	 * @param eventId CFP event identifier (e.g. {@code vdz27}); must contain only
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

		log.info("Importing speakers from CFP API for event {}", eventId);

		try {
			List<DevoxxSpeakerDto> speakers = fetchSpeakers(baseUrl);

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

	private List<DevoxxSpeakerDto> fetchSpeakers(String baseUrl) {
		List<DevoxxSpeakerDto> speakers = new ArrayList<>();
		for (int page = 0;; page++) {
			URI uri = URI.create(baseUrl + "/speakers?page=" + page + "&size=1000");
			var response = restClient.get().uri(uri).retrieve().toEntity(DevoxxSpeakerDto[].class);
			DevoxxSpeakerDto[] batch = response.getBody();
			if (batch == null || batch.length == 0) {
				return speakers;
			}
			speakers.addAll(List.of(batch));
			String links = response.getHeaders().getFirst("Link");
			if (links == null ? batch.length < 1000 : !links.contains("rel=\"next\"")) {
				return speakers;
			}
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
					saveSession(dto, eventId, cfpIdToUuid);
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

	private void saveSession(DevoxxTalkDto dto, String eventId, Map<Integer, UUID> cfpIdToUuid) {
		if (dto.id == null) {
			return;
		}

		Session session = sessionRepository.findById(dto.id).orElse(null);
		if (session == null) {
			session = new Session();
			session.setId(dto.id);
		}

		String title = blankToNull(dto.title);
		if (!Objects.equals(title, session.getTitle())) {
			session.setFormattedTitle(null);
		}
		session.setTitle(title);
		session.setDescription(blankToNull(dto.description));
		session.setWorkshop(dto.sessionType != null && dto.sessionType.isWorkshop());
		session.setSessionType(dto.sessionType == null ? null : blankToNull(dto.sessionType.name));

		// Extract scheduling from timeSlots if available
		DevoxxTimeSlotDto slot = dto.timeSlots == null || dto.timeSlots.isEmpty() ? null : dto.timeSlots.get(0);
		session.setDate(slot == null ? null : blankToNull(slot.date));
		session.setCetTime(slot == null ? null : blankToNull(slot.startTime));

		List<Speaker> sessionSpeakers = new ArrayList<>();
		if (dto.speakers != null) {
			for (DevoxxTalkSpeakerDto speakerRef : dto.speakers) {
				UUID speakerUuid = cfpIdToUuid.get(speakerRef.id);
				if (speakerUuid != null) {
					speakerRepository.findById(speakerUuid).ifPresent(sessionSpeakers::add);
				}
			}
		}
		session.setSpeakers(sessionSpeakers);

		sessionRepository.save(session);
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
			if (detail != null) {
				if (detail.jobTitle != null) {
					dto.jobTitle = detail.jobTitle;
				}
				if (blankToNull(detail.originalImageUrl) != null) {
					dto.originalImageUrl = detail.originalImageUrl;
				}
				dto.detailImageUrl = detail.imageUrl;
			}
		}
		catch (Exception e) {
			log.debug("Could not fetch authenticated speaker detail for id {}: {}", dto.id, e.getMessage());
		}
	}

	private boolean saveSpeaker(DevoxxSpeakerDto dto, String eventId) throws IOException {
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
		if (dto.jobTitle != null) {
			speaker.setTitle(blankToNull(dto.jobTitle));
		}
		speaker.setTwitterAccount(blankToNull(dto.twitterHandle));
		speaker.setLinkedInAccount(normaliseLinkedIn(dto.linkedInUsername));
		speaker.setBlueskyAccount(blankToNull(dto.blueskyUsername));
		speaker.setMastodonAccount(safeHttpsUrl(dto.mastodonUsername));
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

		if (blankToNull(dto.imageUrl) == null && blankToNull(dto.detailImageUrl) == null
				&& blankToNull(dto.originalImageUrl) == null) {
			photoStore.removeImportedPhoto(speakerId);
		}
		else {
			photoStore.download(speakerId, dto.originalImageUrl, dto.detailImageUrl, dto.imageUrl);
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
	 * CFP profiles are untrusted, so only absolute HTTPS URLs may become clickable links.
	 * Package-private for use in tests.
	 */
	static String safeHttpsUrl(String value) {
		String url = blankToNull(value);
		if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
			return null;
		}
		try {
			URI uri = URI.create(url);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? url : null;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
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

		/** Full-resolution portrait when supplied by the CFP. */
		public String originalImageUrl;

		private String detailImageUrl;

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

		public DevoxxSessionTypeDto sessionType;

		public List<DevoxxTalkSpeakerDto> speakers;

		public List<DevoxxTimeSlotDto> timeSlots;

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DevoxxSessionTypeDto {

		public String name;

		public String slug;

		boolean isWorkshop() {
			return (name != null && name.toLowerCase(Locale.ROOT).contains("workshop"))
					|| (slug != null && slug.toLowerCase(Locale.ROOT).contains("workshop"));
		}

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
