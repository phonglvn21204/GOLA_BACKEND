package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "places")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Place {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "google_place_id", unique = true) private String googlePlaceId;
    @Column(nullable = false) private String name;
    private String category;
    private String address;
    private String city;
    private String country;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") private String[] photos;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "opening_hours") private Map<String, Object> openingHours;
    @Column(columnDefinition = "numeric(3,2)")
    private BigDecimal rating;
    @Column(name = "refreshed_at") private Instant refreshedAt;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
}