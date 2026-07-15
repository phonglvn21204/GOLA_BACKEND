package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.review.ReviewRequest;
import com.gola.dto.review.ReviewResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@Valid @RequestBody ReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi đánh giá", reviewService.create(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> recent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.recent(PageRequest.of(page, size))));
    }
}
