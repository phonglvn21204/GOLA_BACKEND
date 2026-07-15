package com.gola.repository;
import com.gola.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
    List<TripStop> findByTrip_IdOrderByOrderIdxAsc(UUID tripId);
    @Query("SELECT COALESCE(MAX(s.orderIdx), 0) FROM TripStop s WHERE s.trip.id = :tripId")
    Double findMaxOrderIdx(UUID tripId);

    @Query("""
        SELECT COUNT(s) FROM TripStop s WHERE s.completedAt IS NOT NULL
        AND s.trip.deletedAt IS NULL
        AND (
            s.trip.ownerId = :userId OR
            EXISTS(SELECT m FROM TripMember m WHERE m.tripId = s.trip.id AND m.userId = :userId)
        )
        """)
    long countCompletedStopsForUser(UUID userId);
}
