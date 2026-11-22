package com.fortytwotalents.speakercardsgenerator.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpeakerValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void rejectsNonHttpsMastodonUrls() {
		Speaker speaker = validSpeaker();
		speaker.setMastodonAccount("javascript:alert(1)");

		Set<ConstraintViolation<Speaker>> violations = validator.validate(speaker);

		assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
			.contains("mastodonAccount");
	}

	@Test
	void acceptsHttpsMastodonUrls() {
		Speaker speaker = validSpeaker();
		speaker.setMastodonAccount("https://mastodon.social/@jane");

		assertThat(validator.validate(speaker)).isEmpty();
	}

	private static Speaker validSpeaker() {
		Speaker speaker = new Speaker();
		speaker.setId(java.util.UUID.randomUUID());
		speaker.setLastName("Doe");
		speaker.setBiography("A great speaker.");
		return speaker;
	}

}
