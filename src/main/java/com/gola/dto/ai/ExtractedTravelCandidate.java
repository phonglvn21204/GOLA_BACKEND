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
public class ExtractedTravelCandidate {
    private String name;
    private String category;
    private String reason;
    private String sourceTitle;
    private String sourceLink;
    private Integer confidence;
    private String searchQuery;
}
