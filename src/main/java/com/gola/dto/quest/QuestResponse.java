package com.gola.dto.quest;

import com.gola.entity.enums.QuestType;
import com.gola.entity.enums.QuestDifficulty;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestResponse {
    private UUID id;
    private QuestType type;
    private String title;
    private String description;
    private String destination;
    private String targetName;
    private Double targetLat;
    private Double targetLng;
    private int rewardCoins;
    private int xpReward;
    private int rewardPoints;
    private Double radiusMeters;
    private String status;
    private QuestDifficulty difficulty;
    private String iconKey;
    private UUID badgeId;
    private UUID rewardId;
    private boolean isFeatured;
    private boolean isActive;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
