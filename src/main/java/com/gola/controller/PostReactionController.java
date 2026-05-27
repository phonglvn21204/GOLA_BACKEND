package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.community.ReactionRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.PostReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/posts/{postId}/reactions")
@RequiredArgsConstructor
@Tag(name = "Post Reactions", description = "Reactions to posts")
public class PostReactionController {

    private final PostReactionService reactionService;

    @PutMapping
    @Operation(summary = "React or update reaction on a post")
    public ResponseEntity<ApiResponse<Void>> react(
            @PathVariable UUID postId,
            @Valid @RequestBody ReactionRequest req) {
        reactionService.reactToPost(postId, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Reaction updated", null));
    }

    @DeleteMapping
    @Operation(summary = "Remove reaction from a post")
    public ResponseEntity<ApiResponse<Void>> removeReaction(@PathVariable UUID postId) {
        reactionService.removeReaction(postId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Reaction removed", null));
    }

    @GetMapping("/count")
    @Operation(summary = "Get reaction count for a post")
    public ResponseEntity<ApiResponse<Integer>> getCount(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok(reactionService.getReactionCount(postId)));
    }
}
