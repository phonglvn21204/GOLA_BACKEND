package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.safety.ReportRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Safety reporting")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Submit a report")
    public ResponseEntity<ApiResponse<Void>> submitReport(@Valid @RequestBody ReportRequest req) {
        reportService.submitReport(SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Report submitted successfully", null));
    }
}
