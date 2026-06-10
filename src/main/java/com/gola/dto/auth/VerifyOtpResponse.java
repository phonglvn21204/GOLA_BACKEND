package com.gola.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerifyOtpResponse {
    private String resetToken;
    private String message;
}

