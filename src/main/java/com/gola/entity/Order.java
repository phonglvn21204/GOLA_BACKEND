package com.gola.entity;

import com.gola.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "stripe_session_id", unique = true) private String stripeSessionId;
    @Column(name = "stripe_payment_intent") private String stripePaymentIntent;
    private Long amount;
    @Column(nullable = false) @Builder.Default private String currency = "usd";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_status") @Builder.Default private PaymentStatus status = PaymentStatus.PENDING;
    @Column(name = "price_id") private UUID priceId;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at") @Builder.Default private Instant updatedAt = Instant.now();
}