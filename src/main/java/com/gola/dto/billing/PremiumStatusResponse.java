package com.gola.dto.billing;
import com.gola.entity.enums.SubStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
@Data @Builder
public class PremiumStatusResponse {
    private boolean premium;
    private SubStatus subscriptionStatus;
    private UUID activeSubscriptionId;
    private String planName;
    private Instant currentPeriodEnd;
}