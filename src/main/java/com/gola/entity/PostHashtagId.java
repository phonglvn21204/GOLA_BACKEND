package com.gola.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostHashtagId implements Serializable {
    private UUID postId;
    private String tag;
}
