package com.gola.dto.trip;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateTripMemoryPhotoRequest {
    private Integer dayIndex;
    private UUID tripStopId;
    private String captionNote;
    private Integer sortOrder;
}
