package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.user.ProfileResponse;
import com.gola.dto.user.UpdateProfileRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile management")
public class ProfileController {
    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

    private final ProfileService profileService;

    @GetMapping("/me")
    @Operation(summary = "Get my own profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.getMyProfile(SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update my profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                profileService.updateProfile(SecurityUtils.getCurrentUserId(), req)));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update my avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(@RequestBody AvatarRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Avatar updated",
                profileService.updateAvatar(SecurityUtils.getCurrentUserId(), req.getAvatarUrl())));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and update my avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String avatarUrl = storeAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.ok("Avatar updated",
                profileService.updateAvatar(userId, avatarUrl)));
    }

    @GetMapping("/me/onboarding")
    @Operation(summary = "Get onboarding status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOnboarding() {
        ProfileResponse profile = profileService.getMyProfile(SecurityUtils.getCurrentUserId());
        Map<String, Object> body = new HashMap<>();
        body.put("completed", profile.getOnboardedAt() != null);
        body.put("onboardedAt", profile.getOnboardedAt());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PatchMapping("/me/onboarding")
    @Operation(summary = "Update onboarding status")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateOnboarding(@RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Onboarding updated",
                profileService.completeOnboarding(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user's public profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.getProfile(id, SecurityUtils.getCurrentUserId())));
    }

    @Data
    public static class AvatarRequest {
        private String avatarUrl;
    }

    private String storeAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw com.gola.exception.GolaException.badRequest("No avatar file uploaded");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "avatar.jpg" : file.getOriginalFilename());
        if (!contentType.startsWith("image/") || !isSupportedImage(original, contentType)) {
            throw com.gola.exception.GolaException.badRequest("Only image files are allowed");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new com.gola.exception.GolaException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Ảnh đại diện quá lớn. Vui lòng chọn ảnh nhỏ hơn 5MB."
            );
        }

        String ext = extensionFrom(original, contentType);
        String fileName = UUID.randomUUID() + ext;
        Path root = Path.of("uploads", "avatars").toAbsolutePath().normalize();
        Path dir = root.resolve(userId.toString()).normalize();
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw com.gola.exception.GolaException.badRequest("Invalid upload path");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw com.gola.exception.GolaException.badRequest("Could not store avatar image");
        }
        return "/api/uploads/avatars/" + userId + "/" + fileName;
    }

    private boolean isSupportedImage(String fileName, String contentType) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return contentType.equals("image/jpeg") || contentType.equals("image/jpg") || contentType.equals("image/png") || contentType.equals("image/webp")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String extensionFrom(String fileName, String contentType) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        if (lower.endsWith(".jpg")) return ".jpg";
        if (contentType.equals("image/png")) return ".png";
        if (contentType.equals("image/webp")) return ".webp";
        return ".jpg";
    }
}
