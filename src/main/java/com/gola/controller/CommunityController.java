package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.community.CommentResponse;
import com.gola.dto.community.CommunitySidebarResponse;
import com.gola.dto.community.CreatePostRequest;
import com.gola.dto.community.PostCommentRequest;
import com.gola.dto.community.PostResponse;
import com.gola.dto.community.PublicUserProfileResponse;
import com.gola.dto.community.ReactionRequest;
import com.gola.dto.safety.ReportRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.CommunitySidebarService;
import com.gola.service.CommentService;
import com.gola.service.FollowService;
import com.gola.service.PostReactionService;
import com.gola.service.PostService;
import com.gola.service.PublicUserProfileService;
import com.gola.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
@Tag(name = "Community", description = "Community feed, comments, reports, and follows")
public class CommunityController {

    private final PostService postService;
    private final CommentService commentService;
    private final PostReactionService reactionService;
    private final FollowService followService;
    private final ReportService reportService;
    private final CommunitySidebarService communitySidebarService;
    private final PublicUserProfileService publicUserProfileService;

    @GetMapping("/feed")
    @Operation(summary = "Get community feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeed(SecurityUtils.getCurrentUserId(), page, size)));
    }

    @GetMapping("/sidebar")
    @Operation(summary = "Get real community sidebar data")
    public ResponseEntity<ApiResponse<CommunitySidebarResponse>> sidebar() {
        return ResponseEntity.ok(ApiResponse.ok(communitySidebarService.getSidebar(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/users/{userId}/profile")
    @Operation(summary = "Get public-safe community user profile")
    public ResponseEntity<ApiResponse<PublicUserProfileResponse>> publicUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(publicUserProfileService.getProfile(SecurityUtils.getCurrentUserId(), userId)));
    }

    @PostMapping("/posts")
    @Operation(summary = "Create community post")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody CreatePostRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created", postService.createPost(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping("/posts/saved")
    @Operation(summary = "Get current user's saved community posts")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> savedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getSavedPosts(SecurityUtils.getCurrentUserId(), page, size)));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Get community post")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostById(postId, SecurityUtils.getCurrentUserId(), SecurityUtils.hasRole("ADMIN"))));
    }

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "Update community post")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Post updated",
                postService.updatePost(postId, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Delete community post")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable UUID postId) {
        postService.deletePost(postId, SecurityUtils.getCurrentUserId(), SecurityUtils.hasRole("ADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Post deleted", null));
    }

    @PostMapping("/posts/{postId}/save")
    @Operation(summary = "Save a community post")
    public ResponseEntity<ApiResponse<PostResponse>> savePost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok("Post saved", postService.savePost(postId, SecurityUtils.getCurrentUserId())));
    }

    @DeleteMapping("/posts/{postId}/save")
    @Operation(summary = "Unsave a community post")
    public ResponseEntity<ApiResponse<PostResponse>> unsavePost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok("Post unsaved", postService.unsavePost(postId, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/posts/{postId}/reactions")
    @Operation(summary = "Toggle current user's reaction on a post")
    public ResponseEntity<ApiResponse<Boolean>> toggleReaction(
            @PathVariable UUID postId,
            @RequestBody(required = false) ReactionRequest req) {
        boolean active = reactionService.toggleReaction(postId, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok(active ? "Reaction added" : "Reaction removed", active));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "List post comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> comments(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getCommentsForPost(postId, SecurityUtils.getCurrentUserId(), SecurityUtils.hasRole("ADMIN"))));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Add post comment")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody PostCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Comment added",
                commentService.addComment(postId, SecurityUtils.getCurrentUserId(), req)));
    }

    @PatchMapping("/comments/{commentId}")
    @Operation(summary = "Update a comment")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody PostCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Comment updated",
                commentService.updateComment(commentId, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete a comment")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID commentId) {
        commentService.deleteComment(commentId, SecurityUtils.getCurrentUserId(), SecurityUtils.hasRole("ADMIN"));
        return ResponseEntity.ok(ApiResponse.ok("Comment deleted", null));
    }

    @PostMapping("/comments/{commentId}/replies")
    @Operation(summary = "Reply to a comment")
    public ResponseEntity<ApiResponse<CommentResponse>> replyToComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody PostCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Reply added",
                commentService.addReply(commentId, SecurityUtils.getCurrentUserId(), req)));
    }

    @PostMapping("/comments/{commentId}/reactions")
    @Operation(summary = "Toggle current user's reaction on a comment")
    public ResponseEntity<ApiResponse<CommentResponse>> reactToComment(
            @PathVariable UUID commentId,
            @RequestBody(required = false) ReactionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Comment reaction updated",
                commentService.toggleReaction(commentId, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/comments/{commentId}/reactions")
    @Operation(summary = "Remove current user's reaction from a comment")
    public ResponseEntity<ApiResponse<CommentResponse>> removeCommentReaction(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok("Comment reaction removed",
                commentService.removeReaction(commentId, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/users/{userId}/follow")
    @Operation(summary = "Follow a user")
    public ResponseEntity<ApiResponse<Void>> follow(@PathVariable UUID userId) {
        followService.followUser(SecurityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiResponse.ok("User followed", null));
    }

    @DeleteMapping("/users/{userId}/follow")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable UUID userId) {
        followService.unfollowUser(SecurityUtils.getCurrentUserId(), userId);
        return ResponseEntity.ok(ApiResponse.ok("User unfollowed", null));
    }

    @PostMapping("/reports")
    @Operation(summary = "Report a community target")
    public ResponseEntity<ApiResponse<Void>> report(@Valid @RequestBody ReportRequest req) {
        reportService.submitReport(SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }

    @PostMapping("/posts/{postId}/reports")
    @Operation(summary = "Report a post")
    public ResponseEntity<ApiResponse<Void>> reportPost(
            @PathVariable UUID postId,
            @RequestBody ReportReasonRequest req) {
        ReportRequest report = new ReportRequest();
        report.setTargetType("POST");
        report.setTargetId(postId);
        report.setReason(req != null && req.reason() != null ? req.reason() : "Inappropriate post");
        reportService.submitReport(SecurityUtils.getCurrentUserId(), report);
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }

    @PostMapping("/comments/{commentId}/reports")
    @Operation(summary = "Report a comment")
    public ResponseEntity<ApiResponse<Void>> reportComment(
            @PathVariable UUID commentId,
            @RequestBody ReportReasonRequest req) {
        ReportRequest report = new ReportRequest();
        report.setTargetType("COMMENT");
        report.setTargetId(commentId);
        report.setReason(req != null && req.reason() != null ? req.reason() : "Inappropriate comment");
        reportService.submitReport(SecurityUtils.getCurrentUserId(), report);
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }

    public record ReportReasonRequest(String reason) {}
}
