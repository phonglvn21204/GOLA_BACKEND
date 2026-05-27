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
@Table(name = "quest_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"quest_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestProgress extends BaseEntity {

    @Column(name = "quest_id", nullable = false)
    private UUID questId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
