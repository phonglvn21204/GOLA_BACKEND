package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.community.CommentResponse;
import com.gola.dto.community.PostResponse;
import com.gola.entity.Report;
import com.gola.security.SecurityUtils;
import com.gola.service.CommentService;
import com.gola.service.PostService;
import com.gola.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/community")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Community", description = "Admin community moderation")
public class AdminCommunityController {

    private final PostService postService;
    private final CommentService commentService;
    private final ReportService reportService;

    @GetMapping("/posts")
    @Operation(summary = "List all community posts including hidden")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> posts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getAllPosts(SecurityUtils.getCurrentUserId(), page, size)));
    }

    @PatchMapping("/posts/{postId}/hide")
    @Operation(summary = "Hide a community post")
    public ResponseEntity<ApiResponse<PostResponse>> hidePost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok("Post hidden",
                postService.setHidden(postId, true, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/posts/{postId}/restore")
    @Operation(summary = "Restore a hidden community post")
    public ResponseEntity<ApiResponse<PostResponse>> restorePost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.ok("Post restored",
                postService.setHidden(postId, false, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/comments/{commentId}/hide")
    @Operation(summary = "Hide a community comment")
    public ResponseEntity<ApiResponse<CommentResponse>> hideComment(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok("Comment hidden",
                commentService.setHidden(commentId, true)));
    }

    @PatchMapping("/comments/{commentId}/restore")
    @Operation(summary = "Restore a hidden community comment")
    public ResponseEntity<ApiResponse<CommentResponse>> restoreComment(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok("Comment restored",
                commentService.setHidden(commentId, false)));
    }

    @GetMapping("/reports")
    @Operation(summary = "List community reports")
    public ResponseEntity<ApiResponse<PageResponse<Report>>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.listReports(status, PageRequest.of(page, size))));
    }

    @PatchMapping("/reports/{reportId}/resolve")
    @Operation(summary = "Resolve a community report")
    public ResponseEntity<ApiResponse<Report>> resolveReport(@PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.ok("Report resolved", reportService.updateStatus(reportId, "RESOLVED")));
    }

    @PatchMapping("/reports/{reportId}/reject")
    @Operation(summary = "Reject a community report")
    public ResponseEntity<ApiResponse<Report>> rejectReport(@PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.ok("Report rejected", reportService.updateStatus(reportId, "REJECTED")));
    }
}
