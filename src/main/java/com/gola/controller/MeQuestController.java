package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.QuestProgressResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.QuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Current user quest progress")
public class MeQuestController {
    private final QuestService questService;

    @GetMapping("/quests/progress")
    @Operation(summary = "List current user's quest progress")
    public ResponseEntity<ApiResponse<List<QuestProgressResponse>>> myQuestProgress() {
        return ResponseEntity.ok(ApiResponse.ok(questService.getMyProgress(SecurityUtils.getCurrentUserId())));
    }
}
