package com.gola;

import com.gola.dto.community.CreatePostRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Posts API integration")
class PostControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createPost() throws Exception {
        var req = CreatePostRequest.builder()
                .body("Integration test post #vietnam")
                .tripId(TestDataConstants.SEED_TRIP_ID)
                .hashtags(List.of("vietnam", "test"))
                .build();

        mockMvc.perform(post("/posts")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.body").value(req.getBody()));
    }

    @Test
    void getFeed() throws Exception {
        mockMvc.perform(get("/posts/feed").param("page", "0").param("size", "5").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getPostsByHashtag() throws Exception {
        mockMvc.perform(get("/posts/hashtag/{tag}", "food").param("page", "0").param("size", "5").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getPostById() throws Exception {
        mockMvc.perform(get("/posts/{id}", TestDataConstants.SEED_POST_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_POST_ID.toString()));
    }
}
