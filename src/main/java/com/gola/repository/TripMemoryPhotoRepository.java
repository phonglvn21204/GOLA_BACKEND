package com.gola.repository;

import com.gola.entity.TripMemoryPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripMemoryPhotoRepository extends JpaRepository<TripMemoryPhoto, UUID> {
    List<TripMemoryPhoto> findByTripIdAndUserIdOrderBySortOrderAscCreatedAtAsc(UUID tripId, UUID userId);
    Optional<TripMemoryPhoto> findByIdAndTripIdAndUserId(UUID id, UUID tripId, UUID userId);
    int countByTripIdAndUserId(UUID tripId, UUID userId);
}
