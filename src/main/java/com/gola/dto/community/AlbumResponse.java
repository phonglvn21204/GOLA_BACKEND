package com.gola.dto.community;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlbumResponse {
    private UUID id;
    private UUID ownerId;
    private UUID tripId;
    private String title;
    private String coverUrl;
    private boolean isPublic;
    private boolean isAiCurated;
    private Instant createdAt;
}
