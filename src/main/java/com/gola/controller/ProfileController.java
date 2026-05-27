package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.user.ProfileResponse;
import com.gola.dto.user.UpdateProfileRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user's public profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.getProfile(id, SecurityUtils.getCurrentUserId())));
    }
}
