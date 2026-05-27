package com.gola.dto.trip;

import com.gola.entity.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class TripSessionResponse {
    private UUID id;
    private UUID tripId;
    private SessionStatus status;
    private Instant startedAt;
    private Instant endedAt;
}
