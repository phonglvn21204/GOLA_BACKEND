package com.gola.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email @NotBlank
    private String email;

    @NotBlank @Size(min = 8, max = 72)
    private String password;

    @NotBlank @Size(min = 2, max = 100)
    private String displayName;

    private String locale = "en";
    private String referralCode;
}