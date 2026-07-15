package com.gola.dto.quest;

import com.gola.entity.enums.QuestProgressStatus;
import com.gola.entity.enums.QuestType;
import com.gola.entity.enums.QuestDifficulty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class QuestProgressResponse {
    private UUID id;
    private UUID questId;
    private UUID userId;
    private String userDisplayName;
    private String userEmail;
    private String userAvatarUrl;
    private UUID tripId;
    private UUID tripStopId;
    private int taskIdx;
    private QuestProgressStatus status;
    private String questTitle;
    private String questDescription;
    private String questDestination;
    private String targetName;
    private QuestType type;
    private int rewardCoins;
    private int xpReward;
    private int rewardPointsAwarded;
    private QuestDifficulty difficulty;
    private String iconKey;
    private Double radiusMeters;
    private String tripStopName;
    private String tripStopCategory;
    private Double targetLat;
    private Double targetLng;
    private String proofImageUrl;
    private UUID mediaId;
    private Double completedLat;
    private Double completedLng;
    private Double distanceMetersFromTarget;
    private Boolean gpsValid;
    private boolean xpAwarded;
    private String flagReason;
    private String rejectReason;
    private String note;
    private String adminNote;
    private Instant startedAt;
    private Instant submittedAt;
    private Instant completedAt;
    private Instant verifiedAt;
    private Instant approvedAt;
    private UUID approvedBy;
    private Instant rejectedAt;
    private UUID rejectedBy;
    private Instant createdAt;
}
