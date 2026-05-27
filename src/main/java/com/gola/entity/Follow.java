package com.gola.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Follow {

    @Id
    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Id
    @Column(name = "followee_id", nullable = false)
    private UUID followeeId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
