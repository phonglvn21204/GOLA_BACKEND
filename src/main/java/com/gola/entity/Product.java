package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "stripe_product_id", nullable = false, unique = true) private String stripeProductId;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean isActive = true;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}