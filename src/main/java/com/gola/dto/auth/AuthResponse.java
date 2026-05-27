package com.gola.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long   expiresIn;
    private UserInfo user;

    @Data @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String displayName;
        private String avatarUrl;
        private String role;
        private boolean emailVerified;
    }
}