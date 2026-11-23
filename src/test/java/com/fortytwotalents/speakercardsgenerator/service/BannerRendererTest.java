package com.fortytwotalents.speakercardsgenerator.service;

import com.fortytwotalents.speakercardsgenerator.config.EventConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Session;
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
		Session session = session(4243L, "Hands-on Java");
		session.setSessionType("Hands-on Workshop");
		session.setWorkshop(true);
		Speaker speaker = speaker();

		assertThat(this.renderer.downloadFileName(BannerType.SESSION, null, session))
			.isEqualTo("vdt27-workshop-4243-hands-on-java-session.png");
		assertThat(this.renderer.downloadFileName(BannerType.SPEAKER, speaker, session))
			.isEqualTo("vdt27-workshop-4243-hands-on-java-jane-doe-" + speaker.getId() + "-speaker.png");
		assertThat(this.renderer.downloadFileName(BannerType.SOCIAL, speaker, null))
			.isEqualTo("vdt27-jane-doe-" + speaker.getId() + "-social.png");

		session.setWorkshop(false);
		session.setSessionType("Byte-Size");
		assertThat(this.renderer.downloadFileName(BannerType.SESSION, null, session))
			.isEqualTo("vdt27-byte-size-4243-hands-on-java-session.png");
	}

	@Test
	void identicalNamesAndTitlesStillProduceDistinctStableNames() {
		Session first = session(1L, "Same title");
		Session second = session(2L, "Same title");
		Speaker firstSpeaker = speaker();
		Speaker secondSpeaker = speaker();

		String firstName = this.renderer.downloadFileName(BannerType.SESSION, null, first);
		assertThat(firstName).isNotEqualTo(this.renderer.downloadFileName(BannerType.SESSION, null, second));
		assertThat(this.renderer.downloadFileName(BannerType.SPEAKER, firstSpeaker, first))
			.isNotEqualTo(this.renderer.downloadFileName(BannerType.SPEAKER, secondSpeaker, first));
		assertThat(this.renderer.downloadFileName(BannerType.SESSION, null, first)).isEqualTo(firstName);
	}

	@Test
	void unsafeAndLongMetadataCannotEscapeDirectoriesOrExceedFileNameLimits() {
		this.event.setShortName("../Événement/".repeat(10));
		Session session = session(Long.MAX_VALUE, "../Zürich: Java / 日本語?\r\n".repeat(20));
		session.setSessionType("Conference / ../../".repeat(10));
		Speaker speaker = speaker();
		speaker.setFirstName("../José".repeat(30));
		speaker.setLastName("\\García\r\n".repeat(30));

		String filename = this.renderer.downloadFileName(BannerType.SPEAKER, speaker, session);
		assertThat(filename).matches("[a-z0-9-]+\\.png")
			.startsWith("evenement-")
			.contains("zurich-java", "jose", speaker.getId().toString(), Long.toString(Long.MAX_VALUE));
		assertThat(filename.length()).isLessThanOrEqualTo(255);
		assertThat(this.renderer.fileName(BannerType.SPEAKER, speaker, session)).isEqualTo("speaker/" + filename);
	}

	@Test
	void missingAndNonLatinMetadataStillKeepsTheSessionIdentity() {
		this.event.setShortName(null);
		Session session = session(7L, "日本語");
		session.setSessionType(" ");

		assertThat(this.renderer.downloadFileName(BannerType.SESSION, null, session))
			.isEqualTo("unknown-session-7-unknown-session.png");
		session.setTitle(null);
		assertThat(this.renderer.downloadFileName(BannerType.SESSION, null, session))
			.isEqualTo("unknown-session-7-unknown-session.png");
	}

	private static Session session(long id, String title) {
		Session session = new Session();
		session.setId(id);
		session.setTitle(title);
		return session;
	}

	private static Speaker speaker() {
		Speaker speaker = new Speaker();
		speaker.setId(UUID.randomUUID());
		speaker.setFirstName("Jane");
		speaker.setLastName("Doe");
		return speaker;
	}

}
