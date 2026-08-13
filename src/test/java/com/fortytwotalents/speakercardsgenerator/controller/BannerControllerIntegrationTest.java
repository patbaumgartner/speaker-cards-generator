package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

	private static final long TALK_ID = 4242L;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private SpeakerRepository speakerRepository;

	@Autowired
	private TalkRepository talkRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
		this.talkRepository.deleteAll();
		this.speakerRepository.deleteAll();

		Speaker speaker = speaker(SPEAKER_ID, "Jane", "Doe");
		Speaker coSpeaker = speaker(CO_SPEAKER_ID, "John", "Roe");
		this.speakerRepository.saveAll(List.of(speaker, coSpeaker));

		Talk talk = new Talk();
		talk.setId(TALK_ID);
		talk.setTitle("Building Cloud-Native Event-Driven Microservices with Spring Boot");
		talk.setDate("2026-03-24");
		talk.setCetTime("14:00");
		talk.setSpeakers(new ArrayList<>(List.of(speaker, coSpeaker)));
		this.talkRepository.save(talk);
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
	void talkBannerPngRendersEverySpeakerOfTheSession() throws Exception {
		// The talk banner iterates talk.speakers, which is lazy. With
		// spring.jpa.open-in-view=false this only works because the repository fetches
		// the collection eagerly.
		byte[] png = this.mockMvc.perform(get("/talk-banner/{id}.png", TALK_ID))
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
			.andExpect(content().string(org.hamcrest.Matchers.containsString("#TC26")));

		this.mockMvc.perform(get("/talk-banner/{id}", TALK_ID)).andExpect(status().isOk());
		this.mockMvc.perform(get("/speaker-social/{id}", SPEAKER_ID)).andExpect(status().isOk());
	}

	@Test
	void speakerPhotoFallsBackToThePlaceholderInsteadOfA404() throws Exception {
		this.mockMvc.perform(get("/speaker-photo/{id}", SPEAKER_ID))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void unknownIdsReturnNotFound() throws Exception {
		this.mockMvc.perform(get("/speaker-banner/{id}.png", UUID.randomUUID())).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/talk-banner/{id}.png", 999999L)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/speaker-photo/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void downloadAllProducesAZipWithEverySpeakerTalkAndSocialBanner() throws Exception {
		Set<String> entries = zipEntries("/api/banners/download/all");

		assertThat(entries).containsExactlyInAnyOrder("speaker/Doe_Jane.png", "social/Doe_Jane.png",
				"speaker/Roe_John.png", "social/Roe_John.png", "talks/" + TALK_ID + ".png");
	}

	@Test
	void downloadTalksProducesOneEntryPerTalkNotOnePerSpeaker() throws Exception {
		assertThat(zipEntries("/api/banners/download/talks")).containsExactly("talks/" + TALK_ID + ".png");
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
		// Pins the reason findAllWithSpeakersBy() exists. Talk.speakers is a lazy
		// @ManyToMany and these endpoints run with spring.jpa.open-in-view=false, so a
		// plain findAll() hands back detached entities whose speaker list explodes on
		// first touch. The ZIP writer used to swallow that exception per entry, which
		// meant talk banners were silently missing from every archive.
		Talk detached = this.talkRepository.findAll().get(0);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> detached.getSpeakers().size())
			.isInstanceOf(org.hibernate.LazyInitializationException.class);

		assertThat(this.talkRepository.findAllWithSpeakersBy().get(0).getSpeakers()).hasSize(2);
	}

	@Test
	void indexPageListsTheSpeakers() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Doe")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Test Conference")));
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
