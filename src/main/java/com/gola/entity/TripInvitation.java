package com.gola.entity;

import com.gola.entity.enums.InvitationStatus;
import com.gola.entity.enums.MemberRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_invitations")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripInvitation extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private String email;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "member_role")
    @Builder.Default
    private MemberRole role = MemberRole.VIEWER;
}
