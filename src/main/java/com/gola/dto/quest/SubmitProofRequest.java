package com.gola.dto.quest;

import lombok.Data;

@Data
public class SubmitProofRequest {
    private String mediaId;
    private Double lat;
    private Double lng;
}
