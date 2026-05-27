package com.gola.repository;

import com.gola.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.profile.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(UUID userId, Instant now);
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff OR r.revokedAt IS NOT NULL")
    int deleteExpiredAndRevoked(Instant cutoff);
}