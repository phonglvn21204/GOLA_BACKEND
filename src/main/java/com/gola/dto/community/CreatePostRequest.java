package com.gola.dto.community;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequest {
    @NotBlank(message = "Body cannot be blank")
    private String body;
    private UUID tripId;
    private List<String> hashtags;
    private String[] mediaUrls;
}
