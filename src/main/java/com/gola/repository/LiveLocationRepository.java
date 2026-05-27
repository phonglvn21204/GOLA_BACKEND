package com.gola.repository;

import com.gola.entity.LiveLocation;
import com.gola.entity.LiveLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveLocationRepository extends JpaRepository<LiveLocation, LiveLocationId> {

    /** Latest ping per user for a session */
    @Query("SELECT l FROM LiveLocation l WHERE l.sessionId = :sessionId AND l.ts = " +
           "(SELECT MAX(l2.ts) FROM LiveLocation l2 WHERE l2.sessionId = :sessionId AND l2.userId = l.userId)")
    List<LiveLocation> findLatestPerUserBySessionId(UUID sessionId);

    /** All pings for one user in a session, ordered newest-first */
    List<LiveLocation> findBySessionIdAndUserIdOrderByTsDesc(UUID sessionId, UUID userId);

    /** Most recent single ping for a user */
    Optional<LiveLocation> findTopBySessionIdAndUserIdOrderByTsDesc(UUID sessionId, UUID userId);

    /** Cleanup old location rows */
    void deleteByTsBefore(Instant cutoff);

    @Modifying
    @Query(value = """
            INSERT INTO live_locations (session_id, user_id, geom, lat, lng, heading, speed, accuracy, ts)
            VALUES (:sessionId, :userId,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                    :lat, :lng, :heading, :speed, :accuracy, :ts)
            """, nativeQuery = true)
    void insertPing(@Param("sessionId") UUID sessionId,
                    @Param("userId") UUID userId,
                    @Param("lat") double lat,
                    @Param("lng") double lng,
                    @Param("heading") Double heading,
                    @Param("speed") Double speed,
                    @Param("accuracy") Double accuracy,
                    @Param("ts") Instant ts);
}
