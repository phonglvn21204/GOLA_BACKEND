package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.BadgeResponse;
import com.gola.entity.Wallet;
import com.gola.repository.BadgeRepository;
import com.gola.repository.UserBadgeRepository;
import com.gola.repository.WalletRepository;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "Me Gamification")
public class MeGamificationController {
    private final WalletRepository walletRepo;
    private final UserBadgeRepository userBadgeRepo;
    private final BadgeRepository badgeRepo;

    @GetMapping("/coins")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> coins() {
        var userId = SecurityUtils.getCurrentUserId();
        int coins = walletRepo.findById(userId).map(Wallet::getGolaCoins).orElse(0);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("coins", coins)));
    }

    @GetMapping("/badges")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> badges() {
        var badges = userBadgeRepo.findByUserId(SecurityUtils.getCurrentUserId()).stream()
                .map(userBadge -> badgeRepo.findById(userBadge.getBadgeId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(badge -> BadgeResponse.builder()
                        .id(badge.getId())
                        .name(badge.getName())
                        .iconUrl(badge.getIconUrl())
                        .criteria(badge.getCriteria())
                        .isActive(badge.isActive())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(badges));
    }

    @GetMapping("/rewards")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rewards() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "redemptions", List.of(),
                "message", "Reward history is not implemented yet"
        )));
    }
}
