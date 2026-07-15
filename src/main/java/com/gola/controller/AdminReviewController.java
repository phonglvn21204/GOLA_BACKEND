package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.review.ReviewModerationRequest;
import com.gola.dto.review.ReviewResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String status) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listForAdmin(status, PageRequest.of(page, size))));
    }

    @PatchMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<ReviewResponse>> hide(@PathVariable UUID id, @RequestBody(required = false) ReviewModerationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ẩn đánh giá",
            reviewService.hide(id, SecurityUtils.getCurrentUserId(), req != null ? req.getReason() : null)));
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<ReviewResponse>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Đã khôi phục đánh giá", reviewService.restore(id)));
    }
}
