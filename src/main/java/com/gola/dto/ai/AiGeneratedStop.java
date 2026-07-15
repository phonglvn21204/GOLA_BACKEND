package com.gola.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedStop {
    private int day;
    private String timeOfDay;
    private String startTime;
    private Integer durationMinutes;
    private BigDecimal estimatedCost;
    private String category;
    private String searchQuery;
    private Boolean systemStop;
    private Boolean mustHave;
    private String flexibility;
    private String candidateProviderId;
    private String providerTitle;
    private String rationale;
    private String mealType;
    private String experienceType;
    private Map<String, Object> travelFromPrevious;
    private String placeName;
    private String description;
    private Double lat;
    private Double lng;
    private String imageUrl;
}
