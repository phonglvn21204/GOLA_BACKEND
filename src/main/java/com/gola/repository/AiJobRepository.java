package com.gola.repository;
import com.gola.entity.AiJob;
import com.gola.entity.enums.AiJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    List<AiJob> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AiJobStatus status);
}