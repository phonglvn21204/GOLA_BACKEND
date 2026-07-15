package com.gola.dto.community;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlbumResponse {
    private UUID id;
    private UUID ownerId;
    private UUID tripId;
    private String title;
    private String coverUrl;
    private boolean isPublic;
    private boolean isAiCurated;
    private String albumSource;
    private String status;
    private String summary;
    private String subtitle;
    private String introStory;
    private String mood;
    private List<String> colorPalette;
    private String designDirection;
    private String coverCaption;
    private UUID coverPhotoId;
    private String analysisMode;
    private Integer analyzedPhotoCount;
    private String shareCaption;
    private List<String> hashtags;
    private List<String> highlightMoments;
    private List<String> travelQuotes;
    private String errorMessage;
    private List<AlbumDayResponse> days;
    private Instant generatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
