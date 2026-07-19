package com.gola.repository;
import com.gola.entity.SosEvent;
import com.gola.entity.enums.SosStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository public interface SosEventRepository extends JpaRepository<SosEvent, UUID> {
    Optional<SosEvent> findByClientToken(String token);
    List<SosEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SosEvent> findByStatus(SosStatus status);
    List<SosEvent> findByStatusIn(List<SosStatus> statuses);
    List<SosEvent> findByStatusAndCreatedAtBefore(SosStatus status, Instant createdAt);
    List<SosEvent> findByTripIdAndStatus(UUID tripId, SosStatus status);
}
