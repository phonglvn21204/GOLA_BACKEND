package com.gola.dto.map;

import com.gola.entity.enums.ExploreCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplorePlaceResponse {
    private String placeId;
    private String name;
    private Double lat;
    private Double lng;
    private String address;
    private ExploreCategory category;
}
