package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users/{id}/follow")
@RequiredArgsConstructor
@Tag(name = "Follows", description = "Follow and unfollow users")
public class FollowController {

    private final FollowService followService;

    @PostMapping
    @Operation(summary = "Follow a user")
    public ResponseEntity<ApiResponse<Void>> follow(@PathVariable UUID id) {
        followService.followUser(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Successfully followed user", null));
    }

    @DeleteMapping
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable UUID id) {
        followService.unfollowUser(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Successfully unfollowed user", null));
    }
}
