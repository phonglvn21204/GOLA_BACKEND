package com.gola.dto.billing;
import com.gola.entity.enums.SubStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
@Data @Builder
public class SubscriptionResponse {
    private UUID id;
    private UUID productId;
    private UUID priceId;
    private String productName;
    private SubStatus status;
    private Instant startDate;
    private Instant endDate;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private long daysRemaining;
    private boolean expired;
    private boolean cancelAtPeriodEnd;
    private Instant cancelledAt;
    private Instant createdAt;
}
