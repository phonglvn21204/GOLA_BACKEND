package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "category", length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Double lat;

    private Double lng;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "trip_stop_images", joinColumns = @JoinColumn(name = "trip_stop_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private List<String> photoUrls = new ArrayList<>();

    @Column(name = "phone", length = 100)
    private String phone;

    @Column(name = "website", columnDefinition = "TEXT")
    private String website;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "image_source", length = 100)
    private String imageSource;

    @Column(name = "place_address", columnDefinition = "TEXT")
    private String placeAddress;

    @Column(name = "data_source", length = 100)
    private String dataSource;

    @Column(name = "enrichment_status", length = 100)
    private String enrichmentStatus;

    @Column(name = "has_real_photo")
    private Boolean hasRealPhoto;

    @Column(name = "has_real_coordinates")
    private Boolean hasRealCoordinates;

    @Column(name = "has_opening_hours")
    private Boolean hasOpeningHours;

    @Column(name = "opening_hours_text", columnDefinition = "TEXT")
    private String openingHoursText;

    @Column(name = "open_now")
    private Boolean openNow;

    @Column(name = "business_status", length = 100)
    private String businessStatus;

    @Column(name = "next_open_close_text", columnDefinition = "TEXT")
    private String nextOpenCloseText;

    @Column(name = "scheduled_open_status", length = 100)
    private String scheduledOpenStatus;

    @Column(name = "place_data_reject_reason", columnDefinition = "TEXT")
    private String placeDataRejectReason;

    @Column(name = "provider_title", columnDefinition = "TEXT")
    private String providerTitle;

    @Column(name = "provider_id", columnDefinition = "TEXT")
    private String providerId;

    @Column(name = "provider_source", length = 100)
    private String providerSource;

    @Column(name = "system_stop")
    private Boolean systemStop;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
