package com.gola.dto.quest;

import lombok.Data;

@Data
public class SubmitProofRequest {
    private String mediaId;
    private String proofImageUrl;
    private Double lat;
    private Double lng;
    private String note;
}
