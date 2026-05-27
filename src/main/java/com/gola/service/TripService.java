package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.exception.GolaException;
import com.gola.dto.trip.*;
import com.gola.entity.*;
import com.gola.entity.enums.*;
import com.gola.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {
    private final TripRepository      tripRepo;
    private final TripStopRepository  stopRepo;
    private final TripMemberRepository memberRepo;
    private final TripSessionRepository sessionRepo;
    private final TripShareRepository shareRepo;

    @Transactional
    public TripResponse createTrip(UUID userId, CreateTripRequest req) {
        var trip = Trip.builder()
            .ownerId(userId)
            .title(req.getTitle())
            .origin(req.getOrigin())
            .destination(req.getDestination())
            .startDate(req.getStartDate())
            .endDate(req.getEndDate())
            .description(req.getDescription())
            .isPublic(req.isPublic())
            .status(TripStatus.DRAFT)
            .build();
        tripRepo.save(trip);
        memberRepo.save(TripMember.builder()
            .tripId(trip.getId()).userId(userId).role(MemberRole.OWNER).build());
        log.info("Trip created: {} by user: {}", trip.getId(), userId);
        return mapToResponse(trip);
    }

    public PageResponse<TripResponse> listMyTrips(UUID userId, Pageable pageable) {
        return new PageResponse<>(tripRepo.findAllForUser(userId, pageable).map(this::mapToResponse));
    }

    public TripResponse getTrip(UUID tripId, UUID userId) {
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.isPublic() && !memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        return mapToResponse(trip);
    }

    @Transactional
    public TripResponse updateTrip(UUID tripId, UUID userId, CreateTripRequest req) {
        var trip = getEditableTrip(tripId, userId);
        trip.setTitle(req.getTitle());
        trip.setOrigin(req.getOrigin());
        trip.setDestination(req.getDestination());
        trip.setStartDate(req.getStartDate());
        trip.setEndDate(req.getEndDate());
        trip.setDescription(req.getDescription());
        trip.setPublic(req.isPublic());
        return mapToResponse(tripRepo.save(trip));
    }

    @Transactional
    public void deleteTrip(UUID tripId, UUID userId) {
        var trip = getEditableTrip(tripId, userId);
        if (!trip.getOwnerId().equals(userId)) throw GolaException.forbidden();
        trip.setDeletedAt(Instant.now());
        tripRepo.save(trip);
    }

    @Transactional
    public TripStopResponse addStop(UUID tripId, UUID userId, AddStopRequest req) {
        getEditableTrip(tripId, userId);
        double orderIdx;

        if (req.getOrderIdx() != null && req.getOrderIdx() > 0) {
            // Nếu FE truyền orderIdx hợp lệ, dùng giá trị đó
            orderIdx = req.getOrderIdx();
        } else {
            // Nếu không, lấy max + 1000 để thêm cuối
            double maxIdx = stopRepo.findMaxOrderIdx(tripId);
            orderIdx = maxIdx + 1000;
        }

        var stop = TripStop.builder()
                .trip(tripRepo.getReferenceById(tripId))
                .placeId(req.getPlaceId())
                .name(req.getName())
                .orderIdx(orderIdx)
                .arrivalAt(req.getArrivalAt())
                .durationMin(req.getDurationMin())
                .notes(req.getNotes())
                .build();
        return mapStopToResponse(stopRepo.save(stop));
    }

    @Transactional
    public void deleteStop(UUID tripId, UUID stopId, UUID userId) {
        getEditableTrip(tripId, userId);
        var stop = stopRepo.findById(stopId).orElseThrow(() -> GolaException.notFound("Stop"));
        if (!stop.getTrip().getId().equals(tripId)) throw GolaException.forbidden();
        stopRepo.delete(stop);
    }

    @Transactional
    public TripSession startTrip(UUID tripId, UUID userId) {
        getEditableTrip(tripId, userId);
        sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE).ifPresent(s -> {
            throw GolaException.conflict("Trip session already active");
        });
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        trip.setStatus(TripStatus.ACTIVE);
        tripRepo.save(trip);
        return sessionRepo.save(TripSession.builder().tripId(tripId).status(SessionStatus.ACTIVE).build());
    }

    @Transactional
    public void endTrip(UUID tripId, UUID userId) {
        getEditableTrip(tripId, userId);
        sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE).ifPresent(s -> {
            s.setStatus(SessionStatus.ENDED);
            s.setEndedAt(Instant.now());
            sessionRepo.save(s);
        });
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        trip.setStatus(TripStatus.COMPLETED);
        tripRepo.save(trip);
    }

    public String createShareLink(UUID tripId, UUID userId, ShareTripRequest req) {
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.getOwnerId().equals(userId)) throw GolaException.forbidden();
        String token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(UUID.randomUUID().toString().getBytes());
        var share = TripShare.builder()
            .tripId(tripId).token(token).scope(req.getScope()).createdBy(userId)
            .expiresAt(Instant.now().plusSeconds(req.getTtlDays() * 86400L))
            .build();
        shareRepo.save(share);
        return token;
    }

    private Trip getEditableTrip(UUID tripId, UUID userId) {
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(tripId, userId,
            List.of(MemberRole.OWNER, MemberRole.EDITOR))) { throw GolaException.forbidden(); }
        return trip;
    }

    private TripResponse mapToResponse(Trip t) {
        return TripResponse.builder()
            .id(t.getId()).ownerId(t.getOwnerId()).title(t.getTitle())
            .origin(t.getOrigin()).destination(t.getDestination())
            .startDate(t.getStartDate()).endDate(t.getEndDate())
            .status(t.getStatus()).isPublic(t.isPublic())
            .coverUrl(t.getCoverUrl()).description(t.getDescription())
            .stopsCount(t.getStops().size()).membersCount(t.getMembers().size())
            .stops(t.getStops().stream().map(this::mapStopToResponse).toList())
            .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
            .build();
    }

    private TripStopResponse mapStopToResponse(TripStop s) {
        return TripStopResponse.builder()
            .id(s.getId()).placeId(s.getPlaceId()).orderIdx(s.getOrderIdx())
            .name(s.getName()).arrivalAt(s.getArrivalAt())
            .durationMin(s.getDurationMin()).notes(s.getNotes())
            .completedAt(s.getCompletedAt()).build();
    }

}