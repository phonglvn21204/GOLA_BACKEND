package com.gola.dto.community;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private UUID id;
    private UUID authorId;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String body;
    private String[] mediaUrls;
    private String[] thumbnailUrls;
    private String[] mediumUrls;
    private UUID tripId;
    private boolean isHidden;
    private List<String> hashtags;
    private long likeCount;
    private long commentCount;
    private String userReaction;
    private String currentUserReaction;
    private Map<String, Long> reactionCounts;
    private long totalReactionCount;
    private boolean savedByCurrentUser;
    private long savedCount;
    private Instant createdAt;
    private Instant updatedAt;
}
