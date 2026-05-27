package com.gola.repository;

import com.gola.entity.Trip;
import com.gola.entity.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @Query("SELECT t FROM Trip t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Trip> findActiveById(UUID id);

    @Query("""
        SELECT t FROM Trip t WHERE t.ownerId = :userId AND t.status = :status AND t.deletedAt IS NULL
        """)
    List<Trip> findByOwnerAndStatus(UUID userId, TripStatus status);

    long countByStatusAndDeletedAtIsNull(TripStatus status);
}