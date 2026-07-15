package com.gola.dto.safety;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAiSuggestionAction {
    private String type;
    private Integer minutes;
    private UUID fromStopId;
    private UUID stopId;
    private UUID oldStopId;
    private String newPlaceName;
    private String newName;
    private String newCategory;
    private String newArrivalAt;
    private String reason;
    private Double latitude;
    private Double longitude;
}
