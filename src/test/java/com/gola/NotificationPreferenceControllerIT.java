package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Notification preferences API integration")
class NotificationPreferenceControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void getPreferences() throws Exception {
        mockMvc.perform(get("/me/notifications/preferences").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void setPreference() throws Exception {
        mockMvc.perform(put("/me/notifications/preferences/{type}", "TRIP")
                        .param("channel", "IN_APP")
                        .param("isEnabled", "true")
                        .with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
