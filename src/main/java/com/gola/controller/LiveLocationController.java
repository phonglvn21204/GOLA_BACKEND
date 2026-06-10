package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.trip.LiveLocationRequest;
import com.gola.dto.trip.LiveLocationResponse;
import com.gola.dto.trip.TripSessionResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.LiveLocationService;
import com.gola.service.TripSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/live")
@RequiredArgsConstructor
@Tag(name = "Live Location", description = "Real-time GPS location sharing within active trip sessions")
public class LiveLocationController {

    private final LiveLocationService liveLocationService;
    private final TripSessionService tripSessionService;

    @PostMapping("/sessions")
    @Operation(summary = "Start a live trip session")
    public ResponseEntity<ApiResponse<TripSessionResponse>> startLiveSession(@Valid @RequestBody StartLiveSessionRequest req) {
        TripSessionResponse res = tripSessionService.startSession(req.getTripId(), SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Session started", res));
    }

    @PostMapping("/sessions/{sessionId}/end")
    @Operation(summary = "End a live trip session")
    public ResponseEntity<ApiResponse<TripSessionResponse>> endLiveSession(@PathVariable UUID sessionId) {
        TripSessionResponse res = tripSessionService.endSessionById(sessionId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Session ended", res));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get live trip session by ID")
    public ResponseEntity<ApiResponse<TripSessionResponse>> getLiveSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(tripSessionService.getSessionById(sessionId, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{sessionId}/ping")
    @Operation(summary = "Send a GPS location ping for the current user")
    public ResponseEntity<ApiResponse<LiveLocationResponse>> ping(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LiveLocationRequest req) {
        LiveLocationResponse res = liveLocationService.ping(sessionId, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @PostMapping("/locations")
    @Operation(summary = "Send a GPS location ping for the current user")
    public ResponseEntity<ApiResponse<LiveLocationResponse>> pingLocation(@Valid @RequestBody LiveLocationPingRequest req) {
        LiveLocationRequest location = new LiveLocationRequest();
        location.setLat(req.getLat());
        location.setLng(req.getLng());
        location.setHeading(req.getHeading());
        location.setSpeed(req.getSpeed());
        location.setAccuracy(req.getAccuracy());
        LiveLocationResponse res = liveLocationService.ping(req.getSessionId(), SecurityUtils.getCurrentUserId(), location);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @GetMapping("/{sessionId}/locations")
    @Operation(summary = "Get the latest location of all members in a session")
    public ResponseEntity<ApiResponse<List<LiveLocationResponse>>> getLatestLocations(
            @PathVariable UUID sessionId) {
        List<LiveLocationResponse> res = liveLocationService.getLatestLocations(sessionId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @Data
    public static class StartLiveSessionRequest {
        private UUID tripId;
    }

    @Data
    public static class LiveLocationPingRequest {
        private UUID sessionId;
        private Double lat;
        private Double lng;
        private Double heading;
        private Double speed;
        private Double accuracy;
    }
}
