package com.gola.dto.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunitySidebarResponse {
    private CurrentUserSummary currentUserSummary;
    private List<SuggestedTraveler> suggestedTravelers;
    private List<TrendingHashtag> trendingHashtags;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CurrentUserSummary {
        private long tripsCompleted;
        private long stopsVisited;
        private long questsCompleted;
        private long badgesEarned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuggestedTraveler {
        private UUID userId;
        private String displayName;
        private String avatarUrl;
        private String homeCity;
        private long followerCount;
        private boolean followedByCurrentUser;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendingHashtag {
        private String tag;
        private long postCount;
    }
}
