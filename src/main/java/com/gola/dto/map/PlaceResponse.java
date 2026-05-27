package com.gola.dto.map;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceResponse {
    private UUID id;
    private String googlePlaceId;
    private String name;
    private String category;
    private String address;
    private String city;
    private String country;
    private String[] photos;
    private Map<String, Object> openingHours;
    private Double rating;
    private Instant refreshedAt;
    private Instant createdAt;
}
