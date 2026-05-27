package com.gola.dto.quest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RedemptionRequest {
    @NotNull
    private UUID rewardId;
}
