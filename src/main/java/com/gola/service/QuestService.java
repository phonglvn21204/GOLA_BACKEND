package com.gola.service;

import com.gola.dto.quest.BadgeResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestService {
    private static final double DEFAULT_RADIUS_METERS = 150.0;

    private final QuestRepository         questRepo;
    private final QuestTaskRepository     questTaskRepo;
    private final QuestProgressRepository questProgressRepo;
    private final WalletRepository        walletRepo;
    private final UserBadgeRepository     userBadgeRepo;
    private final BadgeRepository         badgeRepo;
    private final TripRepository          tripRepo;
    private final TripStopRepository      tripStopRepo;
    private final TripMemberRepository    tripMemberRepo;
    private final ProfileRepository       profileRepo;
    private final NotificationService     notificationService;

    public List<QuestResponse> getAllQuests() {
        return questRepo.findAllByOrderByCreatedAtDesc().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public QuestResponse getQuestById(UUID id) {
        var quest = questRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Quest"));
        return mapToResponse(quest);
    }

    public List<QuestProgressResponse> getTripQuests(UUID tripId, UUID userId) {
        Trip trip = requireTripAccess(tripId, userId);
        ensureAdminManagedTripQuests(trip, userId);
        return questProgressRepo.findByTripIdAndUserIdOrderByCreatedAtAsc(tripId, userId).stream()
            .map(this::mapToProgressResponse)
            .toList();
    }

    @Transactional
    public List<QuestProgressResponse> generateTripQuests(UUID tripId, UUID userId) {
        Trip trip = requireTripAccess(tripId, userId);
        ensureAdminManagedTripQuests(trip, userId);
        return getTripQuests(tripId, userId);
    }

    @Transactional
    public QuestProgressResponse startProgress(UUID progressId, UUID userId) {
        var progress = questProgressRepo.findByIdAndUserId(progressId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress"));
        if (progress.getTripId() != null) {
            requireTripAccess(progress.getTripId(), userId);
        }
        if (progress.getStatus() == QuestProgressStatus.APPROVED || progress.getStatus() == QuestProgressStatus.COMPLETED || progress.getStatus() == QuestProgressStatus.SUBMITTED) {
            return mapToProgressResponse(progress);
        }
        if (progress.getStatus() == QuestProgressStatus.LOCKED) {
            throw GolaException.badRequest("Quest is locked.");
        }
        progress.setStatus(QuestProgressStatus.IN_PROGRESS);
        progress.setStartedAt(Instant.now());
        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse completeProgress(
            UUID progressId,
            UUID userId,
            String mediaId,
            String proofImageUrl,
            Double lat,
            Double lng
    ) {
        var progress = questProgressRepo.findByIdAndUserId(progressId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress"));
        if (progress.getTripId() != null) {
            requireTripAccess(progress.getTripId(), userId);
        }
        if (progress.getStatus() == QuestProgressStatus.APPROVED || progress.getStatus() == QuestProgressStatus.COMPLETED) {
            return mapToProgressResponse(progress);
        }

        Quest quest = questRepo.findById(progress.getQuestId())
            .orElseThrow(() -> GolaException.notFound("Quest"));
        TripStop stop = progress.getTripStopId() == null
            ? null
            : tripStopRepo.findById(progress.getTripStopId()).orElse(null);

        if (requiresPhoto(quest) && (proofImageUrl == null || proofImageUrl.isBlank()) && (mediaId == null || mediaId.isBlank())) {
            throw GolaException.badRequest("Photo proof is required for this quest.");
        }

        double targetLat = resolveTargetLat(quest, stop);
        double targetLng = resolveTargetLng(quest, stop);
        if (requiresGps(quest) && (!hasValidCoordinate(targetLat, targetLng))) {
            throw GolaException.badRequest("Quest target coordinates are missing.");
        }

        if (requiresGps(quest)) {
            if (lat == null || lng == null) {
                throw GolaException.badRequest("Current location is required to submit this quest.");
            }
            double radius = resolveRadiusMeters(quest);
            double distance = haversineMeters(lat, lng, targetLat, targetLng);
            progress.setCompletedLatitude(lat);
            progress.setCompletedLongitude(lng);
            progress.setDistanceMetersFromTarget(distance);
            progress.setGpsValid(distance <= radius);
        }

        applyProof(progress, mediaId, proofImageUrl);
        progress.setStatus(QuestProgressStatus.SUBMITTED);
        progress.setSubmittedAt(Instant.now());
        progress.setCompletedAt(null);
        progress.setVerifiedAt(null);
        progress.setVerifiedBy(null);
        progress.setReviewedAt(null);
        progress.setReviewedBy(null);
        progress.setRejectedAt(null);
        progress.setRejectedBy(null);
        progress.setRejectReason(null);
        progress.setFlagReason(null);
        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse submitTripQuest(
            UUID tripId,
            UUID questId,
            UUID userId,
            String mediaId,
            String proofImageUrl,
            Double lat,
            Double lng,
            String note
    ) {
        Trip trip = requireTripAccess(tripId, userId);
        Quest quest = questRepo.findById(questId).orElseThrow(() -> GolaException.notFound("Quest"));
        if (!quest.isActive() || !"ACTIVE".equalsIgnoreCase(quest.getStatus())) {
            throw GolaException.badRequest("Quest is not active.");
        }
        if (!matchesTripDestination(quest, trip)) {
            throw GolaException.forbidden();
        }

        QuestProgress progress = questProgressRepo.findByQuestIdAndUserIdAndTripId(questId, userId, tripId)
            .orElseGet(() -> questProgressRepo.save(QuestProgress.builder()
                .questId(questId)
                .userId(userId)
                .tripId(tripId)
                .taskIdx(0)
                .status(QuestProgressStatus.ASSIGNED)
                .startedAt(Instant.now())
                .build()));
        progress.setNote(clean(note));
        QuestProgressResponse response = completeProgress(progress.getId(), userId, mediaId, proofImageUrl, lat, lng);
        QuestProgress saved = questProgressRepo.findById(progress.getId()).orElseThrow(() -> GolaException.notFound("Quest progress"));
        saved.setNote(clean(note));
        return mapToProgressResponse(questProgressRepo.save(saved));
    }

    @Transactional
    public QuestProgressResponse startQuest(UUID questId, UUID userId) {
        questRepo.findById(questId)
            .orElseThrow(() -> GolaException.notFound("Quest"));

        var existingOpt = questProgressRepo.findByQuestIdAndUserId(questId, userId);
        if (existingOpt.isPresent()) {
            var existing = existingOpt.get();
            if (existing.getStatus() == QuestProgressStatus.APPROVED || existing.getStatus() == QuestProgressStatus.COMPLETED) {
                throw GolaException.conflict("Quest already completed");
            }
            return mapToProgressResponse(existing);
        }

        var progress = QuestProgress.builder()
            .questId(questId)
            .userId(userId)
            .taskIdx(0)
            .status(QuestProgressStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .build();

        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    @Transactional
    public QuestProgressResponse submitProof(UUID questId, Integer taskIdx, UUID userId, String mediaId, String proofImageUrl, Double lat, Double lng) {
        var quest = questRepo.findById(questId)
            .orElseThrow(() -> GolaException.notFound("Quest"));

        var progress = questProgressRepo.findByQuestIdAndUserId(questId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress. Please start the quest first."));

        if (progress.getStatus() == QuestProgressStatus.APPROVED || progress.getStatus() == QuestProgressStatus.COMPLETED) {
            throw GolaException.badRequest("Quest already completed.");
        }

        if (progress.getTaskIdx() != taskIdx) {
            throw GolaException.badRequest("Invalid task index. Current task index is " + progress.getTaskIdx());
        }

        var task = questTaskRepo.findByQuestIdAndIdx(questId, taskIdx)
            .orElseThrow(() -> GolaException.notFound("Quest task"));

        if (task.getProofType() == ProofType.CHECKIN || task.getProofType() == ProofType.GPS) {
            if (lat == null || lng == null) {
                throw GolaException.badRequest("Coordinates required for GPS check-in.");
            }
            double radius = task.getRadiusM() != null ? task.getRadiusM() : 100.0;
            boolean inside = questTaskRepo.isWithinRadius(questId, taskIdx, lat, lng, radius);
            progress.setCompletedLatitude(lat);
            progress.setCompletedLongitude(lng);
            progress.setGpsValid(inside);
        }

        applyProof(progress, mediaId, proofImageUrl);
        progress.setStatus(QuestProgressStatus.SUBMITTED);
        progress.setSubmittedAt(Instant.now());

        return mapToProgressResponse(questProgressRepo.save(progress));
    }

    public QuestProgressResponse getUserProgress(UUID questId, UUID userId) {
        var progress = questProgressRepo.findByQuestIdAndUserId(questId, userId)
            .orElseThrow(() -> GolaException.notFound("Quest progress"));
        return mapToProgressResponse(progress);
    }

    public List<QuestProgressResponse> getMyProgress(UUID userId) {
        return questProgressRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::mapToProgressResponse)
            .toList();
    }

    public List<BadgeResponse> getMyBadges(UUID userId) {
        return userBadgeRepo.findByUserId(userId).stream()
            .map(UserBadge::getBadgeId)
            .filter(Objects::nonNull)
            .map(badgeRepo::findById)
            .flatMap(java.util.Optional::stream)
            .map(this::mapBadge)
            .toList();
    }

    public QuestResponse mapToResponse(Quest q) {
        return QuestResponse.builder()
            .id(q.getId())
            .type(q.getType())
            .title(q.getTitle())
            .description(q.getDescription())
            .destination(q.getDestination())
            .targetName(q.getTargetName())
            .targetLat(q.getTargetLat())
            .targetLng(q.getTargetLng())
            .rewardCoins(q.getRewardCoins())
            .xpReward(q.getRewardCoins())
            .rewardPoints(q.getRewardCoins())
            .radiusMeters(resolveRadiusMeters(q))
            .status(q.getStatus())
            .difficulty(q.getDifficulty())
            .iconKey(q.getIconKey())
            .badgeId(q.getBadgeId())
            .rewardId(q.getRewardId())
            .isFeatured(q.isFeatured())
            .isActive(q.isActive())
            .expiresAt(q.getExpiresAt())
            .createdAt(q.getCreatedAt())
            .updatedAt(q.getUpdatedAt())
            .build();
    }

    public QuestProgressResponse mapToProgressResponse(QuestProgress qp) {
        Quest quest = questRepo.findById(qp.getQuestId()).orElse(null);
        TripStop stop = qp.getTripStopId() == null ? null : tripStopRepo.findById(qp.getTripStopId()).orElse(null);
        return mapToProgressResponse(qp, quest, stop);
    }

    public QuestProgressResponse mapToProgressResponse(QuestProgress qp, Quest quest, TripStop stop) {
        double radius = quest != null ? resolveRadiusMeters(quest) : DEFAULT_RADIUS_METERS;
        Profile user = qp.getUserId() == null ? null : profileRepo.findActiveById(qp.getUserId()).orElse(null);
        String userEmail = user != null ? clean(user.getEmail()) : null;
        String userDisplayName = user != null ? clean(user.getDisplayName()) : null;
        if (userDisplayName == null && userEmail != null) {
            int at = userEmail.indexOf('@');
            userDisplayName = at > 0 ? userEmail.substring(0, at) : userEmail;
        }
        if (userDisplayName == null) {
            userDisplayName = "Người dùng";
        }
        return QuestProgressResponse.builder()
            .id(qp.getId())
            .questId(qp.getQuestId())
            .userId(qp.getUserId())
            .userDisplayName(userDisplayName)
            .userEmail(userEmail)
            .userAvatarUrl(user != null ? user.getAvatarUrl() : null)
            .tripId(qp.getTripId())
            .tripStopId(qp.getTripStopId())
            .taskIdx(qp.getTaskIdx())
            .status(qp.getStatus())
            .questTitle(quest != null ? quest.getTitle() : null)
            .questDescription(quest != null ? quest.getDescription() : null)
            .questDestination(quest != null ? quest.getDestination() : null)
            .targetName(quest != null && quest.getTargetName() != null ? quest.getTargetName() : (stop != null ? stop.getName() : null))
            .type(quest != null ? quest.getType() : null)
            .rewardCoins(quest != null ? quest.getRewardCoins() : 0)
            .xpReward(quest != null ? quest.getRewardCoins() : 0)
            .rewardPointsAwarded(qp.getRewardPointsAwarded())
            .difficulty(quest != null ? quest.getDifficulty() : null)
            .iconKey(quest != null ? quest.getIconKey() : null)
            .radiusMeters(radius)
            .tripStopName(stop != null ? stop.getName() : null)
            .tripStopCategory(stop != null ? stop.getCategory() : null)
            .targetLat(quest != null && quest.getTargetLat() != null ? quest.getTargetLat() : (stop != null ? stop.getLat() : null))
            .targetLng(quest != null && quest.getTargetLng() != null ? quest.getTargetLng() : (stop != null ? stop.getLng() : null))
            .proofImageUrl(qp.getProofImageUrl())
            .mediaId(qp.getProofMediaId())
            .completedLat(qp.getCompletedLatitude())
            .completedLng(qp.getCompletedLongitude())
            .distanceMetersFromTarget(qp.getDistanceMetersFromTarget())
            .gpsValid(qp.getGpsValid())
            .xpAwarded(qp.isXpAwarded())
            .flagReason(qp.getFlagReason())
            .rejectReason(qp.getRejectReason())
            .note(qp.getNote())
            .adminNote(qp.getAdminNote())
            .startedAt(qp.getStartedAt())
            .submittedAt(qp.getSubmittedAt())
            .completedAt(qp.getCompletedAt())
            .verifiedAt(qp.getVerifiedAt())
            .approvedAt(qp.getApprovedAt())
            .approvedBy(qp.getApprovedBy())
            .rejectedAt(qp.getRejectedAt())
            .rejectedBy(qp.getRejectedBy())
            .createdAt(qp.getCreatedAt())
            .build();
    }

    @Transactional
    public void completeAndAward(QuestProgress progress, Quest quest, UUID reviewerId) {
        progress.setStatus(QuestProgressStatus.APPROVED);
        progress.setCompletedAt(progress.getCompletedAt() != null ? progress.getCompletedAt() : Instant.now());
        progress.setVerifiedAt(Instant.now());
        progress.setVerifiedBy(reviewerId);
        progress.setApprovedAt(Instant.now());
        progress.setApprovedBy(reviewerId);
        progress.setReviewedAt(Instant.now());
        progress.setReviewedBy(reviewerId);
        progress.setRejectReason(null);
        progress.setFlagReason(null);

        if (progress.isXpAwarded()) {
            return;
        }

        if (quest.getRewardCoins() > 0) {
            var wallet = walletRepo.findByUserIdForUpdate(progress.getUserId())
                .orElseGet(() -> walletRepo.save(Wallet.builder().userId(progress.getUserId()).golaCoins(0).build()));
            wallet.addCoins(quest.getRewardCoins());
            walletRepo.save(wallet);
            log.info("Rewarded {} coins to user {} for completing quest {}", quest.getRewardCoins(), progress.getUserId(), quest.getId());
        }

        if (quest.getBadgeId() != null && !userBadgeRepo.existsByUserIdAndBadgeId(progress.getUserId(), quest.getBadgeId())) {
            userBadgeRepo.save(UserBadge.builder()
                .userId(progress.getUserId())
                .badgeId(quest.getBadgeId())
                .sourceQuestId(quest.getId())
                .earnedAt(Instant.now())
                .build());
            log.info("Rewarded badge {} to user {} for completing quest {}", quest.getBadgeId(), progress.getUserId(), quest.getId());
        }

        progress.setXpAwarded(true);
        progress.setRewardPointsAwarded(Math.max(0, quest.getRewardCoins()));
        try {
            notificationService.notifyQuest(
                    progress.getUserId(),
                    "QUEST_COMPLETED",
                    "Hoàn thành nhiệm vụ",
                    "Bạn đã hoàn thành nhiệm vụ " + quest.getTitle() + ".",
                    quest.getId(),
                    "/quest"
            );
            if (quest.getRewardCoins() > 0 || quest.getRewardId() != null || quest.getBadgeId() != null) {
                notificationService.notifyQuest(
                        progress.getUserId(),
                        "REWARD_AVAILABLE",
                        "Phần thưởng mới",
                        "Bạn có phần thưởng mới có thể nhận.",
                        quest.getId(),
                        "/rewards"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create quest notification quest={} user={}: {}", quest.getId(), progress.getUserId(), e.getMessage());
        }
    }

    private Trip requireTripAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId)
            .orElseThrow(() -> GolaException.notFound("Trip"));
        if (trip.getOwnerId().equals(userId) || tripMemberRepo.existsByTripIdAndUserId(tripId, userId)) {
            return trip;
        }
        throw GolaException.forbidden();
    }

    private void ensureAdminManagedTripQuests(Trip trip, UUID userId) {
        List<Quest> activeQuests = questRepo.findByIsActiveTrueAndStatusIgnoreCaseOrderByCreatedAtDesc("ACTIVE");
        for (Quest quest : activeQuests) {
            if (!matchesTripDestination(quest, trip)) {
                continue;
            }
            questProgressRepo.findByQuestIdAndUserIdAndTripId(quest.getId(), userId, trip.getId())
                .orElseGet(() -> questProgressRepo.save(QuestProgress.builder()
                    .questId(quest.getId())
                    .userId(userId)
                    .tripId(trip.getId())
                    .taskIdx(0)
                    .status(QuestProgressStatus.ASSIGNED)
                    .startedAt(Instant.now())
                    .build()));
        }
    }

    private boolean matchesTripDestination(Quest quest, Trip trip) {
        if (quest.getDestination() == null || quest.getDestination().isBlank()) {
            return true;
        }
        String questDestination = normalize(quest.getDestination());
        String tripDestination = normalize(trip.getDestination());
        if (questDestination.isBlank() || tripDestination.isBlank()) {
            return false;
        }
        return tripDestination.contains(questDestination) || questDestination.contains(tripDestination);
    }

    private boolean requiresPhoto(Quest quest) {
        return quest.getType() == QuestType.GPS_PHOTO
            || quest.getType() == QuestType.PHOTO_ONLY
            || quest.getType() == QuestType.PHOTO_CHECKIN;
    }

    private boolean requiresGps(Quest quest) {
        return quest.getType() == QuestType.GPS_PHOTO
            || quest.getType() == QuestType.GPS_CHECKIN;
    }

    private double resolveTargetLat(Quest quest, TripStop stop) {
        if (quest.getTargetLat() != null) return quest.getTargetLat();
        return stop != null && stop.getLat() != null ? stop.getLat() : Double.NaN;
    }

    private double resolveTargetLng(Quest quest, TripStop stop) {
        if (quest.getTargetLng() != null) return quest.getTargetLng();
        return stop != null && stop.getLng() != null ? stop.getLng() : Double.NaN;
    }

    private boolean hasValidCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat != 0.0 && lng != 0.0;
    }

    private Quest findOrCreateTemplate(QuestType type) {
        return questRepo.findByTypeAndIsActiveTrueOrderByCreatedAtDesc(type).stream()
            .findFirst()
            .orElseGet(() -> questRepo.save(defaultTemplate(type)));
    }

    private Quest defaultTemplate(QuestType type) {
        return Quest.builder()
            .type(type)
            .title(defaultTitle(type))
            .description("System default quest template for trip stops.")
            .rewardCoins(defaultXp(type))
            .radiusMeters(defaultRadius(type))
            .difficulty(defaultDifficulty(type))
            .iconKey(defaultIcon(type))
            .isActive(true)
            .isFeatured(false)
            .build();
    }

    private String defaultTitle(QuestType type) {
        return switch (type) {
            case GPS_PHOTO -> "GPS photo submission";
            case PHOTO_ONLY -> "Photo submission";
            case PHOTO_CHECKIN -> "Photo check-in";
            case GPS_CHECKIN -> "GPS check-in";
            case FOOD -> "Local food discovery";
            case TEAM -> "Team stop challenge";
            case SAFETY -> "Safety check";
            case REWARD -> "Reward stop";
            case MINI_CHALLENGE -> "Mini challenge";
            case COMMUNITY -> "Community quest";
            case SOLO -> "Solo quest";
        };
    }

    private int defaultXp(QuestType type) {
        return switch (type) {
            case GPS_PHOTO -> 25;
            case PHOTO_ONLY -> 20;
            case TEAM -> 35;
            case PHOTO_CHECKIN, FOOD -> 30;
            case SAFETY -> 15;
            default -> 20;
        };
    }

    private double defaultRadius(QuestType type) {
        return switch (type) {
            case GPS_CHECKIN, GPS_PHOTO -> 100.0;
            case SAFETY -> 250.0;
            default -> DEFAULT_RADIUS_METERS;
        };
    }

    private QuestDifficulty defaultDifficulty(QuestType type) {
        return switch (type) {
            case TEAM, MINI_CHALLENGE -> QuestDifficulty.MEDIUM;
            default -> QuestDifficulty.EASY;
        };
    }

    private String defaultIcon(QuestType type) {
        return switch (type) {
            case GPS_PHOTO -> "map-pin-camera";
            case PHOTO_ONLY, PHOTO_CHECKIN -> "camera";
            case GPS_CHECKIN -> "map-pin";
            case FOOD -> "utensils";
            case TEAM -> "users";
            case SAFETY -> "shield";
            case REWARD -> "gift";
            case MINI_CHALLENGE -> "sparkles";
            case COMMUNITY -> "message-circle";
            case SOLO -> "user";
        };
    }

    private QuestType inferQuestType(TripStop stop) {
        String text = normalize(stop.getCategory()) + " " + normalize(stop.getName());
        if (containsAny(text, "food", "restaurant", "meal", "lunch", "dinner", "breakfast", "cafe", "coffee", "an ", "quan", "nha hang", "ca phe")) {
            return QuestType.FOOD;
        }
        if (containsAny(text, "market", "shop", "shopping", "souvenir", "cho", "mua", "dac san")) {
            return QuestType.REWARD;
        }
        if (containsAny(text, "team", "group", "nhom")) {
            return QuestType.TEAM;
        }
        if (containsAny(text, "safety", "hospital", "police", "emergency", "safe", "an toan")) {
            return QuestType.SAFETY;
        }
        if (containsAny(text, "photo", "view", "beach", "museum", "landmark", "bien", "song", "nui", "ho", "den", "chua")) {
            return QuestType.PHOTO_CHECKIN;
        }
        return QuestType.GPS_CHECKIN;
    }

    private boolean isTransportStop(TripStop stop) {
        String text = normalize(stop.getCategory()) + " " + normalize(stop.getName());
        return containsAny(text,
            "transport", "travel", "route", "drive", "bus", "train", "flight", "airport",
            "depart", "departure", "return", "di chuyen", "khoi hanh", "ve lai", "san bay", "ben xe");
    }

    private boolean hasUsableCoordinate(TripStop stop) {
        return stop.getLat() != null && stop.getLng() != null
            && Double.isFinite(stop.getLat()) && Double.isFinite(stop.getLng())
            && stop.getLat() != 0.0 && stop.getLng() != 0.0;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyProof(QuestProgress progress, String mediaId, String proofImageUrl) {
        if (mediaId != null && !mediaId.isBlank()) {
            try {
                progress.setProofMediaId(UUID.fromString(mediaId));
            } catch (IllegalArgumentException e) {
                throw GolaException.badRequest("Invalid media ID format.");
            }
        }
        if (proofImageUrl != null && !proofImageUrl.isBlank()) {
            progress.setProofImageUrl(proofImageUrl.trim());
        }
    }

    private double resolveRadiusMeters(Quest quest) {
        if (quest.getRadiusMeters() != null && quest.getRadiusMeters() > 0) {
            return quest.getRadiusMeters();
        }
        return DEFAULT_RADIUS_METERS;
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private BadgeResponse mapBadge(Badge badge) {
        return BadgeResponse.builder()
            .id(badge.getId())
            .name(badge.getName())
            .iconUrl(badge.getIconUrl())
            .criteria(badge.getCriteria())
            .isActive(badge.isActive())
            .build();
    }
}
