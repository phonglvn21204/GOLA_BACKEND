package com.gola.dto.quest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class RewardResponse {
    private UUID id;
    private String name;
    private String description;
    private int costCoins;
    private int stock;
    private String imageUrl;
    private boolean isActive;
}
