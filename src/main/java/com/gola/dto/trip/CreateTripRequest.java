package com.gola.dto.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateTripRequest {
    @NotBlank @Size(max = 255) private String title;
    private String origin;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
    private boolean isPublic = false;
}