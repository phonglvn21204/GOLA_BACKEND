package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "live_locations",
       indexes = {
           @Index(name = "idx_live_loc_session", columnList = "session_id"),
           @Index(name = "idx_live_loc_user",    columnList = "user_id")
       })
@IdClass(LiveLocationId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LiveLocation {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "ts", nullable = false)
    @Builder.Default
    private Instant ts = Instant.now();

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private Double heading;

    private Double speed;

    private Double accuracy;
}
