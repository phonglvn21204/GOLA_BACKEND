package com.gola.dto.safety;
import com.gola.entity.enums.IncidentType;
import com.gola.entity.enums.IncidentSeverity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;
@Data public class IncidentRequest {
    @NotNull private IncidentType type;
    private String description;
    private Double latitude;
    private Double longitude;
    private IncidentSeverity severity;
    private UUID tripId;
    private UUID currentStopId;
    private UUID affectedStopId;
    private Integer estimatedDelayMinutes;
    private Boolean needsAiReroute;
    private String note;
    private String context;
    private String photoUrl;
}
