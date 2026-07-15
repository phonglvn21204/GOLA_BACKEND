package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RepairPlaceDataResponse {
    private int repairedCount;
    private int repairedCoordinatesCount;
    private int repairedImagesCount;
    private int stillMissingCount;
    private int stillMissingCoordinatesCount;
    private int downgradedBadWikimediaImagesCount;
    private int serpApiAcceptedCount;
    private int goongFallbackCount;
    private int skippedSystemStopCount;
    private List<String> failedStopNames;
    private List<String> rejectedReasons;
}
