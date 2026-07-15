package com.gola.dto.billing;
import lombok.Builder;
import lombok.Data;
@Data @Builder
public class PriceResponse {
    private String id;
    private String stripePriceId;
    private long amount;
    private String currency;
    private String intervalType;
    private Integer intervalCount;
}
