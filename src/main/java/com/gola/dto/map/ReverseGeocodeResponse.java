package com.gola.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReverseGeocodeResponse {
    private String address;
    private String formatted;
}
