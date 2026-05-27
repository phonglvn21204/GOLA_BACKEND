package com.gola.entity;

import com.gola.entity.enums.AiJobKind;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "ai_quotas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(AiQuotaId.class)
public class AiQuota {
    @Id @Column(name = "user_id") private UUID userId;
    @Id
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Column(name = "kind")
    private AiJobKind kind;
    @Id @Column(name = "period_start") private LocalDate periodStart;
    @Builder.Default private int count = 0;
}
