package com.gola.dto.trip;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveLocationRequest {
    @NotNull private Double lat;
    @NotNull private Double lng;
    private Double heading;
    private Double speed;
    private Double accuracy;
}
