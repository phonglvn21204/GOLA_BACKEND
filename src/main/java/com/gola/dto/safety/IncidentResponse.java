package com.gola.dto.safety;

import com.gola.entity.enums.IncidentType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponse {
    private UUID id;
    private UUID userId;
    private UUID tripId;
    private IncidentType type;
    private String description;
    private Double latitude;
    private Double longitude;
    private String status;
    private String[] mediaUrls;
    private Instant createdAt;
}
