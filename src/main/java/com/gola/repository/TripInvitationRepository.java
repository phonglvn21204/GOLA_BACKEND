package com.gola.repository;

import com.gola.entity.TripInvitation;
import com.gola.entity.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripInvitationRepository extends JpaRepository<TripInvitation, UUID> {
    Optional<TripInvitation> findByToken(String token);
    List<TripInvitation> findAllByTripIdAndStatus(UUID tripId, InvitationStatus status);
    List<TripInvitation> findAllByEmailAndStatus(String email, InvitationStatus status);
    boolean existsByTripIdAndEmailAndStatus(UUID tripId, String email, InvitationStatus status);

    @Modifying
    @Query("UPDATE TripInvitation i SET i.status = 'EXPIRED' WHERE i.status = 'PENDING' AND i.expiresAt < :now")
    int expireAll(Instant now);
}
