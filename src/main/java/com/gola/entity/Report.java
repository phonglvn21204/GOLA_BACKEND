package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "reports")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Report extends BaseEntity {

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "target_type", nullable = false)
    private String targetType; // e.g., POST, COMMENT, USER

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private String status = "OPEN";
}
