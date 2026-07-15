package com.gola.dto.safety;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAiSuggestionsResponse {
    private UUID incidentId;
    private UUID tripId;
    private String source;
    private List<IncidentAiSuggestion> suggestions;
}
