package com.gola.service;

import com.gola.dto.quest.RewardResponse;
import com.gola.entity.Reward;
import com.gola.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RewardService {

    private final RewardRepository rewardRepo;

    public List<RewardResponse> getActiveRewards() {
        return rewardRepo.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private RewardResponse toResponse(Reward reward) {
        return RewardResponse.builder()
                .id(reward.getId())
                .name(reward.getName())
                .description(reward.getDescription())
                .costCoins(reward.getCostCoins())
                .stock(reward.getStock())
                .imageUrl(reward.getImageUrl())
                .isActive(reward.isActive())
                .build();
    }
}
