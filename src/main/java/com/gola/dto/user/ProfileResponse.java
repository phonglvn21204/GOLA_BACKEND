package com.gola.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ProfileResponse {
    private UUID id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String locale;
    private String theme;
    private String homeCity;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private String phone;
    private boolean emailVerified;
    @JsonProperty("isPremium")
    private boolean isPremium;
    private List<String> roles;
    @JsonProperty("isAdmin")
    private boolean isAdmin;
    @JsonProperty("isBlocked")
    private boolean isBlocked;
    private Instant blockedAt;
    private String blockReason;
    private Double totalDistanceKm;
    private Long totalTrips;
    private Long totalStops;
    private Instant onboardedAt;
    private Instant createdAt;
}
