package com.gola.entity;

import com.gola.entity.enums.AiQuotaKind;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "ai_quota_limits",
       uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "kind"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiQuotaLimit extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiQuotaKind kind;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;
}
