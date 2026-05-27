package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.trip.LiveLocationRequest;
import com.gola.dto.trip.LiveLocationResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.LiveLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @PostMapping("/{sessionId}/ping")
    @Operation(summary = "Send a GPS location ping for the current user")
    public ResponseEntity<ApiResponse<LiveLocationResponse>> ping(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LiveLocationRequest req) {
        LiveLocationResponse res = liveLocationService.ping(sessionId, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @GetMapping("/{sessionId}/locations")
    @Operation(summary = "Get the latest location of all members in a session")
    public ResponseEntity<ApiResponse<List<LiveLocationResponse>>> getLatestLocations(
            @PathVariable UUID sessionId) {
        List<LiveLocationResponse> res = liveLocationService.getLatestLocations(sessionId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}
