package com.gola;

import com.gola.dto.trip.AddStopRequest;
import com.gola.dto.trip.CreateTripRequest;
import com.gola.dto.trip.ShareTripRequest;
import com.gola.entity.enums.ShareScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Trips API integration")
class TripControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createTrip() throws Exception {
        var req = new CreateTripRequest();
        req.setTitle("IT Trip " + LocalDate.now());
        req.setOrigin("Hanoi");
        req.setDestination("Da Nang");
        req.setStartDate(LocalDate.now().plusDays(7));
        req.setEndDate(LocalDate.now().plusDays(10));
        req.setDescription("Created by integration test");
        req.setPublic(true);

        mockMvc.perform(post("/trips")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value(req.getTitle()));
    }

    @Test
    void listMyTrips() throws Exception {
        mockMvc.perform(get("/trips").param("page", "0").param("size", "10").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getTripById() throws Exception {
        mockMvc.perform(get("/trips/{id}", TestDataConstants.SEED_TRIP_ID).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TestDataConstants.SEED_TRIP_ID.toString()));
    }

    @Test
    void updateTrip() throws Exception {
        var req = new CreateTripRequest();
        req.setTitle("Saigon Street Food Tour (IT)");
        req.setOrigin("Ho Chi Minh City");
        req.setDestination("Ho Chi Minh City");
        req.setPublic(true);
        mockMvc.perform(patch("/trips/{id}", TestDataConstants.SEED_TRIP_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void addStop() throws Exception {
        var req = new AddStopRequest();
        req.setPlaceId(TestDataConstants.SEED_PLACE_ID);
        req.setName("Ben Thanh Market");
        req.setDurationMin(90);
        req.setOrderIdx(99.0);
        mockMvc.perform(post("/trips/{id}/stops", TestDataConstants.SEED_TRIP_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shareTrip() throws Exception {
        var req = new ShareTripRequest();
        req.setScope(ShareScope.VIEW);
        req.setTtlDays(3);
        mockMvc.perform(post("/trips/{id}/share", TestDataConstants.SEED_TRIP_ID)
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void startAndEndTrip() throws Exception {
        var create = new CreateTripRequest();
        create.setTitle("Live session IT trip");
        create.setOrigin("Hue");
        create.setDestination("Hue");
        create.setPublic(false);
        var created = mockMvc.perform(post("/trips")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();
        var tripId = readJson(created).path("data").path("id").asText();

        mockMvc.perform(post("/trips/{id}/start", tripId).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/trips/{id}/end", tripId).with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
