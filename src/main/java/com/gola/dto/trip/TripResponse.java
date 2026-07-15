package com.gola.dto.trip;

import com.gola.entity.enums.TripStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class TripResponse {
    private UUID id;
    private UUID ownerId;
    private String title;
    private String origin;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripStatus status;
    private boolean isPublic;
    private String coverUrl;
    private String description;
    private int stopsCount;
    private int membersCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private List<TripStopResponse> stops;
    private List<TripMemberResponse> members;
    private Double totalDistanceKm;
    private Integer totalTravelTimeMin;
    private Double distanceFromUserKm;
    private Integer travelTimeFromUserMin;
    private Integer questCompletedCount;
    private UUID memoryId;
    private String memoryStatus;
    private String memoryShareStatus;
    private String memorySummary;
    private String albumStatus;
    private String bookStatus;
    private String bookDownloadUrl;
    private String reelStatus;
    private Integer qualityScore;
    private String qualityWarning;
}
