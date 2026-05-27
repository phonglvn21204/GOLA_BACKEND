package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.trip.TripSessionResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.TripSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Tag(name = "Trip Sessions", description = "Start and end live trip sessions")
public class TripSessionController {

    private final TripSessionService tripSessionService;

    @PostMapping("/{id}/session/start")
    @Operation(summary = "Start a live trip session")
    public ResponseEntity<ApiResponse<TripSessionResponse>> startSession(@PathVariable UUID id) {
        TripSessionResponse res = tripSessionService.startSession(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Session started", res));
    }

    @PostMapping("/{id}/session/end")
    @Operation(summary = "End the active live trip session")
    public ResponseEntity<ApiResponse<TripSessionResponse>> endSession(@PathVariable UUID id) {
        TripSessionResponse res = tripSessionService.endSession(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Session ended", res));
    }

    @GetMapping("/{id}/session/active")
    @Operation(summary = "Get current active session for a trip")
    public ResponseEntity<ApiResponse<TripSessionResponse>> getActiveSession(@PathVariable UUID id) {
        TripSessionResponse res = tripSessionService.getActiveSession(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}
