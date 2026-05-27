package com.gola;

import com.gola.dto.community.PostCommentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Comments API integration")
class CommentControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void addComment() throws Exception {
        var req = new PostCommentRequest();
        req.setBody("Great post from IT test!");
        mockMvc.perform(post("/posts/{postId}/comments", TestDataConstants.SEED_POST_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.body").value(req.getBody()));
    }

    @Test
    void getComments() throws Exception {
        mockMvc.perform(get("/posts/{postId}/comments", TestDataConstants.SEED_POST_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
