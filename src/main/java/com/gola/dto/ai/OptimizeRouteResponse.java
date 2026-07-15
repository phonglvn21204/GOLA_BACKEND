package com.gola.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeRouteResponse {
    private List<OptimizedStop> stops;
    private String summary;
    private Double distanceBeforeKm;
    private Double distanceAfterKm;
    private Double distanceSavedKm;
    private Integer conflictsFixed;
    private Integer missingDataRemaining;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizedStop {
        private UUID id;
        private String name;
        private Double orderIdx;
        private Instant arrivalAt;
        private Double lat;
        private Double lng;
    }
}
