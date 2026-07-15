package com.gola.repository;

import com.gola.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {
    List<UserBadge> findByUserId(UUID userId);
    List<UserBadge> findTop6ByUserIdOrderByEarnedAtDesc(UUID userId);
    Optional<UserBadge> findByUserIdAndBadgeId(UUID userId, UUID badgeId);
    boolean existsByUserIdAndBadgeId(UUID userId, UUID badgeId);
    long countByUserId(UUID userId);
}
