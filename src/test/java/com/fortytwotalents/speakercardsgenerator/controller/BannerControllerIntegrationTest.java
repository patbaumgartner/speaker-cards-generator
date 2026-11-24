package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Session;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.SessionRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import com.fortytwotalents.speakercardsgenerator.service.SpeakerPhotoStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the banner endpoints against the real Thymeleaf templates and the
 * real rendering pipeline.
 */
@SpringBootTest
@ActiveProfiles("test")
class BannerControllerIntegrationTest {

	private static final UUID SPEAKER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

	private static final UUID CO_SPEAKER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

	private static final long SESSION_ID = 4242L;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private SpeakerRepository speakerRepository;

	@Autowired
	private SessionRepository sessionRepository;

	@Autowired
	private BannerRenderer renderer;

	@Autowired
	private EventConfig eventConfig;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		this.sessionRepository.deleteAll();
		this.speakerRepository.deleteAll();

		Speaker speaker = speaker(SPEAKER_ID, "Jane", "Doe");
		Speaker coSpeaker = speaker(CO_SPEAKER_ID, "John", "Roe");
		this.speakerRepository.saveAll(List.of(speaker, coSpeaker));

		Session session = new Session();
		session.setId(SESSION_ID);
		session.setTitle("Building Cloud-Native Event-Driven Microservices with Spring Boot");
		session.setDate("2027-03-24");
		session.setCetTime("14:00");
		session.setSpeakers(new ArrayList<>(List.of(speaker, coSpeaker)));
		this.sessionRepository.save(session);
	}

	@Test
	void socialBannerToggleUsesTheEventTheme() throws Exception {
		String html = this.mockMvc.perform(get("/speaker-social/{id}", SPEAKER_ID))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).containsPattern("(?s)class=\"campaign-switch\".*?<rect[^>]*fill=\""
				+ java.util.regex.Pattern.quote(this.eventConfig.getColorSecondary()) + "\"");
	}

	@Test
	void speakerBannerPngIsAFullSizeImage() throws Exception {
		byte[] png = this.mockMvc.perform(get("/speaker-banner/{id}.png", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(image.getWidth()).isEqualTo(1920);
		assertThat(image.getHeight()).isEqualTo(1080);
	}

	@Test
	void socialBannerPngIsRendered() throws Exception {
		this.mockMvc.perform(get("/speaker-social/{id}.png", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void sessionBannerPngRendersEverySpeakerOfTheSession() throws Exception {
		// The session banner iterates its speakers, which is lazy. With
		// spring.jpa.open-in-view=false this only works because the repository fetches
		// the collection eagerly.
		byte[] png = this.mockMvc.perform(get("/session-banner/{id}.png", SESSION_ID))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
	}

	@Test
	void htmlPreviewsRenderTheEventBranding() throws Exception {
		this.mockMvc.perform(get("/speaker-banner/{id}", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Jane")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Staff Engineer")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("#TC27")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Tuesday, March 9, 2027")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"lighting\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("background-image: url(/speaker-photo/")));

		this.mockMvc.perform(get("/session-banner/{id}", SESSION_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Staff Engineer")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("background-image: url(/speaker-photo/")));
		this.mockMvc.perform(get("/speaker-social/{id}", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Staff Engineer")));
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void workshopAndConferenceBannersShowExpectedDatesOnEveryBanner(boolean workshop) throws Exception {
		Session session = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		session.setWorkshop(workshop);
		this.sessionRepository.save(session);

		for (String url : List.of("/speaker-banner/" + SPEAKER_ID, "/speaker-social/" + SPEAKER_ID,
				"/session-banner/" + SESSION_ID)) {
			String html = this.mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html.contains("class=\"session-type-label\"")).as(url).isEqualTo(workshop);
			String date = workshop ? "Monday, March 8, 2027" : "Tuesday, March 9, 2027";
			assertThat(html).as(url).containsPattern("class=\"event-date\"[^>]*>" + date + "</div>");
			if (workshop) {
				assertThat(html).as(url)
					.contains("class=\"event-secondary-date\">Conference Day: Tuesday, March 9, 2027</div>")
					.doesNotContain("Workshop Day:");
				this.mockMvc.perform(get(url + ".png"))
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_PNG));
			}
			else {
				assertThat(html).as(url).doesNotContain("Monday, March 8, 2027", "class=\"event-secondary-date\"");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "Conference", "Byte-Size" })
	void conferenceAndByteSizeBannersShowOnlyConferenceDate(String sessionType) throws Exception {
		Session session = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		session.setSessionType(sessionType);
		this.sessionRepository.save(session);

		for (String url : List.of("/speaker-banner/" + SPEAKER_ID, "/speaker-social/" + SPEAKER_ID,
				"/session-banner/" + SESSION_ID)) {
			String html = this.mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html).as(url)
				.containsPattern("class=\"event-date\"[^>]*>Tuesday, March 9, 2027</div>")
				.doesNotContain("Monday, March 8, 2027", "class=\"event-secondary-date\"");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "Conference", "Byte-Size", "Keynote", "<Special & Session>" })
	void sessionTypeAppearsBelowTitleOnEveryBanner(String sessionType) throws Exception {
		Session session = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		session.setSessionType(sessionType);
		this.sessionRepository.save(session);

		for (String url : List.of("/speaker-banner/" + SPEAKER_ID, "/speaker-social/" + SPEAKER_ID,
				"/session-banner/" + SESSION_ID)) {
			String html = this.mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			String expected = sessionType.equals("<Special & Session>") ? "&lt;Special &amp; Session&gt;" : sessionType;
			assertThat(html).as(url).contains("class=\"session-type-label\">" + expected + "</div>");
			int titleIndex = html.indexOf("id=\"session-title\"");
			if (titleIndex < 0) {
				titleIndex = html.indexOf("class=\"session-title\"");
			}
			assertThat(titleIndex).as(url).isNotNegative();
			assertThat(html.indexOf("class=\"session-type-label\"")).as(url).isGreaterThan(titleIndex);
			if (sessionType.equals("Byte-Size")) {
				this.mockMvc.perform(get(url + ".png"))
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_PNG));
			}
		}
	}

	@Test
	void speakerPhotoFallsBackToTheDefaultAvatarInsteadOfA404() throws Exception {
		byte[] body = this.mockMvc.perform(get("/speaker-photo/{id}", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertThat(body).isEqualTo(SpeakerPhotoStore.defaultPhoto().content());
	}

	@Test
	void defaultSpeakerPhotoEndpointServesTheBrandedAvatar() throws Exception {
		byte[] body = this.mockMvc.perform(get("/speaker-photo"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertThat(body).isEqualTo(SpeakerPhotoStore.defaultPhoto().content());
	}

	@Test
	void unknownIdsReturnNotFound() throws Exception {
		this.mockMvc.perform(get("/speaker-banner/{id}.png", UUID.randomUUID())).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/session-banner/{id}.png", 999999L)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/speaker-photo/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void legacyTalkBannerRoutesAreRemoved() throws Exception {
		this.mockMvc.perform(get("/talk-banner/{id}", SESSION_ID)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/talk-banner/{id}.png", SESSION_ID)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/banners/download/talks")).andExpect(status().isNotFound());
		this.mockMvc
			.perform(put("/api/talks/{id}/formatted-title", SESSION_ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"formattedTitle\":\"Session<br/>title\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void sessionTitleFormattingUsesTheRenamedApi() throws Exception {
		this.mockMvc
			.perform(put("/api/sessions/{id}/formatted-title", SESSION_ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"formattedTitle\":\"Session<br/>title\"}"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"formattedTitle\":\"Session<br/>title\"}"));
		assertThat(this.sessionRepository.findById(SESSION_ID).orElseThrow().getFormattedTitle())
			.isEqualTo("Session<br/>title");
	}

	@Test
	void downloadAllProducesAZipWithEverySpeakerSessionAndSocialBanner() throws Exception {
		Set<String> entries = zipEntries("/api/banners/download/all");
		Session session = this.sessionRepository.findWithSpeakersById(SESSION_ID).orElseThrow();

		assertThat(entries).containsExactlyInAnyOrder(
				this.renderer.fileName(BannerType.SPEAKER, session.getSpeakers().get(0), session),
				this.renderer.fileName(BannerType.SOCIAL, session.getSpeakers().get(0), session),
				this.renderer.fileName(BannerType.SPEAKER, session.getSpeakers().get(1), session),
				this.renderer.fileName(BannerType.SOCIAL, session.getSpeakers().get(1), session),
				this.renderer.fileName(BannerType.SESSION, null, session));
	}

	@Test
	void downloadAllStillIncludesSessionsWhenTwoSpeakersShareAName() throws Exception {
		this.speakerRepository.save(speaker(UUID.randomUUID(), "Jane", "Doe"));

		Session session = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		assertThat(zipEntries("/api/banners/download/all")).hasSize(7)
			.contains(this.renderer.fileName(BannerType.SESSION, null, session));
	}

	@Test
	void downloadSessionsProducesOneEntryPerSessionNotOnePerSpeaker() throws Exception {
		Session session = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		assertThat(zipEntries("/api/banners/download/sessions"))
			.containsExactly(this.renderer.fileName(BannerType.SESSION, null, session));
	}

	@Test
	void indexOffersAllBannerVariantsForEverySession() throws Exception {
		addWorkshop();
		Session conference = this.sessionRepository.findById(SESSION_ID).orElseThrow();
		conference.setSessionType("Conference");
		this.sessionRepository.save(conference);
		this.speakerRepository.save(speaker(UUID.randomUUID(), "No", "Sessions"));
		String html = this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("Conference", "Workshop", "Hands-on Java", "General speaker banners")
			.doesNotContain("speaker-" + SPEAKER_ID + ".png");
		for (long sessionId : List.of(SESSION_ID, 4243L)) {
			for (String suffix : List.of("", ".png")) {
				assertThat(html).contains(
						"href=\"/speaker-banner/" + SPEAKER_ID + suffix + "?sessionId=" + sessionId + "\"",
						"href=\"/speaker-social/" + SPEAKER_ID + suffix + "?sessionId=" + sessionId + "\"",
						"href=\"/session-banner/" + sessionId + suffix + "\"");
			}
		}
	}

	@Test
	void speakerVariantsRenderTheSelectedSessionAndUseMatchingDownloadNames() throws Exception {
		Session workshop = addWorkshop();
		for (String route : List.of("speaker-banner", "speaker-social")) {
			String html = this.mockMvc.perform(get("/" + route + "/" + SPEAKER_ID).param("sessionId", "4243"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html)
				.contains("Hands-on Java", "Monday, March 8, 2027", "Conference Day: Tuesday, March 9, 2027",
						"Workshop", "Staff Engineer")
				.doesNotContain("Building Cloud-Native");
		}

		Speaker speaker = this.speakerRepository.findById(SPEAKER_ID).orElseThrow();
		for (BannerType type : BannerType.values()) {
			String route = switch (type) {
				case SPEAKER -> "/speaker-banner/" + SPEAKER_ID;
				case SOCIAL -> "/speaker-social/" + SPEAKER_ID;
				case SESSION -> "/session-banner/4243";
			};
			var response = this.mockMvc.perform(get(route + ".png").param("sessionId", "4243"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andReturn()
				.getResponse();
			String filename = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
				.getFilename();
			assertThat(filename)
				.isEqualTo(this.renderer.downloadFileName(type, type == BannerType.SESSION ? null : speaker, workshop));
			assertThat(ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()))).isNotNull();
		}
	}

	@Test
	void speakerVariantsRejectSessionsThatDoNotBelongToTheSpeaker() throws Exception {
		addWorkshop();
		for (String route : List.of("speaker-banner", "speaker-social")) {
			for (String suffix : List.of("", ".png")) {
				this.mockMvc.perform(get("/" + route + "/" + CO_SPEAKER_ID + suffix).param("sessionId", "4243"))
					.andExpect(status().isNotFound());
				this.mockMvc.perform(get("/" + route + "/" + SPEAKER_ID + suffix).param("sessionId", "999999"))
					.andExpect(status().isNotFound());
			}
		}
	}

	@Test
	void downloadSpeakersAndSocialAreScopedToTheirOwnBannerType() throws Exception {
		assertThat(zipEntries("/api/banners/download/speakers"))
			.allSatisfy(name -> assertThat(name).startsWith("speaker/"));
		assertThat(zipEntries("/api/banners/download/social"))
			.allSatisfy(name -> assertThat(name).startsWith("social/"));
	}

	@Test
	void talkSpeakersAreLazyWithoutTheEntityGraph() {
		// Pins the reason findAllWithSpeakersBy() exists. Session.speakers is a lazy
		// @ManyToMany and these endpoints run with spring.jpa.open-in-view=false, so a
		// plain findAll() hands back detached entities whose speaker list explodes on
		// first touch. The ZIP writer used to swallow that exception per entry, which
		// meant session banners were silently missing from every archive.
		Session detached = this.sessionRepository.findAll().get(0);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> detached.getSpeakers().size())
			.isInstanceOf(org.hibernate.LazyInitializationException.class);

		assertThat(this.sessionRepository.findAllWithSpeakersBy().get(0).getSpeakers()).hasSize(2);
	}

	@Test
	void indexPageListsTheSpeakers() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Doe")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Test Conference")))
			.andExpect(
					content().string(org.hamcrest.Matchers.containsString("<main class=\"main-content\" id=\"main\">")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"sortable-btn\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("aria-sort=\"none\"")))
			.andExpect(content().string(
					org.hamcrest.Matchers.matchesPattern("(?s).*<input\\s+type=\"text\"[^>]*id=\"apiTokenInput\".*")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("api/public/speakers")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Voxxed Days Switzerland")));
	}

	private Set<String> zipEntries(String url) throws Exception {
		byte[] body = this.mockMvc.perform(get(url))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		Set<String> names = new LinkedHashSet<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				assertThat(zip.readAllBytes()).as("content of %s", entry.getName()).isNotEmpty();
				names.add(entry.getName());
			}
		}
		return names;
	}

	private Session addWorkshop() {
		Session workshop = new Session();
		workshop.setId(4243L);
		workshop.setTitle("Hands-on Java");
		workshop.setWorkshop(true);
		workshop.setSessionType("Hands-on Workshop");
		workshop.setSpeakers(new ArrayList<>(List.of(this.speakerRepository.findById(SPEAKER_ID).orElseThrow())));
		return this.sessionRepository.save(workshop);
	}

	private static Speaker speaker(UUID id, String firstName, String lastName) {
		Speaker speaker = new Speaker();
		speaker.setId(id);
		speaker.setFirstName(firstName);
		speaker.setLastName(lastName);
		speaker.setBiography("Speaks about things.");
		speaker.setCompany("Acme");
		speaker.setTitle("Staff Engineer");
		return speaker;
	}

}
