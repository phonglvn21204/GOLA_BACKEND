package com.gola.repository;

import com.gola.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    @Query("""
        SELECT t FROM Trip t WHERE t.deletedAt IS NULL AND (
            t.ownerId = :userId OR
            EXISTS(SELECT m FROM TripMember m WHERE m.tripId = t.id AND m.userId = :userId)
        ) ORDER BY t.createdAt DESC
        """)
    Page<Trip> findAllForUser(UUID userId, Pageable pageable);

    @Query("""
        SELECT DISTINCT t FROM Trip t WHERE t.deletedAt IS NULL AND (
            t.ownerId = :userId OR
            EXISTS(SELECT m FROM TripMember m WHERE m.tripId = t.id AND m.userId = :userId)
        ) ORDER BY t.createdAt DESC
        """)
    List<Trip> findAllForUser(UUID userId);

    @Query(value = """
        SELECT DISTINCT t.* FROM trips t WHERE t.deleted_at IS NULL AND CAST(t.status AS text) IN (:statuses) AND (
            t.owner_id = :userId OR
            EXISTS(SELECT 1 FROM trip_members m WHERE m.trip_id = t.id AND m.user_id = :userId)
        ) ORDER BY t.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT t.id) FROM trips t WHERE t.deleted_at IS NULL AND CAST(t.status AS text) IN (:statuses) AND (
            t.owner_id = :userId OR
            EXISTS(SELECT 1 FROM trip_members m WHERE m.trip_id = t.id AND m.user_id = :userId)
        )
        """,
        nativeQuery = true)
    Page<Trip> findAllForUserByStatusIn(@Param("userId") UUID userId, @Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Trip> findActiveById(UUID id);

    @Query(value = """
        SELECT * FROM trips t WHERE t.owner_id = :userId
        AND CAST(t.status AS text) = :status
        AND t.deleted_at IS NULL
        """, nativeQuery = true)
    List<Trip> findByOwnerAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Query(value = "SELECT COUNT(*) FROM trips t WHERE CAST(t.status AS text) = :status AND t.deleted_at IS NULL", nativeQuery = true)
    long countByStatusNameAndDeletedAtIsNull(@Param("status") String status);

    @Query(value = """
        SELECT COUNT(DISTINCT t.id) FROM trips t WHERE CAST(t.status AS text) = 'COMPLETED'
        AND t.deleted_at IS NULL AND (
            t.owner_id = :userId OR
            EXISTS(SELECT 1 FROM trip_members m WHERE m.trip_id = t.id AND m.user_id = :userId)
        )
        """, nativeQuery = true)
    long countCompletedForUser(@Param("userId") UUID userId);
}
