package com.gola.dto.review;

import com.gola.entity.enums.ReviewStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UUID userId;
    private String userDisplayName;
    private String userEmail;
    private UUID tripId;
    private UUID stopId;
    private String placeName;
    private Integer rating;
    private String comment;
    private ReviewStatus status;
    private Integer reportCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant hiddenAt;
    private UUID hiddenBy;
    private String hideReason;
}
