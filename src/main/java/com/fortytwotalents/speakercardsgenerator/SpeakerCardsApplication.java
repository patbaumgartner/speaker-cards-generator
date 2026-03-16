package com.fortytwotalents.speakercardsgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Speaker Cards Generator – Spring Boot application entry point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpeakerCardsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpeakerCardsApplication.class, args);
	}

}
