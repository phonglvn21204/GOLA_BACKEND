package com.gola.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "album_media")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlbumMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "album_id", nullable = false)
    private UUID albumId;

    @Column(name = "trip_stop_id")
    private UUID tripStopId;

    @Column(name = "memory_photo_id")
    private UUID memoryPhotoId;

    @Column(name = "media_url")
    private String mediaUrl;

    private String caption;

    @Column(name = "order_idx", nullable = false)
    @Builder.Default
    private int orderIdx = 0;

    @Column(name = "day_index", nullable = false)
    @Builder.Default
    private int dayIndex = 1;

    @Column(name = "day_title")
    private String dayTitle;

    @Column(name = "day_summary")
    private String daySummary;

    @Column(name = "stop_name")
    private String stopName;

    private String highlight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
