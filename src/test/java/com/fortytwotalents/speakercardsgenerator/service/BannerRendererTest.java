package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.service.BannerRenderer.BannerType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BannerRendererTest {

	private final EventConfig event = new EventConfig();

	private final BannerRenderer renderer = new BannerRenderer(null, this.event, null, null);

	@Test
	void namesIdentifyEventCategorySessionSpeakerAndBannerKind() {
		this.event.setShortName("VDT27");
		Talk talk = talk(4243L, "Hands-on Java");
		talk.setSessionType("Hands-on Workshop");
		talk.setWorkshop(true);
		Speaker speaker = speaker();

		assertThat(this.renderer.downloadFileName(BannerType.TALK, null, talk))
			.isEqualTo("vdt27-workshop-4243-hands-on-java-session.png");
		assertThat(this.renderer.downloadFileName(BannerType.SPEAKER, speaker, talk))
			.isEqualTo("vdt27-workshop-4243-hands-on-java-jane-doe-" + speaker.getId() + "-speaker.png");
		assertThat(this.renderer.downloadFileName(BannerType.SOCIAL, speaker, null))
			.isEqualTo("vdt27-jane-doe-" + speaker.getId() + "-social.png");

		talk.setWorkshop(false);
		talk.setSessionType("Byte-Size");
		assertThat(this.renderer.downloadFileName(BannerType.TALK, null, talk))
			.isEqualTo("vdt27-byte-size-4243-hands-on-java-session.png");
	}

	@Test
	void identicalNamesAndTitlesStillProduceDistinctStableNames() {
		Talk first = talk(1L, "Same title");
		Talk second = talk(2L, "Same title");
		Speaker firstSpeaker = speaker();
		Speaker secondSpeaker = speaker();

		String firstName = this.renderer.downloadFileName(BannerType.TALK, null, first);
		assertThat(firstName).isNotEqualTo(this.renderer.downloadFileName(BannerType.TALK, null, second));
		assertThat(this.renderer.downloadFileName(BannerType.SPEAKER, firstSpeaker, first))
			.isNotEqualTo(this.renderer.downloadFileName(BannerType.SPEAKER, secondSpeaker, first));
		assertThat(this.renderer.downloadFileName(BannerType.TALK, null, first)).isEqualTo(firstName);
	}

	@Test
	void unsafeAndLongMetadataCannotEscapeDirectoriesOrExceedFileNameLimits() {
		this.event.setShortName("../Événement/".repeat(10));
		Talk talk = talk(Long.MAX_VALUE, "../Zürich: Java / 日本語?\r\n".repeat(20));
		talk.setSessionType("Conference / ../../".repeat(10));
		Speaker speaker = speaker();
		speaker.setFirstName("../José".repeat(30));
		speaker.setLastName("\\García\r\n".repeat(30));

		String filename = this.renderer.downloadFileName(BannerType.SPEAKER, speaker, talk);
		assertThat(filename).matches("[a-z0-9-]+\\.png")
			.startsWith("evenement-")
			.contains("zurich-java", "jose", speaker.getId().toString(), Long.toString(Long.MAX_VALUE));
		assertThat(filename.length()).isLessThanOrEqualTo(255);
		assertThat(this.renderer.fileName(BannerType.SPEAKER, speaker, talk)).isEqualTo("speaker/" + filename);
	}

	@Test
	void missingAndNonLatinMetadataStillKeepsTheSessionIdentity() {
		this.event.setShortName(null);
		Talk talk = talk(7L, "日本語");
		talk.setSessionType(" ");

		assertThat(this.renderer.downloadFileName(BannerType.TALK, null, talk))
			.isEqualTo("unknown-session-7-unknown-session.png");
		talk.setTitle(null);
		assertThat(this.renderer.downloadFileName(BannerType.TALK, null, talk))
			.isEqualTo("unknown-session-7-unknown-session.png");
	}

	private static Talk talk(long id, String title) {
		Talk talk = new Talk();
		talk.setId(id);
		talk.setTitle(title);
		return talk;
	}

	private static Speaker speaker() {
		Speaker speaker = new Speaker();
		speaker.setId(UUID.randomUUID());
		speaker.setFirstName("Jane");
		speaker.setLastName("Doe");
		return speaker;
	}

}
