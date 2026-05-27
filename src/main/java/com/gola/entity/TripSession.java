package com.gola.entity;

import com.gola.entity.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "session_status")
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "started_at", updatable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;
}