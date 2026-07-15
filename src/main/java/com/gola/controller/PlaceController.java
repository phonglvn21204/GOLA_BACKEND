package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.map.AutocompleteSuggestion;
import com.gola.dto.map.PlaceDetail;
import com.gola.dto.map.PlaceResponse;
import com.gola.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/places", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
@Tag(name = "Places", description = "Points of Interest database and cache")
public class PlaceController {
    private final PlaceService placeService;

    @GetMapping("/search")
    @Operation(summary = "Search places locally with fallback to external providers")
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> searchPlaces(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(ApiResponse.ok(placeService.searchPlaces(query, lat, lng)));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Autocomplete places via Goong with Nominatim fallback")
    public ResponseEntity<ApiResponse<List<AutocompleteSuggestion>>> autocompletePlaces(
            @RequestParam String input) {
        return ResponseEntity.ok(ApiResponse.ok(placeService.searchAutocomplete(input)));
    }

    @GetMapping("/detail")
    @Operation(summary = "Get place details from Goong place ID")
    public ResponseEntity<ApiResponse<PlaceDetail>> getPlaceDetail(@RequestParam String placeId) {
        return ResponseEntity.ok(ApiResponse.ok(placeService.getPlaceDetail(placeId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get place details by local ID")
    public ResponseEntity<ApiResponse<PlaceResponse>> getPlaceById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(placeService.getPlaceById(id)));
    }
}
