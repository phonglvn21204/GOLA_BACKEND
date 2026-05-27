package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Follow API integration")
class FollowControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void followAndUnfollowUser() throws Exception {
        mockMvc.perform(post("/users/{id}/follow", TestDataConstants.SEED_USER2_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/users/{id}/follow", TestDataConstants.SEED_USER2_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
