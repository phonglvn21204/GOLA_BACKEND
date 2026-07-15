package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.map.ExplorePlaceResponse;
import com.gola.entity.enums.ExploreCategory;
import com.gola.exception.GolaException;
import com.gola.service.ExploreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/explore", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
@Tag(name = "Explore", description = "Goong-powered nearby place discovery")
public class ExploreController {
    private final ExploreService exploreService;

    @GetMapping("/places")
    @Operation(summary = "Get nearby explore places by destination and category")
    public ResponseEntity<ApiResponse<List<ExplorePlaceResponse>>> getPlaces(
            @RequestParam String destination,
            @RequestParam ExploreCategory category) {
        if (destination == null || destination.isBlank()) {
            throw GolaException.badRequest("destination is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(exploreService.getNearbyPlaces(destination, category)));
    }
}
