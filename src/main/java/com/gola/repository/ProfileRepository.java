package com.gola.repository;

import com.gola.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByEmail(String email);
    boolean existsByEmail(String email);
    @Query("SELECT p FROM Profile p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Profile> findActiveById(UUID id);

    @Query("""
        SELECT p FROM Profile p
        WHERE p.deletedAt IS NULL
        AND p.isPublic = true
        AND p.id <> :viewerId
        ORDER BY p.createdAt DESC
        """)
    List<Profile> findSuggestedTravelers(UUID viewerId, Pageable pageable);
}
