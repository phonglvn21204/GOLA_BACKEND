package com.gola.dto.billing;
import lombok.Builder;
import lombok.Data;
import java.util.List;
@Data @Builder
public class PricingPlanResponse {
    private String id;
    private String stripeProductId;
    private String slug;
    private String name;
    private String description;
    private String billingType;
    private String badge;
    private boolean popular;
    private boolean bestValue;
    private List<String> features;
    private List<PriceResponse> prices;
}
