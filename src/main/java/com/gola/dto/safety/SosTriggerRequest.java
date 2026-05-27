package com.gola.dto.safety;
import lombok.Data;
import java.util.UUID;
@Data public class SosTriggerRequest {
    private Double latitude;
    private Double longitude;
    private UUID tripId;
    private String clientToken;
}