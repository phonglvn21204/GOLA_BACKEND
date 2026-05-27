package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "emergency_contacts")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmergencyContact {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String phone;
    private String relation;
    @Builder.Default private int priority = 1;
    @Column(name = "verified_at") private Instant verifiedAt;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
}