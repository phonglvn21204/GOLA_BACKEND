package com.gola.repository;

import com.gola.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {
    List<SavedPlace> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<SavedPlace> findByUserIdAndExternalPlaceId(UUID userId, String externalPlaceId);

    Optional<SavedPlace> findByUserIdAndPlaceId(UUID userId, UUID placeId);
}
