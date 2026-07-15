package com.gola.dto.map;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SavedPlaceResponse {
    private UUID id;
    private UUID userId;
    private UUID placeId;
    private String externalPlaceId;
    private String name;
    private String address;
    private String category;
    private Double latitude;
    private Double longitude;
    private String provider;
    private Instant createdAt;
}
