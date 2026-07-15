package com.gola.dto.quest;

import com.gola.entity.enums.QuestDifficulty;
import com.gola.entity.enums.QuestType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AdminQuestRequest {
    @NotBlank
    private String title;
    private String description;
    private String destination;
    private QuestType type;
    private String targetName;
    private Double targetLat;
    private Double targetLng;
    private Integer xpReward;
    private Integer rewardCoins;
    private Integer rewardPoints;
    private Double radiusMeters;
    private String status;
    private QuestDifficulty difficulty;
    private String iconKey;
    private UUID badgeId;
    private UUID rewardId;
    private Boolean active;
    private Boolean featured;
    private Instant expiresAt;
}
