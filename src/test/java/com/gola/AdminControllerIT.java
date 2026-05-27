package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Admin API integration")
class AdminControllerIT extends AdminIntegrationTest {

    @Test
    void getMetrics() throws Exception {
        mockMvc.perform(get("/admin/metrics").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listUsers() throws Exception {
        mockMvc.perform(get("/admin/users").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void listIncidents() throws Exception {
        mockMvc.perform(get("/admin/incidents").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listPosts() throws Exception {
        mockMvc.perform(get("/admin/posts").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getActiveSos() throws Exception {
        mockMvc.perform(get("/admin/sos/active").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void changeUserRole() throws Exception {
        mockMvc.perform(patch("/admin/users/{id}/role", TestDataConstants.SEED_USER2_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void hidePost() throws Exception {
        mockMvc.perform(patch("/admin/posts/{id}/hide", TestDataConstants.SEED_POST_FOR_HIDE_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}