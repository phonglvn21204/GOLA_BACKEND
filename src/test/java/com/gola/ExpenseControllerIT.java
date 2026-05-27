package com.gola;

import com.gola.dto.trip.ExpenseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Trip expenses API integration")
class ExpenseControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createAndListExpenses() throws Exception {
        var req = new ExpenseRequest();
        req.setAmount(new BigDecimal("150000"));
        req.setCurrency("VND");
        req.setDescription("Lunch");

        mockMvc.perform(post("/trips/{tripId}/expenses", TestDataConstants.SEED_TRIP_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/trips/{tripId}/expenses", TestDataConstants.SEED_TRIP_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
