package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.entity.DeviceToken;
import com.gola.entity.enums.Platform;
import com.gola.repository.DeviceTokenRepository;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/me/push-tokens")
@RequiredArgsConstructor
@Tag(name = "Push Tokens")
public class PushTokenController {
    private final DeviceTokenRepository tokenRepo;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceToken>> upsert(@RequestBody PushTokenRequest req) {
        var userId = SecurityUtils.getCurrentUserId();
        DeviceToken token = tokenRepo.findByUserIdAndToken(userId, req.getToken())
                .orElseGet(() -> DeviceToken.builder()
                        .userId(userId)
                        .token(req.getToken())
                        .platform(req.getPlatform() != null ? req.getPlatform() : Platform.WEB)
                        .build());
        token.setLastUsedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.ok("Push token saved", tokenRepo.save(token)));
    }

    @Data
    public static class PushTokenRequest {
        private String token;
        private Platform platform;
    }
}
