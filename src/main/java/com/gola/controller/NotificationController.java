package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.entity.Notification;
import com.gola.repository.NotificationRepository;
import com.gola.service.NotificationService;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationRepository repo;
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Notification>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(new PageResponse<>(
            repo.findByUserIdOrderByCreatedAtDesc(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size)))));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unread() {
        long count = repo.countByUserIdAndReadAtIsNull(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        service.markAllRead(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read", null));
    }
}