package com.gola.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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
        private List<String> roles;
        @JsonProperty("isAdmin")
        private boolean isAdmin;
        private boolean emailVerified;
    }
}
