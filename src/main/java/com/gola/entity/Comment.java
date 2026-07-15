package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "comments",
       indexes = @Index(name = "idx_comment_post", columnList = "post_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Comment extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 2000)
    private String body;

    @Builder.Default
    @Column(name = "is_hidden", nullable = false)
    private boolean hidden = false;

    /** Null means top-level comment; non-null means reply */
    @Column(name = "parent_id")
    private UUID parentId;
}
