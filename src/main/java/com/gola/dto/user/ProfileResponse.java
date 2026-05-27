package com.gola.dto.user;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
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
    private boolean isPublic;
    private String phone;
    private boolean emailVerified;
    private Instant onboardedAt;
    private Instant createdAt;
}
