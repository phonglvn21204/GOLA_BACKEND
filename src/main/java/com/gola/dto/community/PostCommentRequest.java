package com.gola.dto.community;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class PostCommentRequest {
    @NotBlank
    private String body;

    private UUID parentId;
}
