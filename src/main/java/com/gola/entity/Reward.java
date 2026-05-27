package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "rewards")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reward extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "cost_coins", nullable = false)
    private int costCoins;

    @Column(nullable = false)
    @Builder.Default
    private int stock = -1; // -1 = unlimited

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
