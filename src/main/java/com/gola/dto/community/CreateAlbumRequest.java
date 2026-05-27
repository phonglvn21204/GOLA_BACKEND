package com.gola.dto.community;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAlbumRequest {
    private UUID tripId;
    @NotBlank(message = "Title cannot be blank")
    private String title;
    private boolean isPublic;
}
