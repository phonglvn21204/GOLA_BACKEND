package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.community.PostResponse;
import com.gola.dto.safety.IncidentResponse;
import com.gola.dto.user.ProfileResponse;
import com.gola.entity.SosEvent;
import com.gola.entity.enums.AppRole;
import com.gola.security.SecurityUtils;
import com.gola.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only platform management endpoints")
public class AdminController {

    private final AdminService adminService;

    // ── Metrics ───────────────────────────────────────────────────────────────

    @GetMapping("/metrics")
    @Operation(summary = "Platform metrics: users, active trips, open reports, quest completions (7d)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getMetrics()));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "Paginated list of all users")
    public ResponseEntity<ApiResponse<PageResponse<ProfileResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(PageRequest.of(page, size))));
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role")
    public ResponseEntity<ApiResponse<Void>> changeUserRole(
            @PathVariable UUID id,
            @RequestBody ChangeRoleRequest req) {
        adminService.changeUserRole(id, req.getRole());
        return ResponseEntity.ok(ApiResponse.ok("Role updated", null));
    }

    @PatchMapping("/users/{id}/block")
    @Operation(summary = "Block a user account")
    public ResponseEntity<ApiResponse<ProfileResponse>> blockUser(
            @PathVariable UUID id,
            @RequestBody(required = false) BlockUserRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("User blocked",
                adminService.blockUser(id, SecurityUtils.getCurrentUserId(), req != null ? req.getReason() : null)));
    }

    @PatchMapping("/users/{id}/unblock")
    @Operation(summary = "Unblock a user account")
    public ResponseEntity<ApiResponse<ProfileResponse>> unblockUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("User unblocked", adminService.unblockUser(id)));
    }

    // ── Incidents ─────────────────────────────────────────────────────────────

    @GetMapping("/incidents")
    @Operation(summary = "Paginated list of all incidents")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listAllIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAllIncidents(PageRequest.of(page, size))));
    }

    @PatchMapping("/incidents/{id}/status")
    @Operation(summary = "Update an incident's status")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateIncidentStatus(
            @PathVariable UUID id,
            @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                adminService.updateIncidentStatus(id, req.getStatus())));
    }

    // ── Posts ─────────────────────────────────────────────────────────────────

    @GetMapping("/posts")
    @Operation(summary = "Paginated list of all posts including hidden")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> listAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAllPosts(PageRequest.of(page, size))));
    }

    @PatchMapping("/posts/{id}/hide")
    @Operation(summary = "Hide a post (sets is_hidden = true)")
    public ResponseEntity<ApiResponse<Void>> hidePost(@PathVariable UUID id) {
        adminService.hidePost(id);
        return ResponseEntity.ok(ApiResponse.ok("Post hidden", null));
    }

    // ── SOS ───────────────────────────────────────────────────────────────────

    @GetMapping("/sos/active")
    @Operation(summary = "List all currently active SOS events")
    public ResponseEntity<ApiResponse<List<SosEvent>>> getActiveSos() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getActiveSosEvents()));
    }

    // ── Inner request DTOs ────────────────────────────────────────────────────

    @Data
    static class ChangeRoleRequest {
        @NotNull
        private AppRole role;
    }

    @Data
    static class BlockUserRequest {
        private String reason;
    }

    @Data
    static class UpdateStatusRequest {
        private String status;
    }
}
