package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.quest.QuestProgressResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.QuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/trips/{tripId}/quests")
@RequiredArgsConstructor
@Tag(name = "Trip Quests", description = "Trip-scoped quest generation and progress")
public class TripQuestController {
    private static final long QUEST_PROOF_MAX_BYTES = 12L * 1024 * 1024;

    private final QuestService questService;

    @GetMapping
    @Operation(summary = "List quest progress generated for a trip")
    public ResponseEntity<ApiResponse<List<QuestProgressResponse>>> listTripQuests(@PathVariable UUID tripId) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(questService.getTripQuests(tripId, userId)));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate deterministic quest progress for trip stops")
    public ResponseEntity<ApiResponse<List<QuestProgressResponse>>> generateTripQuests(@PathVariable UUID tripId) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok("Trip quests generated", questService.generateTripQuests(tripId, userId)));
    }

    @PostMapping(value = "/{questId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit GPS/photo proof for an admin-managed trip quest")
    public ResponseEntity<ApiResponse<QuestProgressResponse>> submitTripQuest(
            @PathVariable UUID tripId,
            @PathVariable UUID questId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("gpsLat") Double gpsLat,
            @RequestParam("gpsLng") Double gpsLng,
            @RequestParam(value = "note", required = false) String note) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String proofUrl = storeQuestProof(userId, photo);
        QuestProgressResponse progress = questService.submitTripQuest(
                tripId,
                questId,
                userId,
                null,
                proofUrl,
                gpsLat,
                gpsLng,
                note
        );
        return ResponseEntity.ok(ApiResponse.ok("Quest submitted for admin review", progress));
    }

    private String storeQuestProof(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw com.gola.exception.GolaException.badRequest("Photo proof is required.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "quest-proof.jpg" : file.getOriginalFilename());
        boolean supportedContentType = contentType.matches("image/(png|jpe?g|webp)");
        boolean supportedExtension = original.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$");
        if (!supportedContentType && !supportedExtension) {
            throw com.gola.exception.GolaException.badRequest("Only image proof files are allowed.");
        }
        if (file.getSize() > QUEST_PROOF_MAX_BYTES) {
            throw new com.gola.exception.GolaException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Ảnh minh chứng quá lớn. Vui lòng chọn ảnh nhỏ hơn 12MB."
            );
        }
        String ext = extensionFrom(original, contentType);
        String fileName = "quest-" + UUID.randomUUID() + ext;
        Path root = Path.of("uploads", "quests").toAbsolutePath().normalize();
        Path dir = root.resolve(userId.toString()).normalize();
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw com.gola.exception.GolaException.badRequest("Invalid upload path.");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw com.gola.exception.GolaException.badRequest("Could not store quest proof photo.");
        }
        return "/api/uploads/quests/" + userId + "/" + fileName;
    }

    private String extensionFrom(String fileName, String contentType) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(png|jpe?g|webp)")) return ext;
        }
        if (contentType.contains("jpeg")) return ".jpg";
        if (contentType.contains("webp")) return ".webp";
        return ".png";
    }
}
