package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "trip_notes",
       indexes = @Index(name = "idx_trip_note_trip", columnList = "trip_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripNote extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 2000)
    private String content;
}
