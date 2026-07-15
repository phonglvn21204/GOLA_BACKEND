package com.gola.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidatePlace {
    private String title;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private BigDecimal rating;
    private Integer reviewCount;
    private String imageUrl;
    private String providerId;
    private String source;
    private BigDecimal estimatedCost;
    private String matchReason;
    private String searchQuery;
}
