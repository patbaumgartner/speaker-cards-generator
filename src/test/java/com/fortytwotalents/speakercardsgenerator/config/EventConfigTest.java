package com.fortytwotalents.speakercardsgenerator.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventConfigTest {

	@Test
	void identicalWorkshopAndConferenceDatesAreNotDistinct() {
		var config = new EventConfig();
		config.setDate("Tuesday, March 9, 2027");
		config.setWorkshopDate("Tuesday, March 9, 2027");

		assertThat(config.hasDistinctWorkshopDate()).isFalse();
	}

	@Test
	void datesAreComparedIgnoringSurroundingWhitespaceAndCase() {
		var config = new EventConfig();
		config.setDate("  Tuesday, March 9, 2027  ");
		config.setWorkshopDate("tuesday, march 9, 2027");

		assertThat(config.hasDistinctWorkshopDate()).isFalse();
	}

	@Test
	void differentDatesAreDistinct() {
		var config = new EventConfig();
		config.setDate("Tuesday, March 9, 2027");
		config.setWorkshopDate("Monday, March 8, 2027");

		assertThat(config.hasDistinctWorkshopDate()).isTrue();
	}

	@Test
	void missingWorkshopDateIsNotDistinct() {
		var config = new EventConfig();
		config.setDate("Tuesday, March 9, 2027");
		config.setWorkshopDate("   ");

		assertThat(config.hasDistinctWorkshopDate()).isFalse();
	}

	@Test
	void workshopDateIsDistinctWhenConferenceDateIsMissing() {
		var config = new EventConfig();
		config.setDate(null);
		config.setWorkshopDate("Monday, March 8, 2027");

		assertThat(config.hasDistinctWorkshopDate()).isTrue();
	}

}
