package com.gola.dto.safety;
import com.gola.entity.enums.SosStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
@Data @Builder public class SosResponse {
    private UUID id;
    private UUID userId;
    private UUID tripId;
    private Double latitude;
    private Double longitude;
    private SosStatus status;
    private Instant createdAt;
}