package com.gola.service;

import com.gola.dto.auth.*;
import com.gola.exception.GolaException;
import com.gola.config.GolaProperties;
import com.gola.security.JwtService;
import com.gola.entity.Profile;
import com.gola.entity.RefreshToken;
import com.gola.entity.UserRole;
import com.gola.entity.Wallet;
import com.gola.entity.enums.AppRole;
import com.gola.repository.ProfileRepository;
import com.gola.repository.RefreshTokenRepository;
import com.gola.repository.WalletRepository;
import com.gola.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final ProfileRepository     profileRepo;
    private final RefreshTokenRepository tokenRepo;
    private final WalletRepository       walletRepo;
    private final UserRoleRepository     roleRepo;
    private final PasswordEncoder        encoder;
    private final JwtService             jwtService;
    private final GolaProperties         props;
    private final RedisTemplate<String, Object> redis;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (profileRepo.existsByEmail(req.getEmail())) {
            throw GolaException.conflict("Email already registered");
        }
        var profile = Profile.builder()
            .id(UUID.randomUUID())
            .email(req.getEmail().toLowerCase())
            .displayName(req.getDisplayName())
            .locale(req.getLocale() != null ? req.getLocale() : "en")
            .build();
        var userRole = UserRole.builder().profile(profile).role(AppRole.USER).build();
        profile.getRoles().add(userRole);
        profileRepo.saveAndFlush(profile);
        walletRepo.save(Wallet.builder().userId(profile.getId()).golaCoins(0).build());
        log.info("New user registered: {}", profile.getEmail());
        return buildAuthResponse(profile, AppRole.USER);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        var profile = profileRepo.findByEmail(req.getEmail().toLowerCase())
            .orElseThrow(() -> GolaException.unauthorized("Email or password is incorrect"));
        if (profile.isDeleted()) throw GolaException.unauthorized("Account deactivated");
        var role = roleRepo.findByProfile_Id(profile.getId()).stream()
            .map(UserRole::getRole).findFirst().orElse(AppRole.USER);
        log.info("User logged in: {}", profile.getEmail());
        return buildAuthResponse(profile, role);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String tokenHash = hashToken(req.getRefreshToken());
        var stored = tokenRepo.findByTokenHash(tokenHash)
            .orElseThrow(() -> GolaException.unauthorized("Invalid refresh token"));
        if (!stored.isValid()) throw GolaException.unauthorized("Refresh token expired or revoked");
        stored.setRevokedAt(Instant.now());
        tokenRepo.save(stored);
        var profile = stored.getProfile();
        var role = roleRepo.findByProfile_Id(profile.getId()).stream()
            .map(UserRole::getRole).findFirst().orElse(AppRole.USER);
        return buildAuthResponse(profile, role);
    }

    @Transactional
    public void logout(UUID userId) {
        tokenRepo.revokeAllByUserId(userId, Instant.now());
    }

    public void forgotPassword(ForgotPasswordRequest req) {
        profileRepo.findByEmail(req.getEmail().toLowerCase()).ifPresent(profile -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set("pwd_reset:" + token, profile.getId().toString(), Duration.ofHours(1));
            log.info("Password reset requested for: {}", profile.getEmail());
            // TODO: send email via notification service
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String key   = "pwd_reset:" + req.getToken();
        Object value = redis.opsForValue().get(key);
        if (value == null) throw GolaException.badRequest("Invalid or expired reset token");
        UUID userId  = UUID.fromString(value.toString());
        var profile  = profileRepo.findById(userId).orElseThrow(() -> GolaException.notFound("User"));
        redis.delete(key);
        tokenRepo.revokeAllByUserId(userId, Instant.now());
        log.info("Password reset completed for user: {}", userId);
    }

    private AuthResponse buildAuthResponse(Profile profile, AppRole role) {
        tokenRepo.revokeAllByUserId(profile.getId(), Instant.now());
        String accessToken  = jwtService.generateAccessToken(profile.getId(), profile.getEmail(), role.name());
        String refreshToken = jwtService.generateRefreshToken(profile.getId());
        tokenRepo.save(RefreshToken.builder()
            .profile(profile)
            .tokenHash(hashToken(refreshToken))
            .expiresAt(Instant.now().plusMillis(props.getJwt().getRefreshTokenExpiryMs()))
            .build());
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(props.getJwt().getAccessTokenExpiryMs() / 1000)
            .user(AuthResponse.UserInfo.builder()
                .id(profile.getId().toString())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .role(role.name())
                .emailVerified(profile.isEmailVerified())
                .build())
            .build();
    }

    private String hashToken(String raw) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}