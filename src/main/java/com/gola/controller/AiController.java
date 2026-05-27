package com.gola.controller;

import com.gola.dto.ai.GenerateTripRequest;
import com.gola.service.AiTripService;
import com.gola.service.BillingService;
import com.gola.dto.common.ApiResponse;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI trip generation, album curation")
public class AiController {
    private final AiTripService aiTripService;
    private final BillingService billingService;

    @PostMapping("/generate-trip")
    @Operation(summary = "Generate AI itinerary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateTrip(@Valid @RequestBody GenerateTripRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        var result = aiTripService.generateTrip(userId, req, billingService.hasActivePremium(userId));
        return ResponseEntity.ok(ApiResponse.ok("Trip generated", result));
    }
}