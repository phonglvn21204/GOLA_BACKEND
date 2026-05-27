package com.gola.dto.billing;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;
@Data @Builder
public class PriceResponse {
    private UUID id;
    private String stripePriceId;
    private long amount;
    private String currency;
    private String intervalType;
    private Integer intervalCount;
}