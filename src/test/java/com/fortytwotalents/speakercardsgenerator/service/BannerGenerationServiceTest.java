package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BannerGenerationServiceTest {

	private final SpeakerRepository speakers = mock(SpeakerRepository.class);

	private final TalkRepository talks = mock(TalkRepository.class);

	private final BannerRenderer renderer = spy(new BannerRenderer(null, new EventConfig(), null, null));

	private final BannerGenerationService service = new BannerGenerationService(this.speakers, this.talks,
			this.renderer);

	private final Speaker speaker = speaker();

	private final Speaker namesake = speaker();

	private final Speaker withoutSession = speaker();

	private final Talk conference = talk(1L);

	private final Talk workshop = talk(2L);

	@BeforeEach
	void setUp() {
		this.workshop.setWorkshop(true);
		this.speaker.setTalks(List.of(this.conference, this.workshop));
		this.namesake.setTalks(List.of(this.conference));
		this.conference.setSpeakers(List.of(this.namesake, this.speaker));
		this.workshop.setSpeakers(List.of(this.speaker));
		when(this.speakers.findAllWithTalksBy()).thenReturn(List.of(this.speaker, this.namesake, this.withoutSession));
		when(this.speakers.findWithTalksById(this.speaker.getId())).thenReturn(Optional.of(this.speaker));
		when(this.talks.findAllWithSpeakersBy()).thenReturn(List.of(this.workshop, this.conference));
		doReturn(new byte[] { 1, 2, 3 }).when(this.renderer)
			.renderPng(any(BannerType.class), nullable(Speaker.class), nullable(Talk.class));
	}

	@Test
	void zipAndDiskExportsIncludeEveryVariantWithIdenticalNames(@TempDir Path output) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.service.writeZip(bytes, EnumSet.allOf(BannerType.class));
		Set<String> zipNames = new HashSet<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				assertThat(zipNames.add(entry.getName())).as("unique entry: %s", entry.getName()).isTrue();
				assertThat(zip.readAllBytes()).containsExactly(1, 2, 3);
			}
		}
		assertThat(zipNames).hasSize(10);
		for (BannerType type : List.of(BannerType.SPEAKER, BannerType.SOCIAL)) {
			for (Talk talk : List.of(this.conference, this.workshop)) {
				verify(this.renderer).renderPng(type, this.speaker, talk);
			}
			verify(this.renderer).renderPng(type, this.namesake, this.conference);
			verify(this.renderer).renderPng(type, this.withoutSession, null);
		}
		verify(this.renderer).renderPng(BannerType.TALK, null, this.conference);
		verify(this.renderer).renderPng(BannerType.TALK, null, this.workshop);

		BannerGenerationResult result = this.service.generateAllBanners(output.toString());
		assertThat(result.getFailureCount()).isZero();
		assertThat(result.getSuccessCount()).isEqualTo(10);
		assertThat(result.getSavedFiles()).hasSize(10);
		Set<String> diskNames = new HashSet<>();
		for (String saved : result.getSavedFiles()) {
			Path file = Path.of(saved);
			diskNames.add(output.relativize(file).toString());
			assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
		}
		assertThat(diskNames).isEqualTo(zipNames);
	}

	@Test
	void inMemoryAndSelectedSpeakerGenerationAlsoIncludeAllSessions() {
		BannerGenerationResult all = this.service.generateAllSpeakerBanners();
		assertThat(all.getSuccessCount()).isEqualTo(4);
		assertThat(all.getFailureCount()).isZero();

		BannerGenerationResult selected = this.service.generateSpeakerBanners(List.of(this.speaker.getId()));
		assertThat(selected.getSuccessCount()).isEqualTo(2);
		assertThat(selected.getFailureCount()).isZero();
		verify(this.renderer, times(2)).renderPng(BannerType.SPEAKER, this.speaker, this.conference);
		verify(this.renderer, times(2)).renderPng(BannerType.SPEAKER, this.speaker, this.workshop);
	}

	private static Talk talk(long id) {
		Talk talk = new Talk();
		talk.setId(id);
		talk.setTitle("Same title");
		talk.setSessionType("Conference");
		return talk;
	}

	private static Speaker speaker() {
		Speaker speaker = new Speaker();
		speaker.setId(UUID.randomUUID());
		speaker.setFirstName("Jane");
		speaker.setLastName("Doe");
		speaker.setTalks(new ArrayList<>());
		return speaker;
	}

}
