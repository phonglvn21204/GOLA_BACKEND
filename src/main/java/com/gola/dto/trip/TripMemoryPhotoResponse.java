package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TripMemoryPhotoResponse {
    private UUID id;
    private UUID tripMemoryId;
    private UUID tripId;
    private UUID userId;
    private UUID mediaId;
    private String imageUrl;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;
    private Integer dayIndex;
    private UUID tripStopId;
    private String tripStopName;
    private String captionNote;
    private String aiCaption;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
