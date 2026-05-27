package com.gola.dto.trip;

import lombok.Data;

@Data
public class StartTripRequest {
    /** Optional: override session notes or context */
    private String notes;
}
