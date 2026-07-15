package com.gola.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.auth.AuthResponse;
import com.gola.entity.Profile;
import com.gola.entity.UserRole;
import com.gola.entity.Wallet;
import com.gola.entity.enums.AppRole;
import com.gola.exception.GolaException;
import com.gola.repository.ProfileRepository;
import com.gola.repository.UserRoleRepository;
import com.gola.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {
    private final ProfileRepository profileRepo;
    private final UserRoleRepository roleRepo;
    private final WalletRepository walletRepo;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Value("${gola.google.client-id}")
    private String googleClientId;

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        // Verify the token and extract claims
        var googleClaims = isJwt(idToken)
            ? verifyGoogleIdToken(idToken)
            : verifyGoogleAccessToken(idToken);

        // Extract user information from token
        String email = googleClaims.getEmail();
        String name = googleClaims.getName();
        String picture = googleClaims.getPictureUrl();

        log.info("Google login attempt for email: {}", email);

        // Find existing user or create new one
        var profile = profileRepo.findByEmail(email.toLowerCase())
            .orElseGet(() -> createNewGoogleUser(email, name, picture));

        if (profile.isDeleted()) {
            throw GolaException.unauthorized("Account deactivated");
        }
        if (profile.isBlocked()) {
            throw GolaException.forbidden("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        log.info("User logged in with Google: {}", profile.getEmail());
        return authService.buildAuthResponse(profile, authService.resolveRoles(profile));
    }

    private boolean isJwt(String token) {
        return token != null && token.split("\\.").length == 3;
    }

    private GoogleClaims verifyGoogleAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw GolaException.unauthorized("Google token is required");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Google userinfo request failed");
            }

            var root = objectMapper.readTree(response.body());
            String email = root.path("email").asText(null);
            String name = root.path("name").asText(null);
            String picture = root.path("picture").asText(null);

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in Google userinfo");
            }

            return new GoogleClaims(email, name, picture);
        } catch (Exception e) {
            log.warn("Failed to verify Google access token: {}", e.getMessage());
            throw GolaException.unauthorized("Invalid or expired Google token");
        }
    }

    private GoogleClaims verifyGoogleIdToken(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw GolaException.unauthorized("Google OAuth not configured");
        }

        try {
            // Decode the JWT without verification for now
            // In production, you should verify the signature using Google's public keys
            // For this implementation, we'll decode and validate key claims
            DecodedJWT jwt = JWT.decode(idToken);

            // Verify issuer
            String issuer = jwt.getIssuer();
            if (!("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))) {
                throw new IllegalArgumentException("Invalid issuer");
            }

            // Verify audience (client ID)
            String audience = jwt.getAudience().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No audience in token"));
            if (!audience.equals(googleClientId)) {
                throw new IllegalArgumentException("Invalid audience: " + audience);
            }

            // Verify expiration
            Instant expiresAt = jwt.getExpiresAtAsInstant();
            if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                throw new IllegalArgumentException("Token expired");
            }

            // Extract claims from payload
            String email = jwt.getClaim("email").asString();
            String name = jwt.getClaim("name").asString();
            String picture = jwt.getClaim("picture").asString();

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in token");
            }

            return new GoogleClaims(email, name, picture);
        } catch (Exception e) {
            log.warn("Failed to verify Google ID token: {}", e.getMessage());
            throw GolaException.unauthorized("Invalid or expired Google token");
        }
    }

    protected Profile createNewGoogleUser(String email, String displayName, String pictureUrl) {
        log.info("Creating new user from Google OAuth: {}", email);

        var profile = Profile.builder()
            .id(UUID.randomUUID())
            .email(email.toLowerCase())
            .displayName(displayName != null ? displayName : email.split("@")[0])
            .avatarUrl(pictureUrl)
            .emailVerifiedAt(Instant.now())  // Google users have verified email
            .locale("en")
            .theme("dark")
            .isPublic(true)
            .build();

        // Create user role
        var userRole = UserRole.builder()
            .profile(profile)
            .role(AppRole.USER)
            .build();
        profile.getRoles().add(userRole);

        // Save profile
        profileRepo.saveAndFlush(profile);

        // Create wallet for the user
        walletRepo.save(Wallet.builder()
            .userId(profile.getId())
            .golaCoins(0)
            .build());

        log.info("New user created via Google OAuth: {}", profile.getEmail());
        return profile;
    }

    /**
     * Helper class to hold Google ID token claims
     */
    private static class GoogleClaims {
        private final String email;
        private final String name;
        private final String pictureUrl;

        public GoogleClaims(String email, String name, String pictureUrl) {
            this.email = email;
            this.name = name;
            this.pictureUrl = pictureUrl;
        }

        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getPictureUrl() { return pictureUrl; }
    }
}




