package com.gola.entity;

import com.gola.entity.enums.NotifType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notif_type")
    @Builder.Default
    private NotifType type = NotifType.SYSTEM;
    private String title;
    private String body;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> payload;
    @Column(name = "read_at") private Instant readAt;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
}