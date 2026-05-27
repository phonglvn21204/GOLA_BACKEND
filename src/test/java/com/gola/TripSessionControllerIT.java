package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Trip session API integration")
class TripSessionControllerIT extends BaseIntegrationTest {

    @Test
    void getActiveSession_asTripOwner() throws Exception {
        loginAsSeedUser2();
        mockMvc.perform(get("/trips/{id}/session/active", TestDataConstants.SEED_TRIP2_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
