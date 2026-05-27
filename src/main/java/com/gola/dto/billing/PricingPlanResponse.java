package com.gola.dto.billing;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;
@Data @Builder
public class PricingPlanResponse {
    private UUID id;
    private String stripeProductId;
    private String name;
    private String description;
    private List<PriceResponse> prices;
}