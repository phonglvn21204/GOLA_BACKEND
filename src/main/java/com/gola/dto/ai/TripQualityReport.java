package com.gola.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripQualityReport {
    private int realStopCount;
    private int systemStopCount;
    private int fallbackImageCount;
    private int missingCoordinateCount;
    private int duplicateProviderCount;
    private boolean hotelVerified;
    private double mealCoverage;
    private double cafeCoverage;
    private double attractionCoverage;
    private int dayDensityScore;
    private int qualityScore;
    private String qualityWarning;
}
