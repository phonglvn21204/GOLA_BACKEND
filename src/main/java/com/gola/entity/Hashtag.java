package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "hashtags")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Hashtag {
    @Id
    @Column(length = 100)
    private String tag;

    @Column(name = "post_count", nullable = false)
    @Builder.Default
    private int postCount = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
