package com.gola.entity;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;
@Data public class TripMemberId implements Serializable {
    private UUID tripId;
    private UUID userId;
}