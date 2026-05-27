package com.gola.entity;

import com.gola.entity.enums.AiJobKind;
import com.gola.entity.enums.AiJobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "ai_jobs")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiJob {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false)
    private AiJobKind kind;
    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private AiJobStatus status = AiJobStatus.QUEUED;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> input;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> output;
    @Column(name = "tokens_in") private Integer tokensIn;
    @Column(name = "tokens_out") private Integer tokensOut;
    @Column(name = "cost_usd") private BigDecimal costUsd;
    private String error;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}