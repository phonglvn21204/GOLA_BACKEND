package com.gola.dto.community;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private UUID id;
    private UUID authorId;
    private String body;
    private String[] mediaUrls;
    private UUID tripId;
    private boolean isHidden;
    private List<String> hashtags;
    private Instant createdAt;
    private Instant updatedAt;
}
