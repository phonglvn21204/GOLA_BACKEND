package com.gola.repository;

import com.gola.entity.PostHashtag;
import com.gola.entity.PostHashtagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {
    List<PostHashtag> findByPostId(UUID postId);
    void deleteByPostId(UUID postId);
}
