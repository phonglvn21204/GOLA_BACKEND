package com.gola.dto.community;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AlbumDayResponse {
    private int dayIndex;
    private String title;
    private String summary;
    private String layoutType;
    private List<AlbumItemResponse> items;
}
