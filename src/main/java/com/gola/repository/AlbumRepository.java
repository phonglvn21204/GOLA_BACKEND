package com.gola.repository;

import com.gola.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlbumRepository extends JpaRepository<Album, UUID> {
    List<Album> findByTripId(UUID tripId);
    Optional<Album> findFirstByTripIdAndOwnerIdAndIsAiCuratedTrueOrderByCreatedAtDesc(UUID tripId, UUID ownerId);
}
