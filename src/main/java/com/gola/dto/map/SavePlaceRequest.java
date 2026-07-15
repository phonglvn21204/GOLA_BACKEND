package com.gola.dto.map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class SavePlaceRequest {
    private UUID placeId;
    private String externalPlaceId;
    @NotBlank
    private String name;
    private String address;
    private String category;
    private Double latitude;
    private Double longitude;
    private String provider;
}
