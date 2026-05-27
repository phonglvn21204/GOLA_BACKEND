package com.gola.repository;

import com.gola.entity.Follow;
import com.gola.entity.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId> {
    List<Follow> findByFollowerId(UUID followerId);
    List<Follow> findByFolloweeId(UUID followeeId);
    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
    void deleteByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
}
