package com.gola.entity;

import com.gola.entity.enums.RedemptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redemptions")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Redemption extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reward_id", nullable = false)
    private UUID rewardId;

    @Column(unique = true)
    private String code;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RedemptionStatus status = RedemptionStatus.PENDING;
}
