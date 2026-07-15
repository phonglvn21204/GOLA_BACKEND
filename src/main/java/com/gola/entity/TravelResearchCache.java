package com.gola.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "travel_research_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelResearchCache extends BaseEntity {
    @Column(name = "cache_key", nullable = false, unique = true, length = 160)
    private String cacheKey;

    @Column(nullable = false)
    private String destination;

    @Column(name = "travel_month", nullable = false)
    private Integer month;

    @Column(columnDefinition = "text")
    private String query;

    @Column(name = "context_json", nullable = false, columnDefinition = "text")
    private String contextJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
