package com.gola.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public class FollowId implements Serializable {
    private UUID followerId;
    private UUID followeeId;
}
