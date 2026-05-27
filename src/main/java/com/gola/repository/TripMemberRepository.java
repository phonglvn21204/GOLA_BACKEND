package com.gola.repository;
import com.gola.entity.TripMember;
import com.gola.entity.TripMemberId;
import com.gola.entity.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripMemberRepository extends JpaRepository<TripMember, TripMemberId> {
    List<TripMember> findByTripId(UUID tripId);
    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);
    boolean existsByTripIdAndUserId(UUID tripId, UUID userId);
    boolean existsByTripIdAndUserIdAndRoleIn(UUID tripId, UUID userId, List<MemberRole> roles);
}