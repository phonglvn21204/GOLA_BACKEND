package com.gola.repository;

import com.gola.entity.TripMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripMemoryRepository extends JpaRepository<TripMemory, UUID> {
    Optional<TripMemory> findByTripIdAndUserId(UUID tripId, UUID userId);
    List<TripMemory> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
