package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Notifications API integration")
class NotificationControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void listNotifications() throws Exception {
        mockMvc.perform(get("/notifications").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void unreadCount() throws Exception {
        mockMvc.perform(get("/notifications/unread-count").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").isNumber());
    }

    @Test
    void markAllRead() throws Exception {
        mockMvc.perform(post("/notifications/mark-all-read").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
