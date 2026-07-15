package com.gola.dto.safety;

import com.gola.dto.trip.TripResponse;
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
public class ApplyIncidentSuggestionResponse {
    private UUID incidentId;
    private UUID tripId;
    private String suggestionId;
    private String summary;
    private List<String> actionTypes;
    private int changedStopsCount;
    private TripResponse trip;
}
