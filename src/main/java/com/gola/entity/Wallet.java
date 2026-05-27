package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Wallet {
    @Id
    private UUID userId;

    @Column(name = "gola_coins", nullable = false)
    private int golaCoins = 0;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addCoins(int amount)    { this.golaCoins += amount; }
    public void deductCoins(int amount) {
        if (this.golaCoins < amount) throw new IllegalStateException("Insufficient coins");
        this.golaCoins -= amount;
    }
}