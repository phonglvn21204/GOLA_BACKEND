package com.gola.repository;

import com.gola.entity.CommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, UUID> {
    List<CommentReaction> findAllByCommentIdAndUserId(UUID commentId, UUID userId);

    Optional<CommentReaction> findByCommentIdAndUserId(UUID commentId, UUID userId);

    void deleteByCommentIdAndUserId(UUID commentId, UUID userId);

    long countByCommentId(UUID commentId);

    @Query("""
        SELECT cr.commentId, COUNT(cr)
        FROM CommentReaction cr
        WHERE cr.commentId IN :commentIds
        GROUP BY cr.commentId
        """)
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

    @Query("""
        SELECT cr.commentId
        FROM CommentReaction cr
        WHERE cr.userId = :userId AND cr.commentId IN :commentIds
        """)
    Set<UUID> findLikedCommentIds(
        @Param("userId") UUID userId,
        @Param("commentIds") Collection<UUID> commentIds
    );
}
