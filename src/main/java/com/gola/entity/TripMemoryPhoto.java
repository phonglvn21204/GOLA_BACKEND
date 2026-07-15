package com.gola.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "trip_memory_photos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TripMemoryPhoto extends BaseEntity {
    @Column(name = "trip_memory_id", nullable = false)
    private UUID tripMemoryId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "day_index")
    private Integer dayIndex;

    @Column(name = "trip_stop_id")
    private UUID tripStopId;

    @Column(name = "caption_note")
    private String captionNote;

    @Column(name = "ai_caption")
    private String aiCaption;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
