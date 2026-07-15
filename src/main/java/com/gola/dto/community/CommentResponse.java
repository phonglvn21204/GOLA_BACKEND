package com.gola.dto.community;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class CommentResponse {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String body;
    private UUID parentId;
    private UUID parentCommentId;
    private List<CommentResponse> replies;
    private long likeCount;
    private boolean likedByCurrentUser;
    private String currentUserReaction;
    private boolean canDelete;
    private boolean hidden;
    private Instant createdAt;
    private Instant updatedAt;
}
