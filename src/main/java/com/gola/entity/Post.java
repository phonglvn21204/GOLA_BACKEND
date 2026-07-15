package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    private String body;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "media_urls", columnDefinition = "text[]")
    private String[] mediaUrls;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "thumbnail_urls", columnDefinition = "text[]")
    private String[] thumbnailUrls;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "medium_urls", columnDefinition = "text[]")
    private String[] mediumUrls;
    @Column(name = "trip_id") private UUID tripId;
    @Column(name = "is_hidden") private boolean isHidden;
    @CreatedDate @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
}
