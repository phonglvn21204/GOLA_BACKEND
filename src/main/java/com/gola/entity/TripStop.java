package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_stops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "order_idx", nullable = false)
    private double orderIdx;

    private String name;

    @Column(name = "arrival_at")
    private Instant arrivalAt;

    @Column(name = "duration_min")
    private Integer durationMin;

    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}