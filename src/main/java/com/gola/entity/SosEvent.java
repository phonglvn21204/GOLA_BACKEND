package com.gola.entity;

import com.gola.entity.enums.SosStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "sos_events")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SosEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "trip_id") private UUID tripId;
    @Column(name = "latitude")  private Double latitude;
    @Column(name = "longitude") private Double longitude;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "sos_status")
    @Builder.Default
    private SosStatus status = SosStatus.ACTIVE;
    @Column(name = "client_token", unique = true) private String clientToken;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
}