package com.gola.service;

import com.gola.dto.trip.LiveLocationRequest;
import com.gola.dto.trip.LiveLocationResponse;
import com.gola.entity.LiveLocation;
import com.gola.entity.enums.SessionStatus;
import com.gola.exception.GolaException;
import com.gola.repository.LiveLocationRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveLocationService {

    private final LiveLocationRepository  locationRepo;
    private final TripSessionRepository   sessionRepo;
    private final TripMemberRepository    memberRepo;

    @Transactional
    public LiveLocationResponse ping(UUID sessionId, UUID userId, LiveLocationRequest req) {
        // Validate session is active
        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> GolaException.notFound("Session"));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw GolaException.badRequest("Session is not active");
        }
        // Validate user is a member of the trip
        if (!memberRepo.existsByTripIdAndUserId(session.getTripId(), userId)) {
            throw GolaException.forbidden();
        }

        Instant ts = Instant.now();
        locationRepo.insertPing(
                sessionId,
                userId,
                req.getLat(),
                req.getLng(),
                req.getHeading(),
                req.getSpeed(),
                req.getAccuracy(),
                ts);

        LiveLocation loc = LiveLocation.builder()
                .sessionId(sessionId)
                .userId(userId)
                .lat(req.getLat())
                .lng(req.getLng())
                .heading(req.getHeading())
                .speed(req.getSpeed())
                .accuracy(req.getAccuracy())
                .ts(ts)
                .build();

        log.debug("Location ping: user={} session={} lat={} lng={}", userId, sessionId, req.getLat(), req.getLng());
        return toResponse(loc);
    }

    public List<LiveLocationResponse> getLatestLocations(UUID sessionId, UUID userId) {
        var session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> GolaException.notFound("Session"));
        if (!memberRepo.existsByTripIdAndUserId(session.getTripId(), userId)) {
            throw GolaException.forbidden();
        }
        return locationRepo.findLatestPerUserBySessionId(sessionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private LiveLocationResponse toResponse(LiveLocation l) {
        return LiveLocationResponse.builder()
                .userId(l.getUserId())
                .sessionId(l.getSessionId())
                .lat(l.getLat())
                .lng(l.getLng())
                .heading(l.getHeading())
                .speed(l.getSpeed())
                .accuracy(l.getAccuracy())
                .ts(l.getTs())
                .build();
    }
}
