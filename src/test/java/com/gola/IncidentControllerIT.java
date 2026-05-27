package com.gola;

import com.gola.dto.safety.IncidentRequest;
import com.gola.entity.enums.IncidentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Incidents API integration")
class IncidentControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createIncident() throws Exception {
        var req = new IncidentRequest();
        req.setType(IncidentType.OTHER);
        req.setDescription("Integration test incident");
        req.setLatitude(10.77);
        req.setLongitude(106.69);
        req.setTripId(TestDataConstants.SEED_TRIP_ID);

        mockMvc.perform(post("/incidents")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getMyIncidents() throws Exception {
        mockMvc.perform(get("/incidents").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
