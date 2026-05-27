package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.BadgeResponse;
import com.gola.service.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/badges")
@RequiredArgsConstructor
@Tag(name = "Badges", description = "Quest badges and achievements")
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping
    @Operation(summary = "Get all active badges")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getAllBadges() {
        return ResponseEntity.ok(ApiResponse.ok(badgeService.getAllActiveBadges()));
    }
}
