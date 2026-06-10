package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.community.CreatePostRequest;
import com.gola.dto.community.PostResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.PostService;
import com.gola.service.PostReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Community post feed and tags")
public class PostController {
    private final PostService postService;
    private final PostReactionService postReactionService;

    @PostMapping
    @Operation(summary = "Create a new post")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody CreatePostRequest req) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Post created", postService.createPost(userId, req)));
    }

    @GetMapping("/feed")
    @Operation(summary = "Get paginated post feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeed(page, size)));
    }

    @GetMapping("/trip-stories")
    @Operation(summary = "Get paginated trip stories")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getTripStories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeed(page, size)));
    }

    @GetMapping("/hashtag/{tag}")
    @Operation(summary = "Get paginated posts by hashtag")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getPostsByHashtag(
            @PathVariable String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostsByHashtag(tag, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get post details")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a post")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable UUID id) {
        var userId = SecurityUtils.getCurrentUserId();
        postService.deletePost(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Post deleted successfully", null));
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Like a post")
    public ResponseEntity<ApiResponse<Void>> likePost(@PathVariable UUID id) {
        var req = new com.gola.dto.community.ReactionRequest();
        req.setKind(com.gola.entity.enums.ReactionKind.LIKE);
        postReactionService.reactToPost(id, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Post liked", null));
    }
}
