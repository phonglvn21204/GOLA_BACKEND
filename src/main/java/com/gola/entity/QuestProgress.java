package com.gola.entity;

import com.gola.entity.enums.QuestProgressStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quest_progress")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestProgress extends BaseEntity {

    @Column(name = "quest_id", nullable = false)
    private UUID questId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "trip_stop_id")
    private UUID tripStopId;

    @Column(name = "task_idx", nullable = false)
    @Builder.Default
    private int taskIdx = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "progress_status")
    @Builder.Default
    private QuestProgressStatus status = QuestProgressStatus.IN_PROGRESS;

    @Column(name = "proof_media_id")
    private UUID proofMediaId;

    @Column(name = "proof_media_url")
    private String proofImageUrl;

    @Column(name = "submitted_lat")
    private Double completedLatitude;

    @Column(name = "submitted_lng")
    private Double completedLongitude;

    @Column(name = "distance_meters_from_target")
    private Double distanceMetersFromTarget;

    @Column(name = "gps_valid")
    private Boolean gpsValid;

    @Column(name = "note")
    private String note;

    @Column(name = "xp_awarded", nullable = false)
    @Builder.Default
    private boolean xpAwarded = false;

    @Column(name = "reward_points_awarded", nullable = false)
    @Builder.Default
    private int rewardPointsAwarded = 0;

    @Column(name = "flag_reason")
    private String flagReason;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "admin_note")
    private String adminNote;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "started_at")
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;
}
