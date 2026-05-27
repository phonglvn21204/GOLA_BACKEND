package com.gola.service;

import com.gola.dto.quest.QuestProgressResponse;
import com.gola.dto.quest.QuestResponse;
import com.gola.entity.*;
import com.gola.entity.enums.*;
import com.gola.exception.GolaException;
import com.gola.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestService {
    private final QuestRepository         questRepo;
    private final QuestTaskRepository     questTaskRepo;
    private final QuestProgressRepository questProgressRepo;
    private final WalletRepository        walletRepo;
    private final UserBadgeRepository     userBadgeRepo;

    public List<QuestResponse> getAllQuests() {
        return questRepo.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public QuestResponse getQuestById(UUID id) {
        var quest = questRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Quest"));
        return mapToResponse(quest);
    }

    @Transactional
    public QuestProgressResponse startQuest(UUID questId, UUID userId) {
        questRepo.findById(questId)
            .orElseThrow(() -> GolaException.notFound("Quest"));

        var existingOpt = questProgressRepo.findByQuestIdAndUserId(questId, userId);
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            if (existing.getStatus() == QuestProgressStatus.COMPLETED) {
                throw GolaException.conflict("Quest already completed");
            }
            return mapToProgressResponse(existing);
        }

        var progress = QuestProgress.builder()
            .questId(questId)
            .userId(userId)
            .taskIdx(0)
            .status(QuestProgressStatus.IN_PROGRESS)
            .build();

        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse submitProof(UUID questId, Integer taskIdx, UUID userId, String mediaId, Double lat, Double lng) {
        var quest = questRepo.findById(questId)
            .orElseThrow(() -> GolaException.notFound("Quest"));

        var progress = questProgressRepo.findByQuestIdAndUserId(questId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress. Please start the quest first."));

        if (progress.getStatus() == QuestProgressStatus.COMPLETED) {
            throw GolaException.badRequest("Quest already completed.");
        }

        if (progress.getTaskIdx() != taskIdx) {
            throw GolaException.badRequest("Invalid task index. Current task index is " + progress.getTaskIdx());
        }

        var task = questTaskRepo.findByQuestIdAndIdx(questId, taskIdx)
            .orElseThrow(() -> GolaException.notFound("Quest task"));

        // Proximity checks if ProofType is CHECKIN
        if (task.getProofType() == ProofType.CHECKIN) {
            if (lat == null || lng == null) {
                throw GolaException.badRequest("Coordinates required for GPS check-in.");
            }
            double radius = task.getRadiusM() != null ? task.getRadiusM() : 100.0;
            boolean inside = questTaskRepo.isWithinRadius(questId, taskIdx, lat, lng, radius);
            if (!inside) {
                throw GolaException.badRequest("GPS verification failed. You are outside the required radius.");
            }
        }

        if (mediaId != null && !mediaId.isBlank()) {
            try {
                progress.setProofMediaId(UUID.fromString(mediaId));
            } catch (IllegalArgumentException e) {
                throw GolaException.badRequest("Invalid media ID format.");
            }
        }

        List<QuestTask> tasks = questTaskRepo.findByQuestIdOrderByIdxAsc(questId);
        if (progress.getTaskIdx() >= tasks.size() - 1) {
            // Quest completed
            progress.setStatus(QuestProgressStatus.COMPLETED);
            progress.setVerifiedAt(Instant.now());

            // Reward coins
            if (quest.getRewardCoins() > 0) {
                var wallet = walletRepo.findByUserIdForUpdate(userId)
                    .orElseGet(() -> walletRepo.save(Wallet.builder().userId(userId).golaCoins(0).build()));
                wallet.addCoins(quest.getRewardCoins());
                walletRepo.save(wallet);
                log.info("Rewarded {} coins to user {} for completing quest {}", quest.getRewardCoins(), userId, questId);
            }

            // Reward badge
            if (quest.getBadgeId() != null) {
                if (!userBadgeRepo.existsByUserIdAndBadgeId(userId, quest.getBadgeId())) {
                    userBadgeRepo.save(UserBadge.builder()
                        .userId(userId)
                        .badgeId(quest.getBadgeId())
                        .sourceQuestId(questId)
                        .earnedAt(Instant.now())
                        .build());
                    log.info("Rewarded badge {} to user {} for completing quest {}", quest.getBadgeId(), userId, questId);
                }
            }
        } else {
            progress.setTaskIdx(progress.getTaskIdx() + 1);
        }

        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    public QuestProgressResponse getUserProgress(UUID questId, UUID userId) {
        var progress = questProgressRepo.findByQuestIdAndUserId(questId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress"));
        return mapToProgressResponse(progress);
    }

    private QuestResponse mapToResponse(Quest q) {
        return QuestResponse.builder()
            .id(q.getId())
            .type(q.getType())
            .title(q.getTitle())
            .description(q.getDescription())
            .rewardCoins(q.getRewardCoins())
            .badgeId(q.getBadgeId())
            .isFeatured(q.isFeatured())
            .isActive(q.isActive())
            .expiresAt(q.getExpiresAt())
            .createdAt(q.getCreatedAt())
            .build();
    }

    private QuestProgressResponse mapToProgressResponse(QuestProgress qp) {
        return QuestProgressResponse.builder()
            .questId(qp.getQuestId())
            .userId(qp.getUserId())
            .taskIdx(qp.getTaskIdx())
            .status(qp.getStatus())
            .build();
    }
}
