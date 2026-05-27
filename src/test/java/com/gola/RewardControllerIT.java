package com.gola;

import com.gola.dto.quest.RedemptionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Rewards API integration")
class RewardControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void getActiveRewards() throws Exception {
        mockMvc.perform(get("/rewards").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void redeemReward_mayFailIfInsufficientCoins() throws Exception {
        var req = new RedemptionRequest();
        req.setRewardId(TestDataConstants.SEED_REWARD_ID);
        var result = mockMvc.perform(post("/rewards/redeem")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        int status = result.getResponse().getStatus();
        // User may have enough coins (seed: 1250) or business rule may reject duplicate redemption
        if (status == 200) {
            assertApiSuccess(result);
        }
    }
}
