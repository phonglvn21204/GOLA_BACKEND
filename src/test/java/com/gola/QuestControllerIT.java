package com.gola;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Quests API integration")
class QuestControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void getAllQuests() throws Exception {
        mockMvc.perform(get("/quests").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getQuestById() throws Exception {
        mockMvc.perform(get("/quests/{id}", TestDataConstants.SEED_QUEST_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_QUEST_ID.toString()));
    }

    @Test
    void startQuestAndGetProgress() throws Exception {
        mockMvc.perform(post("/quests/{id}/start", TestDataConstants.SEED_QUEST_IN_PROGRESS_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/quests/{id}/progress", TestDataConstants.SEED_QUEST_IN_PROGRESS_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
