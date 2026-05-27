package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "expenses",
       indexes = @Index(name = "idx_expense_trip", columnList = "trip_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(nullable = false)
    private String description;
}
