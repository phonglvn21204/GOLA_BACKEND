package com.gola.dto.quest;

import com.gola.entity.enums.QuestType;
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
    private int rewardCoins;
    private UUID badgeId;
    private boolean isFeatured;
    private boolean isActive;
    private Instant expiresAt;
    private Instant createdAt;
}
