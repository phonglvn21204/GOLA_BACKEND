package com.gola;

import com.gola.dto.safety.SosTriggerRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SOS API integration")
class SosControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void triggerSos() throws Exception {
        var req = new SosTriggerRequest();
        req.setLatitude(10.7720);
        req.setLongitude(106.6981);
        req.setTripId(TestDataConstants.SEED_TRIP_ID);
        req.setClientToken("it-test-token");

        mockMvc.perform(post("/sos/trigger")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
