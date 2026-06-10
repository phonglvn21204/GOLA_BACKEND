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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile management")
public class ProfileController {

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

    @PostMapping("/me/avatar")
    @Operation(summary = "Update my avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(@RequestBody AvatarRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Avatar updated",
                profileService.updateAvatar(SecurityUtils.getCurrentUserId(), req.getAvatarUrl())));
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
}
