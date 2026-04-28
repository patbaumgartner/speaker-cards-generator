package com.fortytwotalents.speakercardsgenerator.controller;

import com.fortytwotalents.speakercardsgenerator.config.DevoxxApiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC tests for {@link ConfigController} using a standalone {@link MockMvc}
 * setup so the
 * controller can be exercised without bootstrapping the full application
 * context.
 */
class ConfigControllerTest {

    private MockMvc mockMvc;

    private DevoxxApiConfig devoxxApiConfig;

    @BeforeEach
    void setUp() {
        this.devoxxApiConfig = new DevoxxApiConfig();
        this.mockMvc = MockMvcBuilders.standaloneSetup(new ConfigController(this.devoxxApiConfig)).build();
    }

    @Test
    void tokenStatusReturnsFalseWhenNoTokenConfigured() throws Exception {
        this.mockMvc.perform(get("/api/config/api-token/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void tokenStatusReturnsTrueWhenTokenConfigured() throws Exception {
        this.devoxxApiConfig.setApiToken("secret-token");

        this.mockMvc.perform(get("/api/config/api-token/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void setTokenAcceptsValidToken() throws Exception {
        String body = "{\"token\":\"abc123\"}";

        this.mockMvc.perform(post("/api/config/api-token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("configured")));
    }

    @Test
    void setTokenRejectsBlankToken() throws Exception {
        String body = "{\"token\":\"   \"}";

        this.mockMvc.perform(post("/api/config/api-token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("must not be empty")));
    }

    @Test
    void setTokenRejectsMissingToken() throws Exception {
        this.mockMvc.perform(post("/api/config/api-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearTokenReturnsOk() throws Exception {
        this.devoxxApiConfig.setApiToken("to-be-cleared");

        this.mockMvc.perform(delete("/api/config/api-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cleared")));
    }

}
