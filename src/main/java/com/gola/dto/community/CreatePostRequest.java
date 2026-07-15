package com.gola.dto.community;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequest {
    private String body;
    private UUID tripId;
    private List<String> hashtags;
    private String[] mediaUrls;
    private String[] thumbnailUrls;
    private String[] mediumUrls;
}
