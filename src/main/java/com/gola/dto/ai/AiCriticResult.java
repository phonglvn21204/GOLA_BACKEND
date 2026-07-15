package com.gola.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiCriticResult {
    private int score;
    @Builder.Default
    private List<String> problems = new ArrayList<>();
    @Builder.Default
    private List<String> requiredFixes = new ArrayList<>();
    @Builder.Default
    private List<MissingSlot> missingSlots = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedQueries = new ArrayList<>();
    private boolean passed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MissingSlot {
        private String slotType; // BREAKFAST, LUNCH, DINNER, CAFE, HOTEL, NIGHT, ATTRACTION
        private int dayIndex;
        private String searchQuery;
    }
}
