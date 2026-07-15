package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TripMemoryResponse {
    private UUID id;
    private UUID tripId;
    private UUID userId;
    private String title;
    private String summary;
    private String status;
    private String shareStatus;
    private Instant generatedAt;
    private UUID albumId;
    private String albumStatus;
    private String albumError;
    private Instant albumGeneratedAt;
    private String bookStatus;
    private String bookUrl;
    private String bookDownloadUrl;
    private String bookError;
    private Instant bookGeneratedAt;
    private String reelStatus;
    private Object reelStoryboard;
    private String reelError;
    private Instant reelGeneratedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
