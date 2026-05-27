package com.gola.dto.safety;
import com.gola.entity.enums.IncidentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;
@Data public class IncidentRequest {
    @NotNull private IncidentType type;
    private String description;
    private Double latitude;
    private Double longitude;
    private UUID tripId;
}