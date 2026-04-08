package com.fortytwotalents.speakercardsgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
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
	void restClientDeserializesJsonServedWithTextHtmlContentType() {
		RestClient.Builder builder = DevoxxImportService.configureBuilder(RestClient.builder());
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		server.expect(requestTo("https://vdz26.cfp.dev/api/public/speakers"))
			.andRespond(withSuccess(SPEAKERS_JSON, MediaType.TEXT_HTML));

		DevoxxImportService.DevoxxSpeakerDto[] speakers = restClient.get()
			.uri("https://vdz26.cfp.dev/api/public/speakers")
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

		server.expect(requestTo("https://vdz26.cfp.dev/api/public/speakers"))
			.andRespond(withSuccess(SPEAKERS_JSON, MediaType.TEXT_HTML));

		assertThatThrownBy(() -> defaultRestClient.get()
			.uri("https://vdz26.cfp.dev/api/public/speakers")
			.retrieve()
			.body(DevoxxImportService.DevoxxSpeakerDto[].class)).isInstanceOf(UnknownContentTypeException.class);
	}

}
