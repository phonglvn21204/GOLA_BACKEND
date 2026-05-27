package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.map.PlaceResponse;
import com.gola.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/places")
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

    @GetMapping("/{id}")
    @Operation(summary = "Get place details by local ID")
    public ResponseEntity<ApiResponse<PlaceResponse>> getPlaceById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(placeService.getPlaceById(id)));
    }
}
