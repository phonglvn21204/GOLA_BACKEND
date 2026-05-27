package com.gola.entity;

import com.gola.entity.enums.MemberRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(TripMemberId.class)
public class TripMember {
    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", insertable = false, updatable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "member_role")
    @Builder.Default
    private MemberRole role = MemberRole.VIEWER;

    @Column(name = "joined_at", updatable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();
}