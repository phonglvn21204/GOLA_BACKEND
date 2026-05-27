package com.gola.entity;

import com.gola.entity.enums.ShareScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_shares")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripShare {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "share_scope")
    @Builder.Default
    private ShareScope scope = ShareScope.VIEW;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isValid() {
        return revokedAt == null && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
}