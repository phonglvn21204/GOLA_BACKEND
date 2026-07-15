package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.QuestProgressResponse;
import com.gola.dto.quest.QuestResponse;
import com.gola.dto.quest.SubmitProofRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.QuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/quests")
@RequiredArgsConstructor
@Tag(name = "Quests", description = "Quest tracking and gamification")
public class QuestController {
    private final QuestService questService;

    @GetMapping
    @Operation(summary = "Get all quests")
    public ResponseEntity<ApiResponse<List<QuestResponse>>> getAllQuests() {
        return ResponseEntity.ok(ApiResponse.ok(questService.getAllQuests()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get quest by ID")
    public ResponseEntity<ApiResponse<QuestResponse>> getQuestById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(questService.getQuestById(id)));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a quest for current user")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> startQuest(@PathVariable UUID id) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok("Quest started", questService.startQuest(id, userId)));
    }

    @PostMapping("/{id}/tasks/{taskIdx}/submit")
    @Operation(summary = "Submit proof for a quest task")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> submitProof(
            @PathVariable UUID id,
            @PathVariable Integer taskIdx,
            @RequestBody SubmitProofRequest req) {
        var userId = SecurityUtils.getCurrentUserId();
        var progress = questService.submitProof(id, taskIdx, userId, req.getMediaId(), req.getProofImageUrl(), req.getLat(), req.getLng());
        return ResponseEntity.ok(ApiResponse.ok("Proof submitted successfully", progress));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "GPS check-in for the current quest task")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> checkIn(
            @PathVariable UUID id,
            @RequestBody SubmitProofRequest req) {
        var userId = SecurityUtils.getCurrentUserId();
        var current = questService.getUserProgress(id, userId);
        var progress = questService.submitProof(id, current.getTaskIdx(), userId, req.getMediaId(), req.getProofImageUrl(), req.getLat(), req.getLng());
        return ResponseEntity.ok(ApiResponse.ok("Check-in submitted successfully", progress));
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "Get user progress for a quest")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> getUserProgress(@PathVariable UUID id) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(questService.getUserProgress(id, userId)));
    }

    @PostMapping("/progress/{progressId}/start")
    @Operation(summary = "Start a trip-scoped quest progress item")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> startProgress(@PathVariable UUID progressId) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok("Quest started", questService.startProgress(progressId, userId)));
    }

    @PostMapping("/progress/{progressId}/complete")
    @Operation(summary = "Complete a trip-scoped quest progress item")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> completeProgress(
            @PathVariable UUID progressId,
            @RequestBody SubmitProofRequest req) {
        var userId = SecurityUtils.getCurrentUserId();
        var progress = questService.completeProgress(
                progressId,
                userId,
                req.getMediaId(),
                req.getProofImageUrl(),
                req.getLat(),
                req.getLng()
        );
        return ResponseEntity.ok(ApiResponse.ok("Quest completed", progress));
    }
}
