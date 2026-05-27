package com.gola.dto.community;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class CommentResponse {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String body;
    private UUID parentId;
    private Instant createdAt;
}
