package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class LiveLocationResponse {
    private UUID userId;
    private UUID sessionId;
    private double lat;
    private double lng;
    private Double heading;
    private Double speed;
    private Double accuracy;
    private Instant ts;
}
