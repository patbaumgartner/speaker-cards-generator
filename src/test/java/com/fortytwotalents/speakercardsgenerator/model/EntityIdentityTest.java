package com.fortytwotalents.speakercardsgenerator.model;

import java.util.HashSet;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

	@Test
	void speakersWithTheSamePersistentIdAreEqual() {
		UUID id = UUID.randomUUID();
		Speaker first = speaker(id, "Jane", "Doe");
		Speaker reloaded = speaker(id, "Janet", "Smith");

		assertThat(first).isEqualTo(reloaded);
		assertThat(new HashSet<>(java.util.List.of(first, reloaded))).hasSize(1);
	}

	@Test
	void transientSpeakersAreOnlyEqualToThemselves() {
		Speaker first = new Speaker();
		Speaker second = new Speaker();

		assertThat(first).isEqualTo(first).isNotEqualTo(second);
	}

	@Test
	void speakerDisplayAndOrderingHandleMissingNames() {
		Speaker unknown = new Speaker();
		Speaker named = speaker(UUID.randomUUID(), "jane", "Doe");
		Speaker sameName = speaker(UUID.randomUUID(), "Jane", "Doe");

		assertThat(unknown.displayName()).isEqualTo("(unknown speaker)");
		assertThat(new TreeSet<>(java.util.List.of(unknown, named, sameName))).hasSize(3);
	}

	@Test
	void sessionsWithTheSamePersistentIdAreEqual() {
		Session first = session(7L, "First title");
		Session reloaded = session(7L, "Changed title");

		assertThat(first).isEqualTo(reloaded);
		assertThat(new HashSet<>(java.util.List.of(first, reloaded))).hasSize(1);
	}

	@Test
	void sessionOrderingHandlesNullTitlesAndUsesIdAsATieBreaker() {
		Session untitled = session(3L, null);
		Session first = session(1L, "Same");
		Session second = session(2L, "same");

		assertThat(new TreeSet<>(java.util.List.of(untitled, first, second))).containsExactly(first, second, untitled);
	}

	private static Speaker speaker(UUID id, String firstName, String lastName) {
		Speaker speaker = new Speaker();
		speaker.setId(id);
		speaker.setFirstName(firstName);
		speaker.setLastName(lastName);
		return speaker;
	}

	private static Session session(Long id, String title) {
		Session session = new Session();
		session.setId(id);
		session.setTitle(title);
		return session;
	}

}
