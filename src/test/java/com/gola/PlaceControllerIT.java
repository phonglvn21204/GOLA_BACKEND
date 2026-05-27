package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Places API integration")
class PlaceControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void searchPlaces() throws Exception {
        mockMvc.perform(get("/places/search")
                        .param("q", "Ben Thanh")
                        .param("lat", "10.7720")
                        .param("lng", "106.6981")
                        .with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getPlaceById() throws Exception {
        mockMvc.perform(get("/places/{id}", TestDataConstants.SEED_PLACE_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_PLACE_ID.toString()));
    }
}
