package com.gola.repository;

import com.gola.entity.QuestProgress;
import com.gola.entity.enums.QuestProgressStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestProgressRepository extends JpaRepository<QuestProgress, UUID> {
    Optional<QuestProgress> findByQuestIdAndUserId(UUID questId, UUID userId);
    Optional<QuestProgress> findByIdAndUserId(UUID id, UUID userId);
    Optional<QuestProgress> findByQuestIdAndUserIdAndTripId(UUID questId, UUID userId, UUID tripId);
    Optional<QuestProgress> findByQuestIdAndUserIdAndTripIdAndTripStopId(UUID questId, UUID userId, UUID tripId, UUID tripStopId);
    boolean existsByQuestIdAndUserIdAndTripIdAndTripStopId(UUID questId, UUID userId, UUID tripId, UUID tripStopId);
    List<QuestProgress> findByUserIdAndStatus(UUID userId, QuestProgressStatus status);
    List<QuestProgress> findByTripIdAndUserIdOrderByCreatedAtAsc(UUID tripId, UUID userId);
    List<QuestProgress> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<QuestProgress> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<QuestProgress> findByStatusOrderByCreatedAtDesc(QuestProgressStatus status, Pageable pageable);
    List<QuestProgress> findTop5ByStatusOrderByCompletedAtDesc(QuestProgressStatus status);
    long countByStatus(QuestProgressStatus status);
    long countByStatusAndVerifiedAtAfter(QuestProgressStatus status, Instant since);
    long countByTripIdAndUserIdAndStatus(UUID tripId, UUID userId, QuestProgressStatus status);
    long countByUserIdAndStatus(UUID userId, QuestProgressStatus status);

    @Query(value = """
        SELECT COALESCE(SUM(q.reward_coins), 0)
        FROM quest_progress qp
        JOIN quests q ON qp.quest_id = q.id
        WHERE qp.user_id = :userId AND CAST(qp.status AS text) IN ('APPROVED', 'COMPLETED')
        """, nativeQuery = true)
    long sumCompletedQuestCoinsForUser(@Param("userId") UUID userId);
}
