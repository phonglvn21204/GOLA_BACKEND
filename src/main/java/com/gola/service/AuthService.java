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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
    private final OtpService             otpService;
    private final ResetTokenService      resetTokenService;

    @Transactional
    public void register(RegisterRequest req) {
        if (profileRepo.existsByEmail(req.getEmail())) {
            throw GolaException.conflict("Email already registered");
        }
        var profile = Profile.builder()
                .id(UUID.randomUUID())
                .email(req.getEmail().toLowerCase())
                .displayName(req.getDisplayName())
                .locale(req.getLocale() != null ? req.getLocale() : "en")
                .passwordHash(encoder.encode(req.getPassword()))
                .build();
        var userRole = UserRole.builder().profile(profile).role(AppRole.USER).build();
        profile.getRoles().add(userRole);
        profileRepo.saveAndFlush(profile);
        walletRepo.save(Wallet.builder().userId(profile.getId()).golaCoins(0).build());
        otpService.generateAndSendOtp(profile.getEmail());
        log.info("New user registered: {}", profile.getEmail());
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        var profile = profileRepo.findByEmail(req.getEmail().toLowerCase())
            .orElseThrow(() -> GolaException.unauthorized("Email or password is incorrect"));
        if (profile.isDeleted()) throw GolaException.unauthorized("Account deactivated");
        ensureNotBlocked(profile);
        
        // Validate password if stored
        if (profile.getPasswordHash() != null && !encoder.matches(req.getPassword(), profile.getPasswordHash())) {
            throw GolaException.unauthorized("Email or password is incorrect");
        }

        if (!profile.isEmailVerified()) {
            throw GolaException.unauthorized("Please verify your email before login");
        }
        
        log.info("User logged in: {}", profile.getEmail());
        return buildAuthResponse(profile, resolveRoles(profile));
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
        ensureNotBlocked(profile);
        return buildAuthResponse(profile, resolveRoles(profile));
    }

    @Transactional
    public void logout(UUID userId) {
        tokenRepo.revokeAllByUserId(userId, Instant.now());
    }

    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().toLowerCase();
        
        // Check if email exists - don't reveal if email exists for security
        if (!profileRepo.existsByEmail(email)) {
            log.warn("Password reset requested for non-existent email: {}", req.getEmail());
            return;
        }
        
        // Generate and send OTP
        otpService.generateAndSendOtp(req.getEmail());
        log.info("OTP sent for password reset: {}", email);
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest req) {
        String email = req.getEmail().toLowerCase();
        
        // Check if email exists
        if (!profileRepo.existsByEmail(email)) {
            throw GolaException.unauthorized("Email not found");
        }
        
        // Verify OTP
        if (!otpService.verifyOtp(req.getEmail(), req.getOtp())) {
            throw GolaException.unauthorized("Invalid or expired OTP");
        }
        
        // Generate reset token
        String resetToken = resetTokenService.generateResetToken(email);
        
        return VerifyOtpResponse.builder()
            .resetToken(resetToken)
            .message("OTP verified successfully")
            .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        // Verify reset token FIRST (before any DB operations)
        String email = resetTokenService.verifyResetToken(req.getToken());
        if (email == null) {
            throw GolaException.unauthorized("Invalid or expired reset token");
        }
        
        // Validate password is not null or empty BEFORE DB operations
        if (req.getNewPassword() == null || req.getNewPassword().trim().isEmpty()) {
            throw GolaException.badRequest("Password cannot be empty");
        }
        
        // Find user
        var profile = profileRepo.findByEmail(email)
            .orElseThrow(() -> {
                // If user not found, still consume token to prevent reuse attempts
                resetTokenService.consumeResetToken(req.getToken());
                return GolaException.notFound("User");
            });
        
        try {
            // Encode and update password
            String encodedPassword = encoder.encode(req.getNewPassword());
            profile.setPasswordHash(encodedPassword);
            profileRepo.saveAndFlush(profile);
            
            // Revoke all refresh tokens for security
            tokenRepo.revokeAllByUserId(profile.getId(), Instant.now());
            
            log.info("Password reset completed for user: {}", email);
        } catch (Exception e) {
            log.error("Error during password reset for user {}: {}", email, e.getMessage(), e);
            throw GolaException.badRequest("Failed to reset password");
        } finally {
            // Consume reset token (remove it from circulation)
            resetTokenService.consumeResetToken(req.getToken());
        }
    }

    public AuthResponse buildAuthResponse(Profile profile, AppRole role) {
        return buildAuthResponse(profile, List.of(role));
    }

    public AuthResponse buildAuthResponse(Profile profile, List<AppRole> roles) {
        ensureNotBlocked(profile);
        List<AppRole> resolvedRoles = normalizeRoles(roles);
        AppRole primaryRole = primaryRole(resolvedRoles);
        tokenRepo.revokeAllByUserId(profile.getId(), Instant.now());
        String accessToken  = jwtService.generateAccessToken(profile.getId(), profile.getEmail(), primaryRole.name());
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
                .role(primaryRole.name())
                .roles(resolvedRoles.stream().map(AppRole::name).toList())
                .isAdmin(resolvedRoles.contains(AppRole.ADMIN))
                .emailVerified(profile.isEmailVerified())
                .build())
            .build();
    }

    public List<AppRole> resolveRoles(Profile profile) {
        return normalizeRoles(roleRepo.findByProfile_Id(profile.getId()).stream()
            .map(UserRole::getRole)
            .toList());
    }

    private List<AppRole> normalizeRoles(List<AppRole> roles) {
        List<AppRole> resolvedRoles = (roles == null ? Stream.<AppRole>empty() : roles.stream())
            .filter(role -> role != null)
            .distinct()
            .toList();
        return resolvedRoles.isEmpty() ? List.of(AppRole.USER) : resolvedRoles;
    }

    private AppRole primaryRole(List<AppRole> roles) {
        if (roles.contains(AppRole.ADMIN)) return AppRole.ADMIN;
        if (roles.contains(AppRole.MODERATOR)) return AppRole.MODERATOR;
        return roles.get(0);
    }

    private void ensureNotBlocked(Profile profile) {
        if (profile.isBlocked()) {
            throw GolaException.forbidden("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
    }

    private String hashToken(String raw) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest req) {

        if (!otpService.verifyOtp(req.getEmail(), req.getOtp())) {
            throw GolaException.badRequest("Invalid or expired OTP");
        }

        Profile profile = profileRepo.findByEmail(
                        req.getEmail().toLowerCase())
                .orElseThrow(() -> GolaException.notFound("User"));

        profile.setEmailVerifiedAt(Instant.now());

        profileRepo.save(profile);
    }
}
