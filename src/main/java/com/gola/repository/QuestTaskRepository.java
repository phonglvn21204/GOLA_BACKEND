package com.gola.repository;

import com.gola.entity.QuestTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

@Repository
public interface QuestTaskRepository extends JpaRepository<QuestTask, UUID> {
    List<QuestTask> findByQuestIdOrderByIdxAsc(UUID questId);
    Optional<QuestTask> findByQuestIdAndIdx(UUID questId, int idx);

    @Query(value = "SELECT COALESCE(ST_DWithin(geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius), false) " +
                   "FROM quest_tasks WHERE quest_id = :questId AND idx = :idx", nativeQuery = true)
    Boolean isWithinRadius(UUID questId, int idx, Double lat, Double lng, Double radius);
}
