package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.community.CommentResponse;
import com.gola.dto.community.PostCommentRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Post comments")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "Add a comment to a post")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody PostCommentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Comment added",
                commentService.addComment(postId, SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping
    @Operation(summary = "Get comments for a post")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getCommentsForPost(postId)));
    }
}
