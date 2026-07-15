package com.gola.dto.billing;
import com.gola.entity.enums.SubStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Data @Builder
public class PremiumStatusResponse {
    private boolean premium;
    private SubStatus subscriptionStatus;
    private UUID activeSubscriptionId;
    private String currentPlan;
    private String planName;
    private List<String> features;
    private Instant startDate;
    private Instant endDate;
    private Instant currentPeriodEnd;
    private long daysRemaining;
    private boolean expired;
}
