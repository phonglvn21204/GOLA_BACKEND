package com.gola.entity;

import com.gola.entity.enums.QuestType;
import com.gola.entity.enums.QuestDifficulty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "quests")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "quest_type")
    @Builder.Default
    private QuestType type = QuestType.SOLO;
    @Column(nullable = false) private String title;
    private String description;
    private String destination;
    @Column(name = "target_name") private String targetName;
    @Column(name = "target_lat") private Double targetLat;
    @Column(name = "target_lng") private Double targetLng;
    @Column(name = "reward_coins") private int rewardCoins;
    @Column(name = "radius_m") private Double radiusMeters;
    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private QuestDifficulty difficulty = QuestDifficulty.EASY;
    @Column(name = "icon_key") private String iconKey;
    @Column(name = "badge_id") private UUID badgeId;
    @Column(name = "reward_id") private UUID rewardId;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "is_featured") private boolean isFeatured;
    @Column(name = "is_active") @Builder.Default private boolean isActive = true;
    @Column(name = "expires_at") private Instant expiresAt;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
}
