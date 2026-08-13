package com.fortytwotalents.speakercardsgenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot application context smoke test, backed by the in-memory database configured
 * in {@code application-test.properties}.
 */
@SpringBootTest
@ActiveProfiles("test")
class SpeakerCardsApplicationTest {

	/** Verifies that the Spring application context loads successfully. */
	@Test
	void contextLoads() {
		// Context loading is verified by the @SpringBootTest annotation itself.
	}

}
