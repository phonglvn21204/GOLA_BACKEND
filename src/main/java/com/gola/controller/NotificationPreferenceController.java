package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.entity.NotificationPreference;
import com.gola.entity.enums.NotificationChannel;
import com.gola.security.SecurityUtils;
import com.gola.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/me/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences", description = "User notification settings")
public class NotificationPreferenceController {

    private final NotificationPreferenceService prefService;

    @GetMapping
    @Operation(summary = "Get my notification preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreference>>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.ok(
                prefService.getPreferences(SecurityUtils.getCurrentUserId())));
    }

    @PutMapping("/{type}")
    @Operation(summary = "Set preference for a specific notification type")
    public ResponseEntity<ApiResponse<Void>> setPreference(
            @PathVariable String type,
            @RequestParam NotificationChannel channel,
            @RequestParam boolean isEnabled) {
        prefService.setPreference(SecurityUtils.getCurrentUserId(), type, channel, isEnabled);
        return ResponseEntity.ok(ApiResponse.ok("Preference updated", null));
    }
}
