package com.gola.repository;

import com.gola.entity.PostHashtag;
import com.gola.entity.PostHashtagId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {
    List<PostHashtag> findByPostId(UUID postId);
    void deleteByPostId(UUID postId);

    @Query("""
        SELECT ph.tag, COUNT(ph.tag)
        FROM PostHashtag ph
        JOIN Post p ON p.id = ph.postId
        WHERE p.isHidden = false
        GROUP BY ph.tag
        ORDER BY COUNT(ph.tag) DESC
        """)
    List<Object[]> findTrendingTags(Pageable pageable);
}
