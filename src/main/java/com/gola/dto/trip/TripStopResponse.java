package com.gola.dto.trip;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class TripStopResponse {
    private UUID id;
    private UUID placeId;
    private double orderIdx;
    private String name;
    private Instant arrivalAt;
    private Integer durationMin;
    private BigDecimal estimatedCost;
    private String category;
    private String notes;
    private Instant completedAt;
    private Double lat;
    private Double lng;
    private String imageUrl;
    private BigDecimal rating;
    private Integer reviewCount;
    private String imageSource;
    private String placeAddress;
    private String dataSource;
    private String enrichmentStatus;
    private Boolean hasRealPhoto;
    private Boolean hasRealCoordinates;
    private Boolean hasOpeningHours;
    private String openingHoursText;
    private Boolean openNow;
    private String businessStatus;
    private String nextOpenCloseText;
    private String scheduledOpenStatus;
    private String placeDataRejectReason;
    private String providerTitle;
    private String providerId;
    private String providerSource;
    private Boolean systemStop;
}
