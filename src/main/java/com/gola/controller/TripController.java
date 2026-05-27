package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.security.SecurityUtils;
import com.gola.dto.trip.*;
import com.gola.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "CRUD, live sessions, share")
public class TripController {
    private final TripService tripService;

    @PostMapping
    @Operation(summary = "Create a new trip")
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(@Valid @RequestBody CreateTripRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Trip created", tripService.createTrip(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping
    @Operation(summary = "List my trips")
    public ResponseEntity<ApiResponse<PageResponse<TripResponse>>> listTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            tripService.listMyTrips(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trip details")
    public ResponseEntity<ApiResponse<TripResponse>> getTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getTrip(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update trip")
    public ResponseEntity<ApiResponse<TripResponse>> updateTrip(
            @PathVariable UUID id, @Valid @RequestBody CreateTripRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.updateTrip(id, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete trip (soft)")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(@PathVariable UUID id) {
        tripService.deleteTrip(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Trip deleted", null));
    }

    @PostMapping("/{id}/stops")
    @Operation(summary = "Add stop to trip")
    public ResponseEntity<ApiResponse<TripStopResponse>> addStop(
            @PathVariable UUID id, @Valid @RequestBody AddStopRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Stop added", tripService.addStop(id, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/{id}/stops/{stopId}")
    @Operation(summary = "Remove stop from trip")
    public ResponseEntity<ApiResponse<Void>> deleteStop(@PathVariable UUID id, @PathVariable UUID stopId) {
        tripService.deleteStop(id, stopId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Stop removed", null));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start live trip session")
    public ResponseEntity<ApiResponse<Object>> startTrip(@PathVariable UUID id) {
        var session = tripService.startTrip(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Trip started", Map.of("sessionId", session.getId())));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End live trip session")
    public ResponseEntity<ApiResponse<Void>> endTrip(@PathVariable UUID id) {
        tripService.endTrip(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Trip ended", null));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Generate a share token")
    public ResponseEntity<ApiResponse<Object>> shareTrip(
            @PathVariable UUID id, @RequestBody ShareTripRequest req) {
        String token = tripService.createShareLink(id, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Share link created", Map.of("token", token)));
    }
}