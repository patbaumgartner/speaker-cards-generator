package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for {@link ImportController} using a standalone {@link MockMvc} setup. The
 * {@link DevoxxImportService} dependency is mocked.
 */
class ImportControllerTest {

	private MockMvc mockMvc;

	private DevoxxImportService devoxxImportService;

	@BeforeEach
	void setUp() {
		this.devoxxImportService = Mockito.mock(DevoxxImportService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new ImportController(this.devoxxImportService)).build();
	}

	@Test
	void importEndpointsRejectGetRequests() throws Exception {
		this.mockMvc.perform(get("/api/import/devoxx")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void importFromDevoxxReturnsCount() throws Exception {
		given(this.devoxxImportService.importSpeakers()).willReturn(42);

		this.mockMvc.perform(post("/api/import/devoxx"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("42 speakers")));
	}

	@Test
	void importFromDevoxxEventReturnsCount() throws Exception {
		given(this.devoxxImportService.importSpeakers("vdz27")).willReturn(7);

		this.mockMvc.perform(post("/api/import/devoxx/{eventId}", "vdz27"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("vdz27")))
			.andExpect(content().string(containsString("7 speakers")));
	}

	@Test
	void importFromDevoxxEventReturnsServerErrorOnException() throws Exception {
		given(this.devoxxImportService.importSpeakers(anyString())).willThrow(new RuntimeException("api down"));

		this.mockMvc.perform(post("/api/import/devoxx/{eventId}", "vdz27"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string("CFP import failed. Check the application logs."));
	}

}
