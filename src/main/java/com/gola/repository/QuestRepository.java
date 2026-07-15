package com.gola.repository;
import com.gola.entity.Quest;
import com.gola.entity.enums.QuestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository public interface QuestRepository extends JpaRepository<Quest, UUID> {
    List<Quest> findByIsActiveTrueOrderByCreatedAtDesc();
    List<Quest> findByTypeAndIsActiveTrueOrderByCreatedAtDesc(QuestType type);
    List<Quest> findAllByOrderByCreatedAtDesc();
    List<Quest> findByIsActiveTrueAndStatusIgnoreCaseOrderByCreatedAtDesc(String status);
}
