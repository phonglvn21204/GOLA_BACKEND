package com.gola.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "trip_memories",
        uniqueConstraints = @UniqueConstraint(name = "uq_trip_memories_trip_user", columnNames = {"trip_id", "user_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripMemory extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    private String summary;

    @Column(nullable = false)
    @Builder.Default
    private String status = "NOT_GENERATED";

    @Column(name = "share_status", nullable = false)
    @Builder.Default
    private String shareStatus = "PRIVATE";

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "album_id")
    private UUID albumId;

    @Column(name = "album_status", nullable = false)
    @Builder.Default
    private String albumStatus = "NOT_GENERATED";

    @Column(name = "album_error")
    private String albumError;

    @Column(name = "album_generated_at")
    private Instant albumGeneratedAt;

    @Column(name = "book_status", nullable = false)
    @Builder.Default
    private String bookStatus = "NOT_GENERATED";

    @Column(name = "book_url")
    private String bookUrl;

    @Column(name = "book_file_path")
    private String bookFilePath;

    @Column(name = "book_error")
    private String bookError;

    @Column(name = "book_generated_at")
    private Instant bookGeneratedAt;

    @Column(name = "reel_status", nullable = false)
    @Builder.Default
    private String reelStatus = "NOT_GENERATED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reel_storyboard", columnDefinition = "jsonb")
    private Map<String, Object> reelStoryboard;

    @Column(name = "reel_error")
    private String reelError;

    @Column(name = "reel_generated_at")
    private Instant reelGeneratedAt;
}
