package com.gola.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties props;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .claim("type", "ACCESS")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + props.getAccessTokenExpiryMs()))
            .signWith(signingKey())
            .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "REFRESH")
            .id(UUID.randomUUID().toString())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + props.getRefreshTokenExpiryMs()))
            .signWith(signingKey())
            .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { extractClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }
}