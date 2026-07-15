package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.safety.ApplyIncidentSuggestionRequest;
import com.gola.dto.safety.ApplyIncidentSuggestionResponse;
import com.gola.dto.safety.IncidentAiSuggestionsResponse;
import com.gola.dto.safety.IncidentRequest;
import com.gola.dto.safety.IncidentResponse;
import com.gola.entity.enums.IncidentType;
import com.gola.security.SecurityUtils;
import com.gola.service.IncidentAiService;
import com.gola.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Incident reporting for safety")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentAiService incidentAiService;

    @PostMapping
    @Operation(summary = "Report a new incident")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody IncidentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Incident reported",
                        incidentService.createIncident(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping("/trip/{tripId}")
    @Operation(summary = "Get incidents for a trip")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> getTripIncidentsLegacy(
            @PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                incidentService.getTripIncidents(tripId, SecurityUtils.getCurrentUserId(), page, size)));
    }


    @GetMapping
    @Operation(summary = "Get my incidents")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> getMyIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                incidentService.getUserIncidents(SecurityUtils.getCurrentUserId(), page, size)));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get nearby open incidents")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> getNearbyIncidents(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.getNearbyIncidents(type, page, size)));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify that an incident is still valid")
    public ResponseEntity<ApiResponse<IncidentResponse>> verifyIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Incident verified",
                incidentService.verify(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/ai-suggestions")
    @Operation(summary = "Generate AI itinerary suggestions for an incident")
    public ResponseEntity<ApiResponse<IncidentAiSuggestionsResponse>> aiSuggestions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("AI suggestions generated",
                incidentAiService.generateSuggestions(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/apply-suggestion")
    @Operation(summary = "Apply an AI incident suggestion to the trip itinerary")
    public ResponseEntity<ApiResponse<ApplyIncidentSuggestionResponse>> applySuggestion(
            @PathVariable UUID id,
            @RequestBody(required = false) ApplyIncidentSuggestionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Incident suggestion applied",
                incidentAiService.applySuggestion(id, SecurityUtils.getCurrentUserId(), req != null ? req.getSuggestionId() : null)));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an incident")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Incident resolved",
                incidentService.resolveIncident(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an incident")
    public ResponseEntity<ApiResponse<IncidentResponse>> cancelIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Incident cancelled",
                incidentService.cancelIncident(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by ID")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncidentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.getIncidentById(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update incident status (admin only)")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                incidentService.updateIncidentStatus(id, req.getStatus())));
    }

    @Data
    static class UpdateStatusRequest {
        private String status;
    }
}
