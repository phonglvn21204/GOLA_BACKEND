package com.gola.entity;

import com.gola.entity.enums.ProofType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "quest_tasks")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestTask extends BaseEntity {

    @Column(name = "quest_id", nullable = false)
    private UUID questId;

    @Column(name = "idx", nullable = false)
    private int idx;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "proof_type", nullable = false, columnDefinition = "proof_type")
    @Builder.Default
    private ProofType proofType = ProofType.PHOTO;

    @Column(name = "radius_m")
    private Double radiusM;
}
