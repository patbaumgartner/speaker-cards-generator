package com.fortytwotalents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Speaker Cards Generator – Spring Boot application entry point.
 *
 * <p>
 * Generates speaker and talk banner images (PNG) for conferences such as Voxxed Days
 * Zürich, Voxxed Days Ticino, and Voxxed Days CERN. Speakers can be imported either from
 * a Sessionize XLSX export or directly from the Devoxx CFP API
 * ({@code https://m.devoxx.com/events/{eventId}/speakers}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpeakerCardsApplication {

	/**
	 * Application entry point.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(SpeakerCardsApplication.class, args);
	}

}
