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
    private final DistanceCalculator distanceCalculator;

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

    public TripResponse getTrip(UUID tripId, UUID userId, Double userLat, Double userLng) {
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.isPublic() && !memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        log.info("[TripService.getTrip] tripId={} userLat={} userLng={}", tripId, userLat, userLng);
        return mapToResponse(trip, userLat, userLng);
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
                .lat(req.getLat())
                .lng(req.getLng())
                .imageUrl(req.getImageUrl())
                .build();
        log.info("Saving stop: name={} lat={} lng={} imageUrl={}", stop.getName(), stop.getLat(), stop.getLng(), stop.getImageUrl());
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
        return mapToResponse(t, null, null);
    }

    private TripResponse mapToResponse(Trip t, Double userLat, Double userLng) {
        List<TripStopResponse> stops = t.getStops().stream()
                .sorted(java.util.Comparator.comparingDouble(TripStop::getOrderIdx))
                .map(this::mapStopToResponse)
                .toList();

        // Calculate total distance and travel time between consecutive stops with coordinates
        double totalDistanceKm = 0.0;
        int totalTravelTimeMin = 0;

        for (int i = 1; i < stops.size(); i++) {
            TripStopResponse prev = stops.get(i - 1);
            TripStopResponse curr = stops.get(i);
            if (prev.getLat() != null && prev.getLng() != null
                    && curr.getLat() != null && curr.getLng() != null) {
                double segmentDistance = distanceCalculator.calculateDistance(
                        prev.getLat(), prev.getLng(), curr.getLat(), curr.getLng());
                totalDistanceKm += segmentDistance;

                double segmentTravelTime = distanceCalculator.estimateTravelTime(
                        prev.getLat(), prev.getLng(), curr.getLat(), curr.getLng());
                totalTravelTimeMin += (int) Math.round(segmentTravelTime);
            }
        }

        // Calculate distance from origin to the first stop
        Double distanceFromUserKm = null;
        Integer travelTimeFromUserMin = null;
        
        if (!stops.isEmpty()) {
            TripStopResponse firstStop = stops.get(0);
            if (firstStop.getLat() != null && firstStop.getLng() != null) {
                // Determine origin coordinates: use user's real location if provided, else fallback to trip origin city
                double originLat, originLng;
                boolean isUserLocation = false;
                
                if (userLat != null && userLng != null) {
                    originLat = userLat;
                    originLng = userLng;
                    isUserLocation = true;
                } else {
                    try {
                        double[] cityCoords = DistanceCalculator.getCityCoords(t.getOrigin());
                        originLat = cityCoords[0];
                        originLng = cityCoords[1];
                    } catch (Exception e) {
                        log.warn("Failed to get coordinates for city: {}, skipping first leg calculation", t.getOrigin(), e);
                        originLat = Double.NaN;
                        originLng = Double.NaN;
                    }
                }

                // Only calculate first leg if we have valid origin coordinates
                if (!Double.isNaN(originLat) && !Double.isNaN(originLng)) {
                    double originToFirstDist = distanceCalculator.calculateDistance(
                            originLat, originLng, firstStop.getLat(), firstStop.getLng());
                    double originToFirstTime = distanceCalculator.estimateTravelTime(
                            originLat, originLng, firstStop.getLat(), firstStop.getLng());
                    
                    // Add the first leg to the totals
                    totalDistanceKm += originToFirstDist;
                    totalTravelTimeMin += (int) Math.round(originToFirstTime);

                    // If user provided their coordinates, also set the dedicated user distance fields
                    if (isUserLocation) {
                        distanceFromUserKm = Math.round(originToFirstDist * 100.0) / 100.0;
                        travelTimeFromUserMin = (int) Math.round(originToFirstTime);
                        log.info("[mapToResponse] distanceFromUserKm={} travelTimeFromUserMin={}",
                                distanceFromUserKm, travelTimeFromUserMin);
                    } else {
                        log.info("[mapToResponse] Added origin city -> first stop leg: dist={} time={}", 
                                originToFirstDist, originToFirstTime);
                    }
                } else {
                    log.warn("[mapToResponse] Origin city coordinates could not be determined");
                }
            } else {
                log.warn("[mapToResponse] First stop has no lat/lng — cannot calculate first leg distance");
            }
        }

        return TripResponse.builder()
            .id(t.getId()).ownerId(t.getOwnerId()).title(t.getTitle())
            .origin(t.getOrigin()).destination(t.getDestination())
            .startDate(t.getStartDate()).endDate(t.getEndDate())
            .status(t.getStatus()).isPublic(t.isPublic())
            .coverUrl(t.getCoverUrl()).description(t.getDescription())
            .stopsCount(t.getStops().size()).membersCount(t.getMembers().size())
            .stops(stops)
            .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
            .totalDistanceKm(totalDistanceKm > 0 ? Math.round(totalDistanceKm * 100.0) / 100.0 : null)
            .totalTravelTimeMin(totalTravelTimeMin > 0 ? totalTravelTimeMin : null)
            .distanceFromUserKm(distanceFromUserKm)
            .travelTimeFromUserMin(travelTimeFromUserMin)
            .build();
    }

    private TripStopResponse mapStopToResponse(TripStop s) {
        return TripStopResponse.builder()
            .id(s.getId()).placeId(s.getPlaceId()).orderIdx(s.getOrderIdx())
            .name(s.getName()).arrivalAt(s.getArrivalAt())
            .durationMin(s.getDurationMin()).notes(s.getNotes())
            .completedAt(s.getCompletedAt())
            .lat(s.getLat()).lng(s.getLng())
            .imageUrl(s.getImageUrl())
            .build();
    }

}