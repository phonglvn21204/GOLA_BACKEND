package com.gola;

import com.gola.dto.trip.LiveLocationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Live location API integration")
class LiveLocationControllerIT extends BaseIntegrationTest {

    @Test
    void pingAndGetLocations() throws Exception {
        loginAsSeedUser2();
        var req = new LiveLocationRequest();
        req.setLat(16.0678);
        req.setLng(108.2498);

        mockMvc.perform(post("/live/{sessionId}/ping", TestDataConstants.SEED_ACTIVE_SESSION_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/live/{sessionId}/locations", TestDataConstants.SEED_ACTIVE_SESSION_ID)
                        .with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
