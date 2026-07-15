package com.gola.entity;

import com.gola.entity.enums.IncidentSeverity;
import com.gola.entity.enums.IncidentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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
    @Column(name = "current_stop_id")
    private UUID currentStopId;
    @Column(name = "affected_stop_id")
    private UUID affectedStopId;
    @Column(name = "estimated_delay_minutes")
    private Integer estimatedDelayMinutes;
    @Column(name = "needs_ai_reroute", nullable = false)
    @Builder.Default
    private boolean needsAiReroute = false;
    private String context;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "incident_severity")
    @Builder.Default
    private IncidentSeverity severity = IncidentSeverity.MEDIUM;
    @Column(name = "verified_count")
    @Builder.Default
    private Integer verifiedCount = 0;
    @Builder.Default private String status = "OPEN";
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
    @Column(name = "ai_suggestion_json", columnDefinition = "TEXT")
    private String aiSuggestionJson;
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "media_urls", columnDefinition = "text[]")
    private String[] mediaUrls;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
}
