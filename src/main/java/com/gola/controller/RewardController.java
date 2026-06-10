package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.RedemptionRequest;
import com.gola.dto.quest.RewardResponse;
import com.gola.entity.Redemption;
import com.gola.security.SecurityUtils;
import com.gola.service.RedemptionService;
import com.gola.service.RewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rewards")
@RequiredArgsConstructor
@Tag(name = "Rewards", description = "Rewards and redemptions")
public class RewardController {

    private final RewardService rewardService;
    private final RedemptionService redemptionService;

    @GetMapping
    @Operation(summary = "Get all active rewards")
    public ResponseEntity<ApiResponse<List<RewardResponse>>> getActiveRewards() {
        return ResponseEntity.ok(ApiResponse.ok(rewardService.getActiveRewards()));
    }

    @PostMapping("/redeem")
    @Operation(summary = "Redeem a reward using coins")
    public ResponseEntity<ApiResponse<Redemption>> redeemReward(@Valid @RequestBody RedemptionRequest req) {
        Redemption redemption = redemptionService.redeemReward(SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Reward redeemed successfully", redemption));
    }

    @PostMapping("/{id}/redeem")
    @Operation(summary = "Redeem a reward using coins")
    public ResponseEntity<ApiResponse<Redemption>> redeemRewardById(@PathVariable UUID id) {
        RedemptionRequest req = new RedemptionRequest();
        req.setRewardId(id);
        Redemption redemption = redemptionService.redeemReward(SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Reward redeemed successfully", redemption));
    }
}
