package com.gola.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
public class LiveLocationId implements Serializable {
    private UUID sessionId;
    private UUID userId;
    private Instant ts;
}
