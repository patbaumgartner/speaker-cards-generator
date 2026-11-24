package com.fortytwotalents.speakercardsgenerator.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the speaker directory view and its import controls.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebsiteControllerIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void importControlsExposeTokenHints() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-import-type=\"devoxx\"")))
			.andExpect(content().string(containsString("aria-describedby=\"importTokenHint\"")))
			.andExpect(content().string(containsString("aria-describedby=\"importTokenHintNav\"")))
			.andExpect(content().string(containsString("data-import-token-hint")));
	}

	@Test
	void importUiChecksTokenStatusAndGuardsActivation() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("\"/config/api-token/status\"")))
			.andExpect(content().string(containsString("setAttribute(\"aria-disabled\", \"true\")")))
			.andExpect(content().string(containsString("showImportToast(false, TOKEN_REQUIRED_MESSAGE)")));
	}

}
