package com.gola.repository;
import com.gola.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;
@Repository public interface PostRepository extends JpaRepository<Post, UUID> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByIsHiddenFalseOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByAuthorIdAndIsHiddenFalseOrderByCreatedAtDesc(UUID authorId, Pageable pageable);
    long countByAuthorIdAndIsHiddenFalse(UUID authorId);
    List<Post> findTop3ByAuthorIdAndIsHiddenFalseOrderByCreatedAtDesc(UUID authorId);

    @Query("SELECT p FROM Post p JOIN PostHashtag ph ON p.id = ph.postId WHERE ph.tag = :tag AND p.isHidden = false ORDER BY p.createdAt DESC")
    Page<Post> findByHashtag(String tag, Pageable pageable);
}
