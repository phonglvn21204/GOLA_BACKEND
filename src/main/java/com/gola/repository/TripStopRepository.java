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
}