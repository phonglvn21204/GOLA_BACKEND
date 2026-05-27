package com.gola.dto.quest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class BadgeResponse {
    private UUID id;
    private String name;
    private String iconUrl;
    private String criteria;
    private boolean isActive;
}
