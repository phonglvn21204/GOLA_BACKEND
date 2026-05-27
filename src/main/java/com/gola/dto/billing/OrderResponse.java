package com.gola.dto.billing;
import com.gola.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
@Data @Builder
public class OrderResponse {
    private UUID id;
    private UUID priceId;
    private Long amount;
    private String currency;
    private PaymentStatus status;
    private String stripeSessionId;
    private Instant createdAt;
    private Instant updatedAt;
}