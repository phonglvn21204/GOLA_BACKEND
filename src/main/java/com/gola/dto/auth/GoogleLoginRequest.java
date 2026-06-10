package com.gola.dto.auth;

import lombok.Data;

@Data
public class GoogleLoginRequest {
    private String idToken;
    private String accessToken;
}

