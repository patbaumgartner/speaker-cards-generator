package com.fortytwotalents.speakercardsgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import com.fortytwotalents.speakercardsgenerator.model.Talk;
import com.fortytwotalents.speakercardsgenerator.repository.SpeakerRepository;
import com.fortytwotalents.speakercardsgenerator.repository.TalkRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * Verifies that the {@link RestClient} used inside {@link DevoxxImportService} can
 * deserialise a JSON body served with a {@code text/html} content-type header – which is
 * the actual behaviour of some CFP API endpoints.
 */
class DevoxxImportServiceRestClientTest {

	private static final String SPEAKERS_JSON = """
			[
			  {
			    "uuid": "11111111-1111-1111-1111-111111111111",
			    "firstName": "Jane",
			    "lastName": "Doe",
			    "bio": "A great speaker.",
			    "company": "Acme Corp",
			    "imageUrl": "https://example.com/jane.jpg",
			    "twitter": "janedoe",
			    "linkedIn": "janedoe",
			    "blog": "https://janedoe.dev"
			  }
			]
			""";

	@Test
	void reimportRefreshesExistingRecordsAndClearsWithdrawnScheduleAndSpeakers() {
		var builder = RestClient.builder();
		var server = MockRestServiceServer.bindTo(builder).build();
		var speakers = mock(SpeakerRepository.class);
		var talks = mock(TalkRepository.class);
		var photos = mock(SpeakerPhotoStore.class);
		var importer = new DevoxxImportService(new DevoxxApiConfig(), speakers, talks, builder, photos);
		var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
		var speaker = new Speaker();
		speaker.setId(id);
		speaker.setCompany("Old employer");
		speaker.setTitle("Staff Engineer");
		var talk = new Talk();
		talk.setId(42L);
		talk.setTitle("Previous title");
		talk.setFormattedTitle("Previous<br/>title");
		talk.setDate("2026-01-01");
		talk.setCetTime("12:00");
		talk.setWorkshop(true);
		talk.setSessionType("Workshop");
		talk.setSpeakers(List.of(speaker));
		when(speakers.findById(id)).thenReturn(Optional.of(speaker));
		when(talks.findById(42L)).thenReturn(Optional.of(talk));
		String api = "https://vdz27.cfp.dev/api/public";
		server.expect(requestTo(api + "/speakers?page=0&size=1000"))
			.andRespond(withSuccess(SPEAKERS_JSON, MediaType.APPLICATION_JSON));
		server.expect(requestTo(api + "/talks")).andRespond(withSuccess("""
				[{"id":42,"title":"New title","timeSlots":[],"speakers":[]}]
				""", MediaType.APPLICATION_JSON));

		assertThat(importer.importSpeakers()).isEqualTo(1);
		assertThat(speaker.getCompany()).isEqualTo("Acme Corp");
		assertThat(speaker.getTitle()).isEqualTo("Staff Engineer");
		assertThat(talk.getTitle()).isEqualTo("New title");
		assertThat(talk.getFormattedTitle()).isNull();
		assertThat(talk.getDate()).isNull();
		assertThat(talk.getCetTime()).isNull();
		assertThat(talk.isWorkshop()).isFalse();
		assertThat(talk.getSessionType()).isNull();
		assertThat(talk.getSpeakers()).isEmpty();
		verify(photos).download(id, null, null, "https://example.com/jane.jpg");
		verify(speakers).save(speaker);
		verify(talks).save(talk);
		server.verify();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "Principal Engineer|Principal Engineer", "''|" })
	void reimportUpdatesAnExplicitlyProvidedJobTitle(String jobTitle, String expected) {
		var builder = RestClient.builder();
		var server = MockRestServiceServer.bindTo(builder).build();
		var speakers = mock(SpeakerRepository.class);
		var importer = new DevoxxImportService(new DevoxxApiConfig(), speakers, mock(TalkRepository.class), builder,
				mock(SpeakerPhotoStore.class));
		var speaker = new Speaker();
		var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
		speaker.setId(id);
		speaker.setTitle("Staff Engineer");
		when(speakers.findById(id)).thenReturn(Optional.of(speaker));
		String api = "https://vdz27.cfp.dev/api/public";
		server.expect(requestTo(api + "/speakers?page=0&size=1000")).andRespond(withSuccess("""
				[{"uuid":"11111111-1111-1111-1111-111111111111","firstName":"Jane",
				  "lastName":"Doe","jobTitle":"%s"}]
				""".formatted(jobTitle), MediaType.APPLICATION_JSON));
		server.expect(requestTo(api + "/talks")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(importer.importSpeakers()).isEqualTo(1);
		assertThat(speaker.getTitle()).isEqualTo(expected);
		verify(speakers).save(speaker);
		server.verify();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "{\"jobTitle\":\"Principal Engineer\"}|Principal Engineer",
			"{\"jobTitle\":\"\"}|", "{}|Staff Engineer" })
	void authenticatedImportUpdatesTitlesOnlyWhenProvided(String detail, String expected) {
		var builder = RestClient.builder();
		var server = MockRestServiceServer.bindTo(builder).build();
		var speakers = mock(SpeakerRepository.class);
		var config = new DevoxxApiConfig();
		config.setApiToken("test-token");
		var importer = new DevoxxImportService(config, speakers, mock(TalkRepository.class), builder,
				mock(SpeakerPhotoStore.class));
		var speaker = new Speaker();
		var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
		speaker.setId(id);
		speaker.setTitle("Staff Engineer");
		when(speakers.findById(id)).thenReturn(Optional.of(speaker));
		String api = "https://vdz27.cfp.dev/api";
		server.expect(requestTo(api + "/public/speakers?page=0&size=1000")).andRespond(withSuccess("""
				[{"id":123,"uuid":"11111111-1111-1111-1111-111111111111",
				  "firstName":"Jane","lastName":"Doe"}]
				""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(api + "/speakers/123")).andRespond(withSuccess(detail, MediaType.APPLICATION_JSON));
		server.expect(requestTo(api + "/public/talks")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(importer.importSpeakers()).isEqualTo(1);
		assertThat(speaker.getTitle()).isEqualTo(expected);
		verify(speakers).save(speaker);
		server.verify();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{\"name\":\"Workshop\",\"slug\":\"workshop-2\"}|true|Workshop",
					"{\"name\":\"Full-day WORKSHOP\",\"slug\":\"lab\"}|true|Workshop",
					"{\"name\":\"Hands-on Lab\",\"slug\":\"half-day-workshop\"}|true|Workshop",
					"{\"name\":null,\"slug\":\"WORKSHOP\"}|true|Workshop",
					"{\"name\":\" Conference \",\"slug\":\"conf45\"}|false|Conference",
					"{\"name\":\"Byte-Size\",\"slug\":\"byte-size\"}|false|Byte-Size",
					"{\"name\":\"Keynote\",\"slug\":\"key30\"}|false|Keynote",
					"{\"name\":\"   \",\"slug\":\"other\"}|false|", "null|false|" })
	void importsSessionTypeAndWorkshopClassification(String sessionType, boolean workshop, String label) {
		var builder = RestClient.builder();
		var server = MockRestServiceServer.bindTo(builder).build();
		var talks = mock(TalkRepository.class);
		var importer = new DevoxxImportService(new DevoxxApiConfig(), mock(SpeakerRepository.class), talks, builder,
				mock(SpeakerPhotoStore.class));
		var existing = new Talk();
		existing.setId(42L);
		existing.setWorkshop(true);
		existing.setSessionType("Workshop");
		when(talks.findById(42L)).thenReturn(Optional.of(existing));
		String api = "https://vdz27.cfp.dev/api/public";
		server.expect(requestTo(api + "/speakers?page=0&size=1000"))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo(api + "/talks")).andRespond(withSuccess("""
				[{"id":42,"title":"A session","sessionType":%s}]
				""".formatted(sessionType), MediaType.APPLICATION_JSON));

		importer.importSpeakers();

		var saved = ArgumentCaptor.forClass(Talk.class);
		verify(talks).save(saved.capture());
		assertThat(saved.getValue().isWorkshop()).isEqualTo(workshop);
		assertThat(saved.getValue().getSessionTypeLabel()).isEqualTo(label);
		server.verify();
	}

	@Test
	void restClientDeserializesJsonServedWithTextHtmlContentType() {
		RestClient.Builder builder = DevoxxImportService.configureBuilder(RestClient.builder());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://vdz27.cfp.dev/api/public/speakers"))
			.andRespond(withSuccess(SPEAKERS_JSON, MediaType.TEXT_HTML));

		DevoxxImportService.DevoxxSpeakerDto[] speakers = restClient.get()
			.uri("https://vdz27.cfp.dev/api/public/speakers")
			.retrieve()
			.body(DevoxxImportService.DevoxxSpeakerDto[].class);

		assertThat(speakers).isNotNull().hasSize(1);
		assertThat(speakers[0].firstName).isEqualTo("Jane");
		assertThat(speakers[0].lastName).isEqualTo("Doe");
		assertThat(speakers[0].uuid).isEqualTo("11111111-1111-1111-1111-111111111111");
	}

	@Test
	void defaultRestClientCannotDeserializeJsonWithTextHtmlContentType() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient defaultRestClient = builder.build();

		server.expect(requestTo("https://vdz27.cfp.dev/api/public/speakers"))
			.andRespond(withSuccess(SPEAKERS_JSON, MediaType.TEXT_HTML));

		assertThatThrownBy(() -> defaultRestClient.get()
			.uri("https://vdz27.cfp.dev/api/public/speakers")
			.retrieve()
			.body(DevoxxImportService.DevoxxSpeakerDto[].class)).isInstanceOf(UnknownContentTypeException.class);
	}

}
