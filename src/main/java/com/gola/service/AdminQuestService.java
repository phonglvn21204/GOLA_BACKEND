package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.quest.AdminQuestRequest;
import com.gola.dto.quest.QuestProgressResponse;
import com.gola.dto.quest.QuestResponse;
import com.gola.entity.Quest;
import com.gola.entity.QuestProgress;
import com.gola.entity.enums.QuestDifficulty;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.entity.enums.QuestType;
import com.gola.exception.GolaException;
import com.gola.repository.QuestProgressRepository;
import com.gola.repository.QuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminQuestService {
    private final QuestRepository questRepo;
    private final QuestProgressRepository questProgressRepo;
    private final QuestService questService;

    public List<QuestResponse> listQuests() {
        return questRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(questService::mapToResponse)
            .toList();
    }

    @Transactional
    public QuestResponse createQuest(AdminQuestRequest req) {
        Quest quest = Quest.builder()
            .title(req.getTitle())
            .description(req.getDescription())
            .destination(clean(req.getDestination()))
            .type(req.getType() != null ? req.getType() : QuestType.GPS_CHECKIN)
            .targetName(clean(req.getTargetName()))
            .targetLat(req.getTargetLat())
            .targetLng(req.getTargetLng())
            .rewardCoins(resolveXp(req))
            .radiusMeters(req.getRadiusMeters())
            .status(resolveStatus(req))
            .difficulty(req.getDifficulty() != null ? req.getDifficulty() : QuestDifficulty.EASY)
            .iconKey(req.getIconKey())
            .badgeId(req.getBadgeId())
            .rewardId(req.getRewardId())
            .isActive(req.getActive() == null || req.getActive())
            .isFeatured(Boolean.TRUE.equals(req.getFeatured()))
            .expiresAt(req.getExpiresAt())
            .build();
        return questService.mapToResponse(questRepo.save(quest));
    }

    @Transactional
    public QuestResponse updateQuest(UUID id, AdminQuestRequest req) {
        Quest quest = questRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest"));
        quest.setTitle(req.getTitle());
        quest.setDescription(req.getDescription());
        quest.setDestination(clean(req.getDestination()));
        if (req.getType() != null) quest.setType(req.getType());
        quest.setTargetName(clean(req.getTargetName()));
        quest.setTargetLat(req.getTargetLat());
        quest.setTargetLng(req.getTargetLng());
        quest.setRewardCoins(resolveXp(req));
        quest.setRadiusMeters(req.getRadiusMeters());
        quest.setStatus(resolveStatus(req));
        quest.setDifficulty(req.getDifficulty() != null ? req.getDifficulty() : QuestDifficulty.EASY);
        quest.setIconKey(req.getIconKey());
        quest.setBadgeId(req.getBadgeId());
        quest.setRewardId(req.getRewardId());
        if (req.getActive() != null) quest.setActive(req.getActive());
        if (req.getFeatured() != null) quest.setFeatured(req.getFeatured());
        quest.setExpiresAt(req.getExpiresAt());
        return questService.mapToResponse(questRepo.save(quest));
    }

    @Transactional
    public QuestResponse toggleQuest(UUID id) {
        Quest quest = questRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest"));
        quest.setActive(!quest.isActive());
        quest.setStatus(quest.isActive() ? "ACTIVE" : "INACTIVE");
        return questService.mapToResponse(questRepo.save(quest));
    }

    @Transactional
    public QuestResponse activateQuest(UUID id) {
        Quest quest = questRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest"));
        quest.setActive(true);
        quest.setStatus("ACTIVE");
        return questService.mapToResponse(questRepo.save(quest));
    }

    @Transactional
    public QuestResponse deactivateQuest(UUID id) {
        Quest quest = questRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest"));
        quest.setActive(false);
        quest.setStatus("INACTIVE");
        return questService.mapToResponse(questRepo.save(quest));
    }

    @Transactional
    public void softDeleteQuest(UUID id) {
        Quest quest = questRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest"));
        quest.setActive(false);
        questRepo.save(quest);
    }

    public PageResponse<QuestProgressResponse> listProgress(QuestProgressStatus status, Pageable pageable) {
        var page = status == null
            ? questProgressRepo.findAllByOrderByCreatedAtDesc(pageable)
            : questProgressRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        return new PageResponse<>(page.map(questService::mapToProgressResponse));
    }

    @Transactional
    public QuestProgressResponse flagProgress(UUID id, UUID adminId, String reason) {
        QuestProgress progress = questProgressRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest progress"));
        progress.setStatus(QuestProgressStatus.FLAGGED);
        progress.setFlagReason(cleanReason(reason, "Flagged by admin"));
        progress.setAdminNote(progress.getFlagReason());
        progress.setReviewedAt(Instant.now());
        progress.setReviewedBy(adminId);
        return questService.mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse approveProgress(UUID id, UUID adminId) {
        QuestProgress progress = questProgressRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest progress"));
        if (progress.getUserId().equals(adminId)) {
            throw GolaException.badRequest("Admin cannot approve their own quest submission.");
        }
        Quest quest = questRepo.findById(progress.getQuestId()).orElseThrow(() -> GolaException.notFound("Quest"));
        progress.setReviewedAt(Instant.now());
        progress.setReviewedBy(adminId);
        questService.completeAndAward(progress, quest, adminId);
        return questService.mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse rejectProgress(UUID id, UUID adminId, String reason) {
        QuestProgress progress = questProgressRepo.findById(id).orElseThrow(() -> GolaException.notFound("Quest progress"));
        String cleanReason = cleanReason(reason, "Rejected by admin");
        progress.setStatus(QuestProgressStatus.REJECTED);
        progress.setFlagReason(cleanReason);
        progress.setRejectReason(cleanReason);
        progress.setAdminNote(cleanReason);
        progress.setRejectedAt(Instant.now());
        progress.setRejectedBy(adminId);
        progress.setReviewedAt(Instant.now());
        progress.setReviewedBy(adminId);
        return questService.mapToProgressResponse(questProgressRepo.save(progress));
    }

    private int resolveXp(AdminQuestRequest req) {
        if (req.getRewardPoints() != null) return Math.max(0, Math.min(1000, req.getRewardPoints()));
        if (req.getXpReward() != null) return Math.max(0, req.getXpReward());
        if (req.getRewardCoins() != null) return Math.max(0, req.getRewardCoins());
        return 0;
    }

    private String resolveStatus(AdminQuestRequest req) {
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            String status = req.getStatus().trim().toUpperCase(Locale.ROOT);
            if (status.equals("DRAFT") || status.equals("ACTIVE") || status.equals("INACTIVE")) {
                return status;
            }
        }
        return req.getActive() == null || req.getActive() ? "ACTIVE" : "INACTIVE";
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) return fallback;
        return reason.trim();
    }
}
