package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.safety.*;
import com.gola.exception.GolaException;
import com.gola.service.BillingService;
import com.gola.service.SosService;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@RestController @RequestMapping("/sos")
@RequiredArgsConstructor
@Tag(name = "SOS Emergency", description = "Trigger, resolve, and monitor SOS events")
public class SosController {
    private final SosService sosService;
    private final BillingService billingService;

    @PostMapping("/trigger")
    @Operation(summary = "Trigger SOS emergency")
    public ResponseEntity<ApiResponse<SosResponse>> trigger(@RequestBody SosTriggerRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        
        if (!billingService.hasActivePremium(userId)) {
            throw new GolaException(HttpStatus.FORBIDDEN, "PREMIUM_REQUIRED", "This feature requires Premium.");
        }
        
        return ResponseEntity.ok(ApiResponse.ok("SOS triggered", sosService.triggerSos(userId, req)));
    }

    @PostMapping("/{sosId}/resolve")
    @Operation(summary = "Resolve SOS event")
    public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable UUID sosId, @RequestBody Map<String,String> body) {
        sosService.resolveSos(sosId, SecurityUtils.getCurrentUserId(), body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok("SOS resolved", null));
    }

    @PostMapping("/{sosId}/acknowledge")
    @Operation(summary = "Acknowledge SOS event")
    public ResponseEntity<ApiResponse<SosResponse>> acknowledge(@PathVariable UUID sosId) {
        return ResponseEntity.ok(ApiResponse.ok("SOS acknowledged",
                sosService.acknowledge(sosId, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/active")
    @Operation(summary = "List active SOS events")
    public ResponseEntity<ApiResponse<List<SosResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.ok(sosService.getActiveSosEvents().stream()
                .map(sosService::mapToResponse)
                .toList()));
    }
}
