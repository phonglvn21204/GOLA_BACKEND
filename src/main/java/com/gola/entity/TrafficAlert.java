package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "traffic_alerts",
       indexes = @Index(name = "idx_traffic_alert_session", columnList = "session_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrafficAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "ts", nullable = false)
    @Builder.Default
    private Instant ts = Instant.now();
}
