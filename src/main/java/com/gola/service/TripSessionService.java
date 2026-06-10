package com.gola.service;

import com.gola.dto.trip.TripSessionResponse;
import com.gola.entity.TripSession;
import com.gola.entity.Trip;
import com.gola.entity.enums.SessionStatus;
import com.gola.entity.enums.TripStatus;
import com.gola.exception.GolaException;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripSessionRepository;
import com.gola.entity.enums.MemberRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripSessionService {

    private final TripSessionRepository sessionRepo;
    private final TripRepository        tripRepo;
    private final TripMemberRepository  memberRepo;

    @Transactional
    public TripSessionResponse startSession(UUID tripId, UUID userId) {
        requireEditorOrOwner(tripId, userId);
        sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE).ifPresent(s -> {
            throw GolaException.conflict("A live session is already active for this trip");
        });
        Trip trip = tripRepo.findActiveById(tripId)
                .orElseThrow(() -> GolaException.notFound("Trip"));
        trip.setStatus(TripStatus.ACTIVE);
        tripRepo.save(trip);

        TripSession session = sessionRepo.save(
                TripSession.builder()
                        .tripId(tripId)
                        .status(SessionStatus.ACTIVE)
                        .build());

        log.info("Trip session started: {} for trip: {}", session.getId(), tripId);
        return toResponse(session);
    }

    @Transactional
    public TripSessionResponse endSession(UUID tripId, UUID userId) {
        requireEditorOrOwner(tripId, userId);
        TripSession session = sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE)
                .orElseThrow(() -> GolaException.notFound("Active session"));

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        sessionRepo.save(session);

        Trip trip = tripRepo.findActiveById(tripId)
                .orElseThrow(() -> GolaException.notFound("Trip"));
        trip.setStatus(TripStatus.COMPLETED);
        tripRepo.save(trip);

        log.info("Trip session ended: {} for trip: {}", session.getId(), tripId);
        return toResponse(session);
    }

    public TripSessionResponse getActiveSession(UUID tripId, UUID userId) {
        requireMember(tripId, userId);
        TripSession session = sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE)
                .orElseThrow(() -> GolaException.notFound("Active session"));
        return toResponse(session);
    }

    @Transactional
    public TripSessionResponse endSessionById(UUID sessionId, UUID userId) {
        TripSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> GolaException.notFound("Session"));
        return endSession(session.getTripId(), userId);
    }

    public TripSessionResponse getSessionById(UUID sessionId, UUID userId) {
        TripSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> GolaException.notFound("Session"));
        requireMember(session.getTripId(), userId);
        return toResponse(session);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireEditorOrOwner(UUID tripId, UUID userId) {
        if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(tripId, userId,
                List.of(MemberRole.OWNER, MemberRole.EDITOR))) {
            throw GolaException.forbidden();
        }
    }

    private void requireMember(UUID tripId, UUID userId) {
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
    }

    private TripSessionResponse toResponse(TripSession s) {
        return TripSessionResponse.builder()
                .id(s.getId())
                .tripId(s.getTripId())
                .status(s.getStatus())
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .build();
    }
}
