package com.gola.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gola.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpiryMs  = 900_000L;
    private long refreshTokenExpiryMs = 604_800_000L;
}