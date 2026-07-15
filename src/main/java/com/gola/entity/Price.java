package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Price {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "stripe_price_id", nullable = false, unique = true) private String stripePriceId;
    @Column(nullable = false) private long amount;
    @Column(nullable = false) @Builder.Default private String currency = "vnd";
    @Column(name = "interval_type") private String intervalType;
    @Column(name = "interval_count") @Builder.Default private Integer intervalCount = 1;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean isActive = true;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
