package com.gola.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelResearchContext {
    private String destination;
    private String monthText;
    @Builder.Default private List<String> seasonalNotes = new ArrayList<>();
    @Builder.Default private List<String> recommendedAttractions = new ArrayList<>();
    @Builder.Default private List<String> foodSuggestions = new ArrayList<>();
    @Builder.Default private List<String> hotelAreaSuggestions = new ArrayList<>();
    @Builder.Default private List<String> warningsOrTips = new ArrayList<>();
    @Builder.Default private List<SourceSummary> sourceSummaries = new ArrayList<>();

    public static TravelResearchContext empty(String destination) {
        return TravelResearchContext.builder()
                .destination(destination)
                .seasonalNotes(new ArrayList<>())
                .recommendedAttractions(new ArrayList<>())
                .foodSuggestions(new ArrayList<>())
                .hotelAreaSuggestions(new ArrayList<>())
                .warningsOrTips(new ArrayList<>())
                .sourceSummaries(new ArrayList<>())
                .build();
    }

    public boolean hasUsefulContext() {
        return !seasonalNotes.isEmpty()
                || !recommendedAttractions.isEmpty()
                || !foodSuggestions.isEmpty()
                || !hotelAreaSuggestions.isEmpty()
                || !warningsOrTips.isEmpty()
                || !sourceSummaries.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSummary {
        private String title;
        private String snippet;
        private String sourceDomain;
        private String link;
        private Integer rank;
    }
}
