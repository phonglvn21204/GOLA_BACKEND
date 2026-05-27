package com.gola.entity;

import com.gola.entity.enums.SubStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "stripe_subscription_id", unique = true) private String stripeSubscriptionId;
    @Column(name = "stripe_customer_id") private String stripeCustomerId;
    @Column(name = "product_id") private UUID productId;
    @Column(name = "price_id") private UUID priceId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "sub_status") @Builder.Default private SubStatus status = SubStatus.INCOMPLETE;
    @Column(name = "current_period_start") private Instant currentPeriodStart;
    @Column(name = "current_period_end") private Instant currentPeriodEnd;
    @Column(name = "cancel_at_period_end", nullable = false) @Builder.Default private boolean cancelAtPeriodEnd = false;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at") @Builder.Default private Instant updatedAt = Instant.now();
}