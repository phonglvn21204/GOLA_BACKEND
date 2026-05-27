package com.gola.repository;
import com.gola.entity.TripSession;
import com.gola.entity.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripSessionRepository extends JpaRepository<TripSession, UUID> {
    Optional<TripSession> findByTripIdAndStatus(UUID tripId, SessionStatus status);
    @Query("SELECT s FROM TripSession s WHERE s.status = 'ACTIVE' AND s.startedAt < :cutoff")
    List<TripSession> findStaleSessions(Instant cutoff);
}