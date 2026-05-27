package com.gola.repository;

import com.gola.entity.QuestProgress;
import com.gola.entity.enums.QuestProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestProgressRepository extends JpaRepository<QuestProgress, UUID> {
    Optional<QuestProgress> findByQuestIdAndUserId(UUID questId, UUID userId);
    List<QuestProgress> findByUserIdAndStatus(UUID userId, QuestProgressStatus status);
    long countByStatusAndVerifiedAtAfter(QuestProgressStatus status, Instant since);
}
