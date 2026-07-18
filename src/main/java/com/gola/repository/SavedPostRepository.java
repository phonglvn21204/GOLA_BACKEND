package com.gola.repository;

import com.gola.entity.Post;
import com.gola.entity.SavedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    void deleteByPostId(UUID postId);

    long countByPostId(UUID postId);

    @Query("""
        SELECT s.postId
        FROM SavedPost s
        WHERE s.userId = :userId AND s.postId IN :postIds
        """)
    Set<UUID> findSavedPostIds(
        @Param("userId") UUID userId,
        @Param("postIds") Collection<UUID> postIds
    );

    @Query("""
        SELECT p
        FROM Post p JOIN SavedPost s ON s.postId = p.id
        WHERE s.userId = :userId AND p.isHidden = false
        ORDER BY s.createdAt DESC
        """)
    Page<Post> findSavedPosts(@Param("userId") UUID userId, Pageable pageable);
}
