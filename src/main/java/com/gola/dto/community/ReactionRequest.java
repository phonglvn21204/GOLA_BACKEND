package com.gola.dto.community;

import com.gola.entity.enums.ReactionKind;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReactionRequest {
    @NotNull
    private ReactionKind kind;
}
