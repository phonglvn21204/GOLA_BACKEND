package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "albums")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "trip_id")
    private UUID tripId;

    private String title;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @Column(name = "is_ai_curated", nullable = false)
    @Builder.Default
    private boolean isAiCurated = false;

    @Column(name = "album_source", nullable = false)
    @Builder.Default
    private String albumSource = "ITINERARY";

    @Column(nullable = false)
    @Builder.Default
    private String status = "NOT_GENERATED";

    private String summary;

    private String mood;

    @Column(name = "cover_caption")
    private String coverCaption;

    @Column(name = "share_caption")
    private String shareCaption;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private String[] hashtags = new String[0];

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
