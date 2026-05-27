package com.gola.dto.trip;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class AddStopRequest {
    private UUID placeId;
    @NotBlank private String name;
    private Instant arrivalAt;
    private Integer durationMin;
    private String notes;
    private Double orderIdx;
}