package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ReelStoryboardResponse {
    private String status;
    private String title;
    private int durationSeconds;
    private List<ReelSlideResponse> slides;
    private String error;
    private Instant generatedAt;

    @Data
    @Builder
    public static class ReelSlideResponse {
        private int order;
        private String type;
        private String title;
        private String caption;
        private String imageUrl;
        private int durationSeconds;
    }
}
