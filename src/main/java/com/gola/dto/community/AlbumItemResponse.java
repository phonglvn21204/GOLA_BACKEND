package com.gola.dto.community;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;
import java.util.List;

@Data
@Builder
public class AlbumItemResponse {
    private UUID id;
    private UUID tripStopId;
    private UUID memoryPhotoId;
    private String stopName;
    private String title;
    private String caption;
    private String shortCaption;
    private String emotionalTone;
    private String locationHint;
    private String highlight;
    private String imageUrl;
    private String visualSummary;
    private String microStory;
    private String stickerText;
    private String cropFocus;
    private String filterSuggestion;
    private String sceneType;
    private String mood;
    private String layoutType;
    private Double qualityScore;
    private Boolean coverCandidate;
    private List<String> tags;
    private List<String> mainColors;
    private int orderIdx;
}
