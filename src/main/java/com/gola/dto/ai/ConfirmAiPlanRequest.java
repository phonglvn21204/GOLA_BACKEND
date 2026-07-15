package com.gola.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ConfirmAiPlanRequest {
    @Valid
    @NotNull
    private GenerateTripRequest tripRequest;

    @NotNull
    private Map<String, Object> plan;

    private Integer selectedPlanIndex;
}
