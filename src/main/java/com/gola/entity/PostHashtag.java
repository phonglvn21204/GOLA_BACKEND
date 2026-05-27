package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "post_hashtags")
@IdClass(PostHashtagId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PostHashtag {
    @Id
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Id
    @Column(name = "tag", nullable = false, length = 100)
    private String tag;
}
