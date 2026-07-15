package com.gola.service;

import com.gola.dto.community.PostResponse;
import com.gola.dto.community.PublicUserProfileResponse;
import com.gola.entity.Badge;
import com.gola.entity.Profile;
import com.gola.entity.UserBadge;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.exception.GolaException;
import com.gola.repository.BadgeRepository;
import com.gola.repository.FollowRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import com.gola.repository.QuestProgressRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicUserProfileService {
    private final ProfileRepository profileRepo;
    private final FollowRepository followRepo;
    private final PostRepository postRepo;
    private final PostService postService;
    private final TripRepository tripRepo;
    private final QuestProgressRepository questProgressRepo;
    private final UserBadgeRepository userBadgeRepo;
    private final BadgeRepository badgeRepo;

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getProfile(UUID viewerId, UUID targetUserId) {
        Profile profile = profileRepo.findActiveById(targetUserId)
            .orElseThrow(() -> GolaException.notFound("Profile"));

        boolean isCurrentUser = targetUserId.equals(viewerId);
        boolean isPrivate = !profile.isPublic() && !isCurrentUser;
        boolean followed = !isCurrentUser && followRepo.existsByFollowerIdAndFolloweeId(viewerId, targetUserId);
        long followerCount = followRepo.countByFolloweeId(targetUserId);
        long followingCount = followRepo.countByFollowerId(targetUserId);

        if (isPrivate) {
            return PublicUserProfileResponse.builder()
                .userId(profile.getId())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .isPublic(false)
                .isPrivate(true)
                .isCurrentUser(false)
                .followedByCurrentUser(followed)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .badges(Collections.emptyList())
                .recentPosts(Collections.emptyList())
                .build();
        }

        List<PublicUserProfileResponse.PublicBadge> badges = publicBadges(targetUserId);
        List<PublicUserProfileResponse.RecentPost> recentPosts = postRepo
            .findTop3ByAuthorIdAndIsHiddenFalseOrderByCreatedAtDesc(targetUserId)
            .stream()
            .map(post -> toRecentPost(postService.mapToResponse(post, viewerId)))
            .toList();

        return PublicUserProfileResponse.builder()
            .userId(profile.getId())
            .displayName(profile.getDisplayName())
            .avatarUrl(profile.getAvatarUrl())
            .homeCity(profile.getHomeCity())
            .bio(profile.getBio())
            .isPublic(profile.isPublic())
            .isPrivate(false)
            .isCurrentUser(isCurrentUser)
            .followedByCurrentUser(followed)
            .followerCount(followerCount)
            .followingCount(followingCount)
            .postCount(postRepo.countByAuthorIdAndIsHiddenFalse(targetUserId))
            .tripMemoryCount(tripRepo.countCompletedForUser(targetUserId))
            .completedQuestCount(questProgressRepo.countByUserIdAndStatus(targetUserId, QuestProgressStatus.COMPLETED))
            .questPoints(questProgressRepo.sumCompletedQuestCoinsForUser(targetUserId))
            .badgeCount(userBadgeRepo.countByUserId(targetUserId))
            .badges(badges)
            .recentPosts(recentPosts)
            .build();
    }

    private List<PublicUserProfileResponse.PublicBadge> publicBadges(UUID userId) {
        List<UserBadge> userBadges = userBadgeRepo.findTop6ByUserIdOrderByEarnedAtDesc(userId);
        if (userBadges.isEmpty()) return List.of();

        Map<UUID, Badge> badgesById = badgeRepo.findAllById(
                userBadges.stream().map(UserBadge::getBadgeId).toList()
            )
            .stream()
            .collect(Collectors.toMap(Badge::getId, Function.identity()));

        return userBadges.stream()
            .map(userBadge -> {
                Badge badge = badgesById.get(userBadge.getBadgeId());
                if (badge == null) return null;
                return PublicUserProfileResponse.PublicBadge.builder()
                    .id(badge.getId())
                    .name(badge.getName())
                    .iconUrl(badge.getIconUrl())
                    .earnedAt(userBadge.getEarnedAt())
                    .build();
            })
            .filter(item -> item != null)
            .toList();
    }

    private PublicUserProfileResponse.RecentPost toRecentPost(PostResponse post) {
        return PublicUserProfileResponse.RecentPost.builder()
            .id(post.getId())
            .body(post.getBody())
            .thumbnailUrls(post.getThumbnailUrls())
            .mediumUrls(post.getMediumUrls())
            .mediaUrls(post.getMediaUrls())
            .commentCount(post.getCommentCount())
            .totalReactionCount(post.getTotalReactionCount())
            .createdAt(post.getCreatedAt())
            .build();
    }
}
