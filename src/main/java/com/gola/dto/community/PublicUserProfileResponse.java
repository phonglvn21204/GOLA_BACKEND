package com.gola.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserProfileResponse {
    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String homeCity;
    private String bio;
    private boolean isPublic;
    private boolean isPrivate;
    private boolean followedByCurrentUser;
    private boolean isCurrentUser;
    private long followerCount;
    private long followingCount;
    private long postCount;
    private long tripMemoryCount;
    private long completedQuestCount;
    private long questPoints;
    private long badgeCount;
    private List<PublicBadge> badges;
    private List<RecentPost> recentPosts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PublicBadge {
        private UUID id;
        private String name;
        private String iconUrl;
        private Instant earnedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentPost {
        private UUID id;
        private String body;
        private String[] thumbnailUrls;
        private String[] mediumUrls;
        private String[] mediaUrls;
        private long commentCount;
        private long totalReactionCount;
        private Instant createdAt;
    }
}
