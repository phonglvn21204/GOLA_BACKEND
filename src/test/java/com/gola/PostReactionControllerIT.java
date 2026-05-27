package com.gola;

import com.gola.dto.community.ReactionRequest;
import com.gola.entity.enums.ReactionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Post reactions API integration")
class PostReactionControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void putReaction() throws Exception {
        var req = new ReactionRequest();
        req.setKind(ReactionKind.LIKE);
        mockMvc.perform(put("/posts/{postId}/reactions", TestDataConstants.SEED_POST_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getReactionCount() throws Exception {
        mockMvc.perform(get("/posts/{postId}/reactions/count", TestDataConstants.SEED_POST_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteReaction() throws Exception {
        mockMvc.perform(delete("/posts/{postId}/reactions", TestDataConstants.SEED_POST_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
