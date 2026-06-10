package com.gola.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedStop {
    private int day;
    private String timeOfDay;
    private String placeName;
    private String description;
    private Double lat;
    private Double lng;
    private String imageUrl;
}
