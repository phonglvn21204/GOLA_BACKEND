package com.gola;

import com.gola.dto.community.CreateAlbumRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Albums API integration")
class AlbumControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createAlbum() throws Exception {
        var req = CreateAlbumRequest.builder()
                .tripId(TestDataConstants.SEED_TRIP_ID)
                .title("IT Album")
                .isPublic(true)
                .build();
        mockMvc.perform(post("/albums")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAlbumsByTrip() throws Exception {
        mockMvc.perform(get("/albums/trip/{tripId}", TestDataConstants.SEED_TRIP_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getAlbumById() throws Exception {
        mockMvc.perform(get("/albums/{id}", TestDataConstants.SEED_ALBUM_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_ALBUM_ID.toString()));
    }
}
