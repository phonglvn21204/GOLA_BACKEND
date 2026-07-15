package com.gola.service;

import com.gola.dto.community.CommunitySidebarResponse;
import com.gola.entity.Profile;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.repository.PostHashtagRepository;
import com.gola.repository.FollowRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import com.gola.repository.QuestProgressRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import com.gola.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunitySidebarService {
    private final TripRepository tripRepo;
    private final TripStopRepository tripStopRepo;
    private final QuestProgressRepository questProgressRepo;
    private final UserBadgeRepository userBadgeRepo;
    private final ProfileRepository profileRepo;
    private final PostHashtagRepository postHashtagRepo;
    private final FollowRepository followRepo;
    private final PostRepository postRepo;

    @Transactional(readOnly = true)
    public CommunitySidebarResponse getSidebar(UUID userId) {
        var summary = CommunitySidebarResponse.CurrentUserSummary.builder()
            .tripsCompleted(safeCount("completed trips", () -> tripRepo.countCompletedForUser(userId)))
            .stopsVisited(safeCount("completed stops", () -> tripStopRepo.countCompletedStopsForUser(userId)))
            .questsCompleted(safeCount("completed quests", () -> questProgressRepo.countByUserIdAndStatus(userId, QuestProgressStatus.COMPLETED)))
            .badgesEarned(safeCount("badges", () -> userBadgeRepo.countByUserId(userId)))
            .build();

        List<CommunitySidebarResponse.SuggestedTraveler> suggested = profileRepo
            .findSuggestedTravelers(userId, PageRequest.of(0, 5))
            .stream()
            .map(profile -> toSuggestedTraveler(profile, userId))
            .toList();

        List<CommunitySidebarResponse.TrendingHashtag> trending = postHashtagRepo
            .findTrendingTags(PageRequest.of(0, 8))
            .stream()
            .map(row -> CommunitySidebarResponse.TrendingHashtag.builder()
                .tag((String) row[0])
                .postCount(((Number) row[1]).longValue())
                .build())
            .toList();

        return CommunitySidebarResponse.builder()
            .currentUserSummary(summary)
            .suggestedTravelers(suggested)
            .trendingHashtags(trending)
            .build();
    }

    private CommunitySidebarResponse.SuggestedTraveler toSuggestedTraveler(Profile profile, UUID viewerId) {
        long postCount = safeCount("suggested traveler posts", () -> postRepo.countByAuthorIdAndIsHiddenFalse(profile.getId()));
        return CommunitySidebarResponse.SuggestedTraveler.builder()
            .userId(profile.getId())
            .displayName(profile.getDisplayName())
            .avatarUrl(profile.getAvatarUrl())
            .homeCity(profile.getHomeCity())
            .followerCount(safeCount("suggested traveler followers", () -> followRepo.countByFolloweeId(profile.getId())))
            .followedByCurrentUser(followRepo.existsByFollowerIdAndFolloweeId(viewerId, profile.getId()))
            .reason(postCount > 0 ? "Hay chia sẻ kỷ niệm" : "Du khách GOLA")
            .build();
    }

    private long safeCount(String metric, LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Exception ex) {
            log.warn("Community sidebar metric '{}' failed; returning 0. reason={}", metric, ex.getMessage());
            return 0L;
        }
    }
}
