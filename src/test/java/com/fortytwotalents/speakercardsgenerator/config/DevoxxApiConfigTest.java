package com.fortytwotalents.speakercardsgenerator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class DevoxxApiConfigTest {

	@Test
	void usesVdz27AsDefaultEventId() {
		assertThat(new DevoxxApiConfig().getEventId()).isEqualTo("vdz27");
	}

	@Test
	void applicationPropertiesSelectVdz27WithoutProfile() throws IOException {
		Properties properties = new Properties();
		try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
			assertThat(input).isNotNull();
			properties.load(input);
		}

		assertThat(properties.getProperty("spring.profiles.default")).isEqualTo("vdz");
		assertThat(properties.getProperty("app.devoxx.api.event-id")).isEqualTo("vdz27");
	}

}
