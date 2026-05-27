package com.gola.entity;

import com.gola.entity.enums.IncidentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "incidents")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "trip_id") private UUID tripId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "incident_type")
    @Builder.Default
    private IncidentType type = IncidentType.OTHER;
    private String description;
    private Double latitude;
    private Double longitude;
    @Builder.Default private String status = "OPEN";
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "media_urls", columnDefinition = "text[]")
    private String[] mediaUrls;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
}