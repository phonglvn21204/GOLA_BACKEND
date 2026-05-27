package com.gola.dto.safety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReportRequest {
    @NotBlank
    private String targetType;

    @NotNull
    private UUID targetId;

    @NotBlank
    private String reason;
}
