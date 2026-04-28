package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.service.DevoxxImportService;
import com.fortytwotalents.speakercardsgenerator.service.ImportFromCSVService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for {@link ImportController} using a standalone {@link MockMvc} setup. The
 * {@link ImportFromCSVService} and {@link DevoxxImportService} dependencies are mocked.
 */
class ImportControllerTest {

	private MockMvc mockMvc;

	private ImportFromCSVService importFromCSVService;

	private DevoxxImportService devoxxImportService;

	@BeforeEach
	void setUp() {
		this.importFromCSVService = Mockito.mock(ImportFromCSVService.class);
		this.devoxxImportService = Mockito.mock(DevoxxImportService.class);
		this.mockMvc = MockMvcBuilders
			.standaloneSetup(new ImportController(this.importFromCSVService, this.devoxxImportService))
			.build();
	}

	@Test
	void importCsvReturnsOkOnSuccess() throws Exception {
		this.mockMvc.perform(get("/api/import/csv"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("XLSX import completed")));

		verify(this.importFromCSVService).importFromXlsx("SelectedWithSchedule.xlsx");
	}

	@Test
	void importCsvReturnsServerErrorOnException() throws Exception {
		Mockito.doThrow(new RuntimeException("boom")).when(this.importFromCSVService).importFromXlsx(anyString());

		this.mockMvc.perform(get("/api/import/csv"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(containsString("boom")));
	}

	@Test
	void importFromDevoxxReturnsCount() throws Exception {
		given(this.devoxxImportService.importSpeakers()).willReturn(42);

		this.mockMvc.perform(get("/api/import/devoxx"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("42 speakers")));
	}

	@Test
	void importFromDevoxxEventReturnsCount() throws Exception {
		given(this.devoxxImportService.importSpeakers("vdz26")).willReturn(7);

		this.mockMvc.perform(get("/api/import/devoxx/{eventId}", "vdz26"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("vdz26")))
			.andExpect(content().string(containsString("7 speakers")));
	}

	@Test
	void importFromDevoxxEventReturnsServerErrorOnException() throws Exception {
		given(this.devoxxImportService.importSpeakers(anyString())).willThrow(new RuntimeException("api down"));

		this.mockMvc.perform(get("/api/import/devoxx/{eventId}", "vdz26"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(containsString("api down")));
	}

}
