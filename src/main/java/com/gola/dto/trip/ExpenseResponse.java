package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class ExpenseResponse {
    private UUID id;
    private UUID tripId;
    private UUID payerId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private Instant createdAt;
}
