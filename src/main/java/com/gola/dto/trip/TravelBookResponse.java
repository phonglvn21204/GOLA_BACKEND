package com.gola.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TravelBookResponse {
    private String status;
    private String pdfUrl;
    private String downloadUrl;
    private String error;
    private Instant generatedAt;
}
