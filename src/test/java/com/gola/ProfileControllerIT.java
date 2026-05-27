package com.gola;

import com.gola.dto.user.UpdateProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Profile API integration")
class ProfileControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void getMyProfile() throws Exception {
        mockMvc.perform(get("/me").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TestDataConstants.SEED_USER_EMAIL));
    }

    @Test
    void updateMyProfile() throws Exception {
        var req = new UpdateProfileRequest();
        req.setBio("Updated via integration test");
        req.setLocale("vi");
        mockMvc.perform(patch("/me")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bio").value("Updated via integration test"));
    }

    @Test
    void getUserProfileById() throws Exception {
        mockMvc.perform(get("/users/{id}", TestDataConstants.SEED_USER2_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_USER2_ID.toString()));
    }
}
