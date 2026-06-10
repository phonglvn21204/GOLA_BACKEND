package com.gola.dto.trip;
import lombok.Builder;
import lombok.Data;
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
    private String notes;
    private Instant completedAt;
    private Double lat;
    private Double lng;
    private String imageUrl;
}