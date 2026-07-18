package com.gola.repository;

import com.gola.entity.PostReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {
    List<PostReaction> findByPostId(UUID postId);
    Optional<PostReaction> findByPostIdAndUserId(UUID postId, UUID userId);
    List<PostReaction> findAllByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostId(UUID postId);
    int countByPostId(UUID postId);
}
