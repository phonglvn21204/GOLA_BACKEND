package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.quest.AdminQuestProgressReviewRequest;
import com.gola.dto.quest.AdminQuestRequest;
import com.gola.dto.quest.QuestProgressResponse;
import com.gola.dto.quest.QuestResponse;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.security.SecurityUtils;
import com.gola.service.AdminQuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Quests", description = "Quest template and progress moderation")
public class AdminQuestController {
    private final AdminQuestService adminQuestService;

    @GetMapping("/admin/quests")
    @Operation(summary = "List all quest templates")
    public ResponseEntity<ApiResponse<List<QuestResponse>>> listQuests() {
        return ResponseEntity.ok(ApiResponse.ok(adminQuestService.listQuests()));
    }

    @PostMapping("/admin/quests")
    @Operation(summary = "Create quest template")
    public ResponseEntity<ApiResponse<QuestResponse>> createQuest(@Valid @RequestBody AdminQuestRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest created", adminQuestService.createQuest(req)));
    }

    @PutMapping("/admin/quests/{id}")
    @Operation(summary = "Update quest template")
    public ResponseEntity<ApiResponse<QuestResponse>> updateQuest(@PathVariable UUID id, @Valid @RequestBody AdminQuestRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest updated", adminQuestService.updateQuest(id, req)));
    }

    @PatchMapping("/admin/quests/{id}")
    @Operation(summary = "Patch quest template")
    public ResponseEntity<ApiResponse<QuestResponse>> patchQuest(@PathVariable UUID id, @Valid @RequestBody AdminQuestRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest updated", adminQuestService.updateQuest(id, req)));
    }

    @PatchMapping("/admin/quests/{id}/toggle")
    @Operation(summary = "Toggle quest active state")
    public ResponseEntity<ApiResponse<QuestResponse>> toggleQuest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Quest toggled", adminQuestService.toggleQuest(id)));
    }

    @PatchMapping("/admin/quests/{id}/activate")
    @Operation(summary = "Activate quest template")
    public ResponseEntity<ApiResponse<QuestResponse>> activateQuest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Quest activated", adminQuestService.activateQuest(id)));
    }

    @PatchMapping("/admin/quests/{id}/deactivate")
    @Operation(summary = "Deactivate quest template")
    public ResponseEntity<ApiResponse<QuestResponse>> deactivateQuest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Quest deactivated", adminQuestService.deactivateQuest(id)));
    }

    @DeleteMapping("/admin/quests/{id}")
    @Operation(summary = "Soft delete quest template by making it inactive")
    public ResponseEntity<ApiResponse<Void>> deleteQuest(@PathVariable UUID id) {
        adminQuestService.softDeleteQuest(id);
        return ResponseEntity.ok(ApiResponse.ok("Quest deactivated", null));
    }

    @GetMapping("/admin/quest-progress")
    @Operation(summary = "List quest progress for moderation")
    public ResponseEntity<ApiResponse<PageResponse<QuestProgressResponse>>> listProgress(
            @RequestParam(required = false) QuestProgressStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminQuestService.listProgress(status, PageRequest.of(page, size))));
    }

    @GetMapping("/admin/quest-submissions")
    @Operation(summary = "List quest submissions for moderation")
    public ResponseEntity<ApiResponse<PageResponse<QuestProgressResponse>>> listSubmissions(
            @RequestParam(required = false) QuestProgressStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        QuestProgressStatus effectiveStatus = status == null ? QuestProgressStatus.SUBMITTED : status;
        return ResponseEntity.ok(ApiResponse.ok(adminQuestService.listProgress(effectiveStatus, PageRequest.of(page, size))));
    }

    @PatchMapping("/admin/quest-progress/{id}/flag")
    @Operation(summary = "Flag quest progress for review")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> flagProgress(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminQuestProgressReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest progress flagged",
            adminQuestService.flagProgress(id, SecurityUtils.getCurrentUserId(), req != null ? req.getReason() : null)));
    }

    @PatchMapping("/admin/quest-progress/{id}/approve")
    @Operation(summary = "Approve quest progress and award XP if needed")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> approveProgress(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Quest progress approved",
            adminQuestService.approveProgress(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/admin/quest-submissions/{id}/approve")
    @Operation(summary = "Approve quest submission and award points")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> approveSubmission(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Quest submission approved",
            adminQuestService.approveProgress(id, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/admin/quest-progress/{id}/reject")
    @Operation(summary = "Reject quest progress")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> rejectProgress(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminQuestProgressReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest progress rejected",
            adminQuestService.rejectProgress(id, SecurityUtils.getCurrentUserId(), req != null ? req.getReason() : null)));
    }

    @PatchMapping("/admin/quest-submissions/{id}/reject")
    @Operation(summary = "Reject quest submission")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> rejectSubmission(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminQuestProgressReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quest submission rejected",
            adminQuestService.rejectProgress(id, SecurityUtils.getCurrentUserId(), req != null ? req.getReason() : null)));
    }
}
