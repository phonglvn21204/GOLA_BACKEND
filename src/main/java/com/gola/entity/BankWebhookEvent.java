package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "bank_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "ux_bank_webhook_provider_reference", columnNames = {"provider", "external_reference"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankWebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String provider;
    @Column(name = "external_reference", nullable = false) private String externalReference;
    @Column(name = "order_id") private UUID orderId;
    private Long amount;
    @Column(nullable = false) private String status;
    @Column(columnDefinition = "TEXT") private String payload;
    @Column(name = "created_at", updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
