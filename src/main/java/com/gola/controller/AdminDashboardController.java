package com.gola.controller;

import com.gola.dto.admin.AdminDashboardSummaryResponse;
import com.gola.dto.common.ApiResponse;
import com.gola.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "Admin dashboard summaries and charts")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get admin dashboard summary metrics")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.summary()));
    }
}
