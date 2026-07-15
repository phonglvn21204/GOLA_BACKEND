package com.gola.dto.safety;

import com.gola.entity.enums.IncidentSeverity;
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
    private UUID currentStopId;
    private UUID affectedStopId;
    private Integer estimatedDelayMinutes;
    private Boolean needsAiReroute;
    private String context;
    private IncidentSeverity severity;
    private Integer verifiedCount;
    private String status;
    private String aiSummary;
    private String aiSuggestionJson;
    private String photoUrl;
    private String[] mediaUrls;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
}
