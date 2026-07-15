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
public class VerifiedCandidatePlace {
    private String name;
    private String providerTitle;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private BigDecimal rating;
    private Integer reviewCount;
    private String imageUrl;
    private String providerId;
    private String providerSource;
    private String imageSource;
    private BigDecimal estimatedCost;
    private String sourceCandidateName;
    private String sourceArticleTitle;
    private String searchQuery;
    private boolean verified;
    private String rejectReason;
}
