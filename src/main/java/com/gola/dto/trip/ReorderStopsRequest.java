package com.gola.dto.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ReorderStopsRequest {
    @NotEmpty
    @Valid
    private List<StopOrder> stops;

    @Data
    public static class StopOrder {
        @NotNull
        private UUID id;

        @NotNull
        private Double orderIdx;

        private Instant arrivalAt;
    }
}
