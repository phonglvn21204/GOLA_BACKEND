package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.map.AutocompleteSuggestion;
import com.gola.dto.map.PlaceDetail;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {
    private static final ZoneId TRIP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern SIMPLE_HOURS = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*[-–]\\s*(\\d{1,2})(?::(\\d{2}))?");
    private final TripRepository      tripRepo;
    private final TripStopRepository  stopRepo;
    private final TripMemberRepository memberRepo;
    private final TripSessionRepository sessionRepo;
    private final TripShareRepository shareRepo;
    private final ProfileRepository profileRepo;
    private final TripMemoryRepository memoryRepo;
    private final QuestProgressRepository questProgressRepo;
    private final DistanceCalculator distanceCalculator;
    private final PlaceEnrichmentService placeEnrichmentService;
    private final PlaceService placeService;
    private final NotificationService notificationService;

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
        notifyTripUser(userId, "TRIP_CREATED", "Chuyến đi mới", "Chuyến đi mới đã được tạo.", trip.getId(), "/plans");
        return mapToResponse(trip, userId, null, null);
    }

    public PageResponse<TripResponse> listMyTrips(UUID userId, Pageable pageable) {
        return listMyTrips(userId, pageable, null);
    }

    public PageResponse<TripResponse> listMyTrips(UUID userId, Pageable pageable, String statusFilter) {
        List<TripStatus> statuses = resolveTripStatusFilter(statusFilter);
        var trips = statuses == null
                ? tripRepo.findAllForUser(userId, pageable)
                : tripRepo.findAllForUserByStatusIn(userId, statuses.stream().map(Enum::name).toList(), pageable);
        return new PageResponse<>(trips
                .map(trip -> mapToResponse(trip, userId, null, null)));
    }

    public TripResponse getTrip(UUID tripId, UUID userId, Double userLat, Double userLng) {
        var trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.isPublic() && !memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        log.info("[TripService.getTrip] tripId={} userLat={} userLng={}", tripId, userLat, userLng);
        return mapToResponse(trip, userId, userLat, userLng, true);
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
        return mapToResponse(tripRepo.save(trip), userId, null, null);
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
        Trip trip = getEditableTrip(tripId, userId);
        double orderIdx;

        if (req.getOrderIdx() != null && req.getOrderIdx() > 0) {
            // Nếu FE truyền orderIdx hợp lệ, dùng giá trị đó
            orderIdx = req.getOrderIdx();
        } else {
            // Nếu không, lấy max + 1000 để thêm cuối
            double maxIdx = stopRepo.findMaxOrderIdx(tripId);
            orderIdx = maxIdx + 1000;
        }

        enrichAddStopRequest(req, trip);
        sanitizeShortStopFields(req);

        var stop = TripStop.builder()
                .trip(tripRepo.getReferenceById(tripId))
                .placeId(req.getPlaceId())
                .name(req.getName())
                .orderIdx(orderIdx)
                .arrivalAt(req.getArrivalAt())
                .durationMin(req.getDurationMin())
                .estimatedCost(req.getEstimatedCost())
                .category(req.getCategory())
                .notes(req.getNotes())
                .lat(req.getLat())
                .lng(req.getLng())
                .imageUrl(req.getImageUrl())
                .rating(req.getRating())
                .reviewCount(req.getReviewCount())
                .imageSource(req.getImageSource())
                .placeAddress(req.getPlaceAddress())
                .dataSource(req.getDataSource())
                .enrichmentStatus(req.getEnrichmentStatus())
                .hasRealPhoto(req.getHasRealPhoto())
                .hasRealCoordinates(req.getHasRealCoordinates())
                .hasOpeningHours(req.getHasOpeningHours())
                .openingHoursText(req.getOpeningHoursText())
                .openNow(req.getOpenNow())
                .businessStatus(req.getBusinessStatus())
                .nextOpenCloseText(req.getNextOpenCloseText())
                .scheduledOpenStatus(req.getScheduledOpenStatus())
                .placeDataRejectReason(req.getPlaceDataRejectReason())
                .providerTitle(req.getProviderTitle())
                .providerId(req.getProviderId())
                .providerSource(req.getProviderSource())
                .systemStop(req.getSystemStop())
                .build();
        log.info("Saving stop: name={} lat={} lng={} imageSource={} hasImage={} rating={} reviews={}",
                stop.getName(),
                stop.getLat(),
                stop.getLng(),
                stop.getImageSource(),
                stop.getImageUrl() != null,
                stop.getRating(),
                stop.getReviewCount());
        return mapStopToResponse(stopRepo.save(stop));
    }

    private void sanitizeShortStopFields(AddStopRequest req) {
        if (req == null) return;
        req.setName(limit(req.getName(), 180));
        req.setCategory(limit(req.getCategory(), 50));
        req.setDataSource(limit(req.getDataSource(), 50));
        req.setImageSource(limit(req.getImageSource(), 50));
        req.setProviderSource(limit(req.getProviderSource(), 50));
        req.setEnrichmentStatus(limit(req.getEnrichmentStatus(), 50));
        req.setBusinessStatus(limit(req.getBusinessStatus(), 80));
        req.setScheduledOpenStatus(limit(req.getScheduledOpenStatus(), 80));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private void enrichAddStopRequest(AddStopRequest req, Trip trip) {
        boolean hotelLikeRequest = isHotelLikeRequest(req);
        if ((req.getImageSource() != null && !hotelLikeRequest) || req.getName() == null || req.getName().isBlank()) {
            enforceTripCoordinateTrust(req, trip);
            fillStopQualityDefaults(req);
            return;
        }

        if (isSystemStopCategory(req.getCategory()) && !hotelLikeRequest) {
            req.setSystemStop(true);
            req.setEnrichmentStatus("SYSTEM_STOP");
            req.setImageUrl(null);
            req.setImageSource("CATEGORY_FALLBACK");
            req.setRating(null);
            req.setReviewCount(null);
            req.setOpenNow(null);
            req.setOpeningHoursText(null);
            enforceTripCoordinateTrust(req, trip);
            fillStopQualityDefaults(req);
            return;
        }

        try {
            boolean hadTrustedCoordinates = hasValidCoordinate(req.getLat(), req.getLng())
                    && isTrustedCoordinateSource(req.getDataSource());
            PlaceDetail detail = placeEnrichmentService.enrichForStop(
                    req.getName(),
                    trip.getDestination(),
                    req.getCategory(),
                    req.getLat(),
                    req.getLng(),
                    hotelLikeRequest ? PlaceEnrichmentService.SerpApiBudget.of(4) : PlaceEnrichmentService.SerpApiBudget.single()
            );
            if (detail == null) return;

            if (hotelLikeRequest && hotelDisplayName(detail) != null) {
                req.setName("Nhận phòng tại " + hotelDisplayName(detail));
                req.setCategory("HOTEL");
                req.setSystemStop(false);
                if (!"SERPAPI".equalsIgnoreCase(String.valueOf(detail.getImageSource()))
                        || detail.getImageUrl() == null
                        || detail.getRating() == null) {
                    req.setPlaceDataRejectReason("HOTEL_MISSING_PHOTO_OR_RATING");
                }
            }

            if (req.getImageUrl() == null) req.setImageUrl(detail.getImageUrl());
            if (req.getRating() == null) req.setRating(detail.getRating());
            if (req.getReviewCount() == null) req.setReviewCount(detail.getReviewCount());
            if (req.getEstimatedCost() == null && detail.getEstimatedCost() != null) {
                req.setEstimatedCost(detail.getEstimatedCost());
            }
            if (req.getPlaceAddress() == null) {
                req.setPlaceAddress(detail.getPlaceAddress() != null ? detail.getPlaceAddress() : detail.getAddress());
            }
            if (!hasValidCoordinate(req.getLat(), req.getLng()) && hasValidCoordinate(detail.getLat(), detail.getLng())) {
                req.setLat(detail.getLat());
                req.setLng(detail.getLng());
                hadTrustedCoordinates = false;
            }
            req.setImageSource(detail.getImageSource() != null ? detail.getImageSource() : "CATEGORY_FALLBACK");
            if (!hadTrustedCoordinates && detail.getDataSource() != null) {
                req.setDataSource(detail.getDataSource());
            }
            req.setOpeningHoursText(detail.getOpeningHours());
            req.setOpenNow(detail.getOpenNow());
            req.setBusinessStatus(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getBusinessStatus());
            req.setNextOpenCloseText(detail.getNextOpenCloseText());
            req.setHasOpeningHours(detail.getHasOpeningHours());
            req.setEnrichmentStatus(detail.getEnrichmentStatus());
            req.setHasRealPhoto(detail.getHasRealPhoto());
            req.setPlaceDataRejectReason(detail.getRejectedReason());
            if (req.getProviderTitle() == null) req.setProviderTitle(detail.getProviderTitle() != null ? detail.getProviderTitle() : detail.getName());
            if (req.getProviderId() == null) req.setProviderId(detail.getProviderId());
            if (req.getProviderSource() == null) req.setProviderSource(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getDataSource());
        } catch (Exception e) {
            log.warn("Manual stop enrichment failed for '{}': {}", req.getName(), e.getMessage());
            if (req.getImageSource() == null) {
                req.setImageSource("CATEGORY_FALLBACK");
            }
        }
        enforceTripCoordinateTrust(req, trip);
        fillStopQualityDefaults(req);
    }

    private void fillStopQualityDefaults(AddStopRequest req) {
        boolean systemStop = Boolean.TRUE.equals(req.getSystemStop()) || isSystemStopCategory(req.getCategory());
        req.setSystemStop(systemStop);
        req.setDataSource(canonicalDataSource(req.getDataSource()));
        req.setHasRealPhoto(hasRealPlacePhoto(req.getImageUrl(), req.getImageSource(), req.getCategory(), req.getHasRealPhoto()));
        req.setHasRealCoordinates(hasValidCoordinate(req.getLat(), req.getLng()) && isTrustedCoordinateSource(req.getDataSource()));
        req.setHasOpeningHours(Boolean.TRUE.equals(req.getHasOpeningHours())
                || (req.getOpeningHoursText() != null && !req.getOpeningHoursText().isBlank()));
        if (req.getEnrichmentStatus() == null || req.getEnrichmentStatus().isBlank()) {
            if (systemStop) {
            req.setEnrichmentStatus("SYSTEM_STOP");
            } else if (Boolean.TRUE.equals(req.getHasRealCoordinates())
                    && (Boolean.TRUE.equals(req.getHasRealPhoto())
                    || req.getRating() != null
                    || Boolean.TRUE.equals(req.getHasOpeningHours())
                    || req.getPlaceAddress() != null)) {
                req.setEnrichmentStatus("ENRICHED");
            } else if (Boolean.TRUE.equals(req.getHasRealCoordinates()) || req.getPlaceAddress() != null) {
                req.setEnrichmentStatus(Boolean.TRUE.equals(req.getHasRealCoordinates()) ? "PARTIAL_WITH_COORDINATES" : "PARTIAL");
            } else {
                req.setEnrichmentStatus("FAILED");
            }
        }
        if (req.getImageSource() == null || req.getImageSource().isBlank()) {
            req.setImageSource(Boolean.TRUE.equals(req.getHasRealPhoto()) ? req.getDataSource() : "CATEGORY_FALLBACK");
        }
        if (systemStop) {
            req.setHasRealCoordinates(false);
            req.setHasRealPhoto(false);
            req.setScheduledOpenStatus("NOT_APPLICABLE");
        } else {
            req.setScheduledOpenStatus(resolveScheduledOpenStatus(req.getOpeningHoursText(), req.getOpenNow(), req.getArrivalAt()));
        }
    }

    @Transactional
    public TripStopResponse updateStop(UUID tripId, UUID stopId, UUID userId, AddStopRequest req) {
        Trip trip = getEditableTrip(tripId, userId);
        TripStop stop = stopRepo.findById(stopId).orElseThrow(() -> GolaException.notFound("Stop"));
        if (!stop.getTrip().getId().equals(tripId)) throw GolaException.forbidden();

        enrichAddStopRequest(req, trip);
        applyRequestToStop(stop, req);
        fillStopQualityDefaults(stop);
        return mapStopToResponse(stopRepo.save(stop));
    }

    @Transactional
    public List<TripStopResponse> reorderStops(UUID tripId, UUID userId, ReorderStopsRequest req) {
        getEditableTrip(tripId, userId);

        Map<UUID, TripStop> stopById = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId).stream()
                .collect(Collectors.toMap(TripStop::getId, Function.identity()));

        List<TripStop> changedStops = new ArrayList<>();
        for (ReorderStopsRequest.StopOrder item : req.getStops()) {
            TripStop stop = stopById.get(item.getId());
            if (stop == null) {
                throw GolaException.badRequest("Stop does not belong to this trip");
            }
            stop.setOrderIdx(item.getOrderIdx());
            if (item.getArrivalAt() != null) {
                stop.setArrivalAt(item.getArrivalAt());
            }
            changedStops.add(stop);
        }

        stopRepo.saveAll(changedStops);
        return stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId).stream()
                .map(this::mapStopToResponse)
                .toList();
    }

    @Transactional
    public TripStopResponse refreshStopPlaceData(UUID tripId, UUID stopId, UUID userId) {
        Trip trip = getEditableTrip(tripId, userId);
        TripStop stop = stopRepo.findById(stopId).orElseThrow(() -> GolaException.notFound("Stop"));
        if (!stop.getTrip().getId().equals(tripId)) throw GolaException.forbidden();

        refreshStopPlaceData(trip, stop);
        return mapStopToResponse(stopRepo.save(stop));
    }

    @Transactional
    public List<TripStopResponse> refreshMissingStopPlaceData(UUID tripId, UUID userId) {
        Trip trip = getEditableTrip(tripId, userId);
        List<TripStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        List<TripStop> changedStops = new ArrayList<>();

        for (TripStop stop : stops) {
            if (!shouldRepairStop(trip, stop)) continue;
            repairStopPlaceData(trip, stop);
            changedStops.add(stop);
        }

        if (!changedStops.isEmpty()) {
            stopRepo.saveAll(changedStops);
        }
        return stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId).stream()
                .map(this::mapStopToResponse)
                .toList();
    }

    @Transactional
    public RepairPlaceDataResponse repairTripPlaceData(UUID tripId, UUID userId) {
        Trip trip = getEditableTrip(tripId, userId);
        List<TripStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        List<TripStop> changedStops = new ArrayList<>();
        List<String> failedStopNames = new ArrayList<>();
        List<String> rejectedReasons = new ArrayList<>();
        int repairedCount = 0;
        int repairedCoordinatesCount = 0;
        int repairedImagesCount = 0;
        int downgradedBadWikimediaImagesCount = 0;
        int serpApiAcceptedCount = 0;
        int goongFallbackCount = 0;
        int skippedSystemStopCount = 0;
        int attempts = 0;
        int maxAttempts = 8;

        for (TripStop stop : stops) {
            if (isSystemStop(stop)) {
                skippedSystemStopCount++;
                fillStopQualityDefaults(stop);
                changedStops.add(stop);
                continue;
            }
            if (!shouldRepairStop(trip, stop)) continue;
            if (attempts >= maxAttempts) {
                if (!hasTrustedCoordinates(stop, trip)) {
                    failedStopNames.add(stop.getName());
                }
                continue;
            }

            attempts++;
            boolean beforeTrusted = hasTrustedCoordinates(stop, trip);
            boolean beforeRealPhoto = Boolean.TRUE.equals(stop.getHasRealPhoto());
            boolean beforeBadWikimedia = isBadStoredImage(stop);
            repairStopPlaceData(trip, stop);
            boolean afterTrusted = hasTrustedCoordinates(stop, trip);
            boolean afterRealPhoto = Boolean.TRUE.equals(stop.getHasRealPhoto());
            changedStops.add(stop);
            if (!beforeTrusted && afterTrusted) {
                repairedCoordinatesCount++;
                repairedCount++;
            }
            if (!beforeRealPhoto && afterRealPhoto) {
                repairedImagesCount++;
                repairedCount++;
            }
            if (beforeBadWikimedia && !"WIKIMEDIA".equalsIgnoreCase(String.valueOf(stop.getImageSource()))) {
                downgradedBadWikimediaImagesCount++;
            }
            if (!afterTrusted) {
                failedStopNames.add(stop.getName());
                if (stop.getPlaceDataRejectReason() != null && !stop.getPlaceDataRejectReason().isBlank()) {
                    rejectedReasons.add(stop.getName() + ": " + stop.getPlaceDataRejectReason());
                }
            }
            if ("SERPAPI".equalsIgnoreCase(String.valueOf(stop.getDataSource()))
                    || "SERPAPI".equalsIgnoreCase(String.valueOf(stop.getImageSource()))) {
                serpApiAcceptedCount++;
            }
            if ("GOONG".equalsIgnoreCase(String.valueOf(stop.getDataSource())) && afterTrusted) {
                goongFallbackCount++;
            }
        }

        if (!changedStops.isEmpty()) {
            stopRepo.saveAll(changedStops);
        }

        long stillMissingCount = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId).stream()
                .filter(stop -> !isSystemStop(stop))
                .filter(stop -> !hasTrustedCoordinates(stop, trip))
                .count();

        return RepairPlaceDataResponse.builder()
                .repairedCount(repairedCount)
                .repairedCoordinatesCount(repairedCoordinatesCount)
                .repairedImagesCount(repairedImagesCount)
                .stillMissingCount((int) stillMissingCount)
                .stillMissingCoordinatesCount((int) stillMissingCount)
                .downgradedBadWikimediaImagesCount(downgradedBadWikimediaImagesCount)
                .serpApiAcceptedCount(serpApiAcceptedCount)
                .goongFallbackCount(goongFallbackCount)
                .skippedSystemStopCount(skippedSystemStopCount)
                .failedStopNames(failedStopNames)
                .rejectedReasons(rejectedReasons)
                .build();
    }

    private void applyRequestToStop(TripStop stop, AddStopRequest req) {
        stop.setPlaceId(req.getPlaceId());
        stop.setName(req.getName());
        if (req.getOrderIdx() != null && req.getOrderIdx() > 0) {
            stop.setOrderIdx(req.getOrderIdx());
        }
        stop.setArrivalAt(req.getArrivalAt());
        stop.setDurationMin(req.getDurationMin());
        stop.setEstimatedCost(req.getEstimatedCost());
        stop.setCategory(req.getCategory());
        stop.setNotes(req.getNotes());
        stop.setLat(req.getLat());
        stop.setLng(req.getLng());
        stop.setImageUrl(req.getImageUrl());
        stop.setRating(req.getRating());
        stop.setReviewCount(req.getReviewCount());
        stop.setImageSource(req.getImageSource());
        stop.setPlaceAddress(req.getPlaceAddress());
        stop.setDataSource(req.getDataSource());
        stop.setEnrichmentStatus(req.getEnrichmentStatus());
        stop.setHasRealPhoto(req.getHasRealPhoto());
        stop.setHasRealCoordinates(req.getHasRealCoordinates());
        stop.setHasOpeningHours(req.getHasOpeningHours());
        stop.setOpeningHoursText(req.getOpeningHoursText());
        stop.setOpenNow(req.getOpenNow());
        stop.setBusinessStatus(req.getBusinessStatus());
        stop.setNextOpenCloseText(req.getNextOpenCloseText());
        stop.setScheduledOpenStatus(req.getScheduledOpenStatus());
        stop.setPlaceDataRejectReason(req.getPlaceDataRejectReason());
        stop.setSystemStop(req.getSystemStop());
    }

    private void refreshStopPlaceData(Trip trip, TripStop stop) {
        if (isSystemStop(stop) && !isHotelLikeStop(stop)) {
            fillStopQualityDefaults(stop);
            return;
        }

        repairStopPlaceData(trip, stop);
    }

    private void repairStopPlaceData(Trip trip, TripStop stop) {
        if (isSystemStop(stop)) {
            fillStopQualityDefaults(stop);
            return;
        }

        String query = cleanDestinationAwareQuery(stop, trip.getDestination());
        boolean oldTrusted = hasTrustedCoordinates(stop, trip);
        log.info("Repair place data: stop='{}' oldSource={} oldTrusted={} oldHasRealCoordinates={} query='{}'",
                stop.getName(), stop.getDataSource(), oldTrusted, stop.getHasRealCoordinates(), query);

        try {
            PlaceDetail detail = placeEnrichmentService.enrichForStop(
                    query,
                    trip.getDestination(),
                    stop.getCategory(),
                    stop.getLat(),
                    stop.getLng(),
                    isHotelLikeStop(stop) ? PlaceEnrichmentService.SerpApiBudget.of(4) : PlaceEnrichmentService.SerpApiBudget.single()
            );

            if (detail != null) {
                applyPlaceDetail(stop, detail);
                if (isBadStoredImage(stop)) {
                    stop.setImageUrl(null);
                    stop.setImageSource("CATEGORY_FALLBACK");
                    stop.setHasRealPhoto(false);
                }
            } else if (stop.getEnrichmentStatus() == null || stop.getEnrichmentStatus().isBlank()) {
                stop.setEnrichmentStatus("FAILED");
            }

            if (!hasTrustedCoordinates(stop, trip)) {
                PlaceDetail goongDetail = resolveWithGoong(query);
                if (goongDetail != null && hasValidCoordinate(goongDetail.getLat(), goongDetail.getLng())) {
                    stop.setLat(goongDetail.getLat());
                    stop.setLng(goongDetail.getLng());
                    stop.setDataSource("GOONG");
                    if (stop.getPlaceAddress() == null || stop.getPlaceAddress().isBlank()) {
                        stop.setPlaceAddress(goongDetail.getPlaceAddress() != null ? goongDetail.getPlaceAddress() : goongDetail.getAddress());
                    }
                    log.info("Repair accepted GOONG fallback coords for '{}' providerName='{}' address='{}'",
                            stop.getName(), goongDetail.getName(), goongDetail.getAddress());
                }
            }
        } catch (Exception e) {
            log.warn("Refresh stop place data failed for '{}': {}", stop.getName(), e.getMessage());
            if (stop.getEnrichmentStatus() == null || stop.getEnrichmentStatus().isBlank()) {
                stop.setEnrichmentStatus("FAILED");
            }
        }
        enforceTripCoordinateTrust(stop, trip);
        fillStopQualityDefaults(stop);
        log.info("Repair result: stop='{}' source={} hasRealCoordinates={} status={} accepted={}",
                stop.getName(),
                stop.getDataSource(),
                stop.getHasRealCoordinates(),
                stop.getEnrichmentStatus(),
                hasTrustedCoordinates(stop, trip));
    }

    private void applyPlaceDetail(TripStop stop, PlaceDetail detail) {
        boolean detailSerpImage = "SERPAPI".equalsIgnoreCase(String.valueOf(detail.getImageSource()));
        boolean currentBadImage = isBadStoredImage(stop);
        if (detail.getImageUrl() != null && (stop.getImageUrl() == null || detailSerpImage || currentBadImage)) {
            stop.setImageUrl(detail.getImageUrl());
        }
        if (isHotelLikeStop(stop) && hotelDisplayName(detail) != null) {
            stop.setName("Nhận phòng tại " + hotelDisplayName(detail));
            stop.setCategory("HOTEL");
            stop.setSystemStop(false);
            if (!detailSerpImage || detail.getImageUrl() == null || detail.getRating() == null) {
                stop.setPlaceDataRejectReason("HOTEL_MISSING_PHOTO_OR_RATING");
            }
        }
        if (stop.getRating() == null) stop.setRating(detail.getRating());
        if (stop.getReviewCount() == null) stop.setReviewCount(detail.getReviewCount());
        if (stop.getEstimatedCost() == null && detail.getEstimatedCost() != null) {
            stop.setEstimatedCost(detail.getEstimatedCost());
        }
        if (stop.getPlaceAddress() == null) {
            stop.setPlaceAddress(detail.getPlaceAddress() != null ? detail.getPlaceAddress() : detail.getAddress());
        }
        boolean detailTrusted = hasValidCoordinate(detail.getLat(), detail.getLng())
                && isTrustedCoordinateSource(detail.getDataSource());
        boolean currentTrusted = hasValidCoordinate(stop.getLat(), stop.getLng())
                && isTrustedCoordinateSource(stop.getDataSource());
        boolean appliedDetailCoordinates = (!hasValidCoordinate(stop.getLat(), stop.getLng()) || (!currentTrusted && detailTrusted))
                && hasValidCoordinate(detail.getLat(), detail.getLng());
        if (appliedDetailCoordinates) {
            stop.setLat(detail.getLat());
            stop.setLng(detail.getLng());
        }
        stop.setImageSource(detail.getImageSource() != null ? detail.getImageSource() : stop.getImageSource());
        if ((appliedDetailCoordinates && detailTrusted) || (!isTrustedCoordinateSource(stop.getDataSource()) && detail.getDataSource() != null)) {
            stop.setDataSource(canonicalDataSource(detail.getDataSource()));
        }
        stop.setOpeningHoursText(detail.getOpeningHours());
        stop.setOpenNow(detail.getOpenNow());
        stop.setBusinessStatus(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getBusinessStatus());
        stop.setNextOpenCloseText(detail.getNextOpenCloseText());
        stop.setHasOpeningHours(detail.getHasOpeningHours());
        stop.setEnrichmentStatus(detail.getEnrichmentStatus());
        stop.setHasRealPhoto(detail.getHasRealPhoto());
        stop.setPlaceDataRejectReason(detail.getRejectedReason());
        if (stop.getProviderTitle() == null) stop.setProviderTitle(detail.getProviderTitle() != null ? detail.getProviderTitle() : detail.getName());
        if (stop.getProviderId() == null) stop.setProviderId(detail.getProviderId());
        if (stop.getProviderSource() == null) stop.setProviderSource(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getDataSource());
    }

    private boolean hasUsefulPlaceData(TripStop stop) {
        return hasValidCoordinate(stop.getLat(), stop.getLng())
                && (Boolean.TRUE.equals(stop.getHasRealPhoto())
                || stop.getRating() != null
                || Boolean.TRUE.equals(stop.getHasOpeningHours())
                || stop.getPlaceAddress() != null);
    }

    private boolean isHotelLikeStop(TripStop stop) {
        String category = stopCategory(stop.getCategory());
        String name = stop.getName() == null ? "" : normalizeText(stop.getName());
        if (name.matches(".*\\b(tra phong|checkout|di chuyen den khach san|nghi ngoi tai khach san)\\b.*")) {
            return false;
        }
        return Set.of("HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING", "CHECKIN").contains(category)
                || name.matches(".*\\b(nhan phong|khach san|hotel|homestay|resort)\\b.*");
    }

    private boolean isHotelLikeRequest(AddStopRequest req) {
        String category = stopCategory(req.getCategory());
        String name = req.getName() == null ? "" : normalizeText(req.getName());
        if (name.matches(".*\\b(tra phong|checkout|di chuyen den khach san|nghi ngoi tai khach san)\\b.*")) {
            return false;
        }
        return Set.of("HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING", "CHECKIN").contains(category)
                || name.matches(".*\\b(nhan phong|khach san|hotel|homestay|resort)\\b.*");
    }

    private String hotelDisplayName(PlaceDetail detail) {
        if (detail == null) return null;
        for (String value : List.of(detail.getName(), firstAddressSegment(detail.getPlaceAddress()), firstAddressSegment(detail.getAddress()))) {
            String text = stringValue(value);
            if (isSpecificHotelName(text)) {
                return text;
            }
        }
        return null;
    }

    private String firstAddressSegment(String address) {
        String text = stringValue(address);
        if (text == null) return null;
        String[] parts = text.split(",");
        return parts.length == 0 ? text : parts[0].trim();
    }

    private boolean isSpecificHotelName(String value) {
        String text = value == null ? "" : normalizeText(value);
        if (text.isBlank()) return false;
        if (!text.matches(".*\\b(hotel|resort|homestay|villa|khach san|hostel|inn|motel|apartment|apartments)\\b.*")) {
            return false;
        }
        return !text.matches(".*\\b(nhan phong|goi y|da chon|khu bai sau|gan trung tam|khach san vung tau|hotel vung tau|khach san bai sau|hotel back beach)\\b.*");
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private boolean shouldRepairStop(Trip trip, TripStop stop) {
        if (isSystemStop(stop)) return false;
        String source = canonicalDataSource(stop.getDataSource());
        boolean suspiciousSource = source == null
                || source.isBlank()
                || List.of("GEMINI", "AI_SYSTEM", "FALLBACK", "UNKNOWN", "WIKIMEDIA_IMAGE_ONLY", "WIKIMEDIA", "CITY_CENTER", "ENRICHMENT", "NOMINATIM", "NONE").contains(source);
        boolean missingSerpApiMetadata = !"SERPAPI".equalsIgnoreCase(String.valueOf(stop.getImageSource()))
                && (!Boolean.TRUE.equals(stop.getHasRealPhoto())
                || stop.getImageUrl() == null
                || stop.getImageUrl().isBlank()
                || stop.getRating() == null
                || stop.getReviewCount() == null);
        return !hasTrustedCoordinates(stop, trip) || suspiciousSource || isBadStoredImage(stop) || missingSerpApiMetadata;
    }

    private boolean hasTrustedCoordinates(TripStop stop, Trip trip) {
        return hasValidCoordinate(stop.getLat(), stop.getLng())
                && Boolean.TRUE.equals(stop.getHasRealCoordinates())
                && isTrustedCoordinateSource(stop.getDataSource())
                && isCoordinateNearDestination(trip.getDestination(), stop.getLat(), stop.getLng(), stop.getCategory(), stop.getPlaceAddress());
    }

    private PlaceDetail resolveWithGoong(String query) {
        try {
            List<AutocompleteSuggestion> suggestions = placeService.searchAutocomplete(query);
            if (suggestions.isEmpty()) {
                log.info("Repair Goong autocomplete returned no result for '{}'", query);
                return null;
            }
            PlaceDetail detail = placeService.getPlaceDetail(suggestions.get(0).getPlaceId());
            if (detail != null && hasValidCoordinate(detail.getLat(), detail.getLng())) {
                return detail;
            }
        } catch (Exception e) {
            log.warn("Repair Goong lookup failed for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    private String cleanDestinationAwareQuery(TripStop stop, String destination) {
        String name = stop.getName() == null ? "" : stop.getName().trim();
        String dest = destination == null ? "" : destination.trim();
        String category = stopCategory(stop.getCategory());
        boolean generic = isGenericPlaceName(name);

        return switch (category) {
            case "FOOD" -> generic ? joinQuery("nhà hàng địa phương nổi tiếng", dest) : joinQuery(name, dest);
            case "CAFE" -> generic ? joinQuery("quán cafe view biển", dest) : joinQuery("quán cafe", name, dest);
            case "MARKET" -> generic ? joinQuery("chợ đêm ẩm thực", dest) : joinQuery(name, dest);
            case "HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING" -> generic ? joinQuery("khách sạn", dest) : joinQuery(name, dest);
            case "SIGHTSEEING", "SIGHT", "ATTRACTION" -> generic ? joinQuery("địa điểm tham quan", dest) : joinQuery(name, dest);
            default -> joinQuery(name, dest);
        };
    }

    private String buildDestinationAwareQuery(TripStop stop, String destination) {
        String name = stop.getName() == null ? "" : stop.getName().trim();
        String dest = destination == null ? "" : destination.trim();
        String category = stopCategory(stop.getCategory());
        boolean generic = isGenericPlaceName(name);

        return switch (category) {
            case "FOOD" -> generic ? joinQuery("nhà hàng", dest) : joinQuery(name, dest);
            case "CAFE" -> generic ? joinQuery("quán cafe view biển", dest) : joinQuery("quán cafe", name, dest);
            case "HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING" -> generic ? joinQuery("khách sạn", dest) : joinQuery(name, dest);
            case "SIGHTSEEING", "SIGHT", "ATTRACTION" -> generic ? joinQuery("địa điểm tham quan", dest) : joinQuery(name, dest);
            default -> joinQuery(name, dest);
        };
    }

    private String joinQuery(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) values.add(part.trim());
        }
        return String.join(" ", values).trim();
    }

    private boolean isGenericPlaceName(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) return true;
        return normalized.matches(".*\\b(an trua|an toi|an sang|ca phe|cafe|nhan phong|tra phong|gui hanh ly|di chuyen|mua dac san)\\b.*");
    }

    private String stopCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String value = category.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "RESTAURANT", "DINING", "BREAKFAST", "LUNCH", "DINNER" -> "FOOD";
            case "COFFEE" -> "CAFE";
            case "SHOPPING", "NIGHT_MARKET" -> "MARKET";
            case "STAY", "CHECKIN", "CHECK_IN" -> "HOTEL";
            case "SIGHT", "ATTRACTION", "PLACE", "VISIT" -> "SIGHTSEEING";
            default -> value;
        };
    }

    private boolean isSystemStop(TripStop stop) {
        if (isHotelLikeStop(stop)) return false;
        return Boolean.TRUE.equals(stop.getSystemStop()) || isSystemStopCategory(stop.getCategory());
    }

    private boolean isSystemStopCategory(String category) {
        if (category == null || category.isBlank()) return false;
        String normalized = category.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "TRANSPORT", "TRAVEL", "TRANSFER", "TRANSIT", "FLIGHT", "BUS", "TRAIN",
                    "TAXI", "MOTORBIKE", "CHECKIN", "CHECK_IN", "CHECKOUT", "CHECK_OUT",
                    "REST", "NOTE", "EMERGENCY", "DROP_LUGGAGE", "BAG_DROP" -> true;
            default -> false;
        };
    }

    private void fillStopQualityDefaults(TripStop stop) {
        boolean systemStop = isSystemStop(stop);
        stop.setSystemStop(systemStop);
        stop.setDataSource(canonicalDataSource(stop.getDataSource()));
        stop.setHasRealPhoto(hasRealPlacePhoto(stop.getImageUrl(), stop.getImageSource(), stop.getCategory(), stop.getHasRealPhoto()));
        stop.setHasRealCoordinates(hasValidCoordinate(stop.getLat(), stop.getLng()) && isTrustedCoordinateSource(stop.getDataSource()));
        stop.setHasOpeningHours(Boolean.TRUE.equals(stop.getHasOpeningHours())
                || (stop.getOpeningHoursText() != null && !stop.getOpeningHoursText().isBlank()));
        if (stop.getEnrichmentStatus() == null || stop.getEnrichmentStatus().isBlank()) {
            if (systemStop) {
                stop.setEnrichmentStatus("SYSTEM_STOP");
            } else if (Boolean.TRUE.equals(stop.getHasRealCoordinates())
                    && (Boolean.TRUE.equals(stop.getHasRealPhoto())
                    || stop.getRating() != null
                    || Boolean.TRUE.equals(stop.getHasOpeningHours())
                    || stop.getPlaceAddress() != null)) {
                stop.setEnrichmentStatus("ENRICHED");
            } else if (Boolean.TRUE.equals(stop.getHasRealCoordinates()) || stop.getPlaceAddress() != null) {
                stop.setEnrichmentStatus(Boolean.TRUE.equals(stop.getHasRealCoordinates()) ? "PARTIAL_WITH_COORDINATES" : "PARTIAL");
            } else {
                stop.setEnrichmentStatus("FAILED");
            }
        }
        if (stop.getImageSource() == null || stop.getImageSource().isBlank()) {
            stop.setImageSource(Boolean.TRUE.equals(stop.getHasRealPhoto()) ? stop.getDataSource() : "CATEGORY_FALLBACK");
        }
        if (systemStop) {
            stop.setImageUrl(null);
            stop.setRating(null);
            stop.setReviewCount(null);
            stop.setOpenNow(null);
            stop.setOpeningHoursText(null);
            stop.setHasOpeningHours(false);
            stop.setImageSource("CATEGORY_FALLBACK");
            stop.setEnrichmentStatus("SYSTEM_STOP");
            stop.setScheduledOpenStatus("NOT_APPLICABLE");
            stop.setPlaceDataRejectReason(null);
        } else {
            stop.setScheduledOpenStatus(resolveScheduledOpenStatus(stop.getOpeningHoursText(), stop.getOpenNow(), stop.getArrivalAt()));
        }
    }

    private boolean hasRealPlacePhoto(String imageUrl, String imageSource, String category, Boolean providerFlag) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.contains("picsum.photos")) return false;
        String source = imageSource == null ? "" : imageSource.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("SERPAPI".equals(source)) return true;
        if ("WIKIMEDIA".equals(source)) {
            String cat = stopCategory(category);
            return Boolean.TRUE.equals(providerFlag)
                    && List.of("SIGHTSEEING", "MARKET", "OTHER").contains(cat)
                    && !isBadImageUrl(imageUrl);
        }
        return false;
    }

    private boolean isBadStoredImage(TripStop stop) {
        if (stop == null) return false;
        String source = stop.getImageSource() == null ? "" : stop.getImageSource().trim().toUpperCase();
        return "WIKIMEDIA".equals(source)
                && (!hasRealPlacePhoto(stop.getImageUrl(), stop.getImageSource(), stop.getCategory(), stop.getHasRealPhoto())
                || List.of("FOOD", "CAFE", "HOTEL", "HOMESTAY", "ACCOMMODATION").contains(stopCategory(stop.getCategory())));
    }

    private boolean isBadImageUrl(String imageUrl) {
        if (imageUrl == null) return true;
        String lower = imageUrl.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".svg")
                || lower.contains("logo")
                || lower.contains("icon")
                || lower.contains("insignia")
                || lower.contains("vector");
    }

    private String resolveScheduledOpenStatus(String openingHoursText, Boolean openNow, Instant arrivalAt) {
        if (arrivalAt == null) return openNow == null ? "UNKNOWN" : "CURRENT_ONLY";
        if (openingHoursText == null || openingHoursText.isBlank()) {
            return openNow == null ? "UNKNOWN" : "CURRENT_ONLY";
        }
        Matcher matcher = SIMPLE_HOURS.matcher(openingHoursText);
        if (!matcher.find()) {
            return openNow == null ? "UNKNOWN" : "CURRENT_ONLY";
        }
        int open = parseHourMinute(matcher.group(1), matcher.group(2));
        int close = parseHourMinute(matcher.group(3), matcher.group(4));
        int arrival = arrivalAt.atZone(TRIP_ZONE).toLocalTime().getHour() * 60
                + arrivalAt.atZone(TRIP_ZONE).toLocalTime().getMinute();
        boolean openAtScheduled = close >= open
                ? arrival >= open && arrival <= close
                : arrival >= open || arrival <= close;
        return openAtScheduled ? "LIKELY_OPEN" : "POSSIBLY_CLOSED";
    }

    private int parseHourMinute(String hour, String minute) {
        int h = Math.max(0, Math.min(23, Integer.parseInt(hour)));
        int m = minute == null || minute.isBlank() ? 0 : Math.max(0, Math.min(59, Integer.parseInt(minute)));
        return h * 60 + m;
    }

    private void enforceTripCoordinateTrust(AddStopRequest req, Trip trip) {
        if (Boolean.TRUE.equals(req.getSystemStop()) || isSystemStopCategory(req.getCategory())) return;
        req.setDataSource(canonicalDataSource(req.getDataSource()));
        if (!hasValidCoordinate(req.getLat(), req.getLng())) {
            req.setHasRealCoordinates(false);
            req.setPlaceDataRejectReason("MISSING_COORDINATES");
            return;
        }
        if (!isTrustedCoordinateSource(req.getDataSource())) {
            req.setHasRealCoordinates(false);
            req.setPlaceDataRejectReason("UNTRUSTED_SOURCE:" + req.getDataSource());
            if ("GEMINI".equalsIgnoreCase(String.valueOf(req.getDataSource()))
                    || "AI_SYSTEM".equalsIgnoreCase(String.valueOf(req.getDataSource()))) {
                req.setEnrichmentStatus("PARTIAL");
            }
            return;
        }
        if (!isCoordinateNearDestination(trip.getDestination(), req.getLat(), req.getLng(), req.getCategory(), req.getPlaceAddress())) {
            log.warn("Rejected suspicious coords for stop '{}' destination='{}': {},{} source={}",
                    req.getName(), trip.getDestination(), req.getLat(), req.getLng(), req.getDataSource());
            req.setHasRealCoordinates(false);
            req.setEnrichmentStatus("PARTIAL");
            req.setPlaceDataRejectReason("TOO_FAR_FROM_DESTINATION");
        } else {
            req.setPlaceDataRejectReason(null);
        }
    }

    private void enforceTripCoordinateTrust(TripStop stop, Trip trip) {
        if (isSystemStop(stop)) return;
        stop.setDataSource(canonicalDataSource(stop.getDataSource()));
        if (!hasValidCoordinate(stop.getLat(), stop.getLng())) {
            stop.setHasRealCoordinates(false);
            stop.setPlaceDataRejectReason("MISSING_COORDINATES");
            return;
        }
        if (!isTrustedCoordinateSource(stop.getDataSource())) {
            stop.setHasRealCoordinates(false);
            stop.setPlaceDataRejectReason("UNTRUSTED_SOURCE:" + stop.getDataSource());
            if ("GEMINI".equalsIgnoreCase(String.valueOf(stop.getDataSource()))
                    || "AI_SYSTEM".equalsIgnoreCase(String.valueOf(stop.getDataSource()))) {
                stop.setEnrichmentStatus("PARTIAL");
            }
            return;
        }
        if (!isCoordinateNearDestination(trip.getDestination(), stop.getLat(), stop.getLng(), stop.getCategory(), stop.getPlaceAddress())) {
            log.warn("Rejected suspicious coords for stop '{}' destination='{}': {},{} source={}",
                    stop.getName(), trip.getDestination(), stop.getLat(), stop.getLng(), stop.getDataSource());
            stop.setHasRealCoordinates(false);
            stop.setEnrichmentStatus("PARTIAL");
            stop.setPlaceDataRejectReason("TOO_FAR_FROM_DESTINATION");
        } else {
            stop.setPlaceDataRejectReason(null);
        }
    }

    private boolean isTrustedCoordinateSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = canonicalDataSource(source);
        return switch (normalized) {
            case "GOONG", "SERPAPI", "MANUAL", "SAVED_PLACE" -> true;
            default -> false;
        };
    }

    private String canonicalDataSource(String source) {
        if (source == null || source.isBlank()) return source;
        String normalized = source.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SERP_API" -> "SERPAPI";
            case "WIKIMEDIA" -> "WIKIMEDIA_IMAGE_ONLY";
            default -> normalized;
        };
    }

    private boolean isCoordinateNearDestination(String destination, Double lat, Double lng, String category, String address) {
        if (!hasValidCoordinate(lat, lng)) return false;
        if (destination == null || destination.isBlank()) return true;
        String normalized = normalizeText(destination);
        String categoryValue = category == null ? "" : category.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (isSystemStopCategory(categoryValue) || "TRANSPORT".equals(categoryValue)) return true;

        CityFence fence = cityFence(normalized);
        if (fence == null) return true;
        double distanceKm = haversineKm(fence.lat(), fence.lng(), lat, lng);
        boolean addressMatches = address != null && !address.isBlank() && normalizeText(address).contains(fence.keyword());
        return distanceKm <= fence.radiusKm() || addressMatches;
    }

    private CityFence cityFence(String normalizedDestination) {
        if (normalizedDestination.contains("vung tau") || normalizedDestination.contains("ba ria")) {
            return new CityFence(10.3460, 107.0843, 60.0, "vung tau");
        }
        if (normalizedDestination.contains("da lat") || normalizedDestination.contains("lam dong")) {
            return new CityFence(11.9404, 108.4583, 60.0, "da lat");
        }
        if (normalizedDestination.contains("ha noi") || normalizedDestination.contains("hanoi")) {
            return new CityFence(21.0285, 105.8542, 60.0, "ha noi");
        }
        if (normalizedDestination.contains("ho chi minh") || normalizedDestination.contains("hcm") || normalizedDestination.contains("sai gon")) {
            return new CityFence(10.7769, 106.7009, 60.0, "ho chi minh");
        }
        if (normalizedDestination.contains("da nang")) {
            return new CityFence(16.0544, 108.2022, 60.0, "da nang");
        }
        if (normalizedDestination.contains("nha trang") || normalizedDestination.contains("khanh hoa")) {
            return new CityFence(12.2388, 109.1967, 60.0, "nha trang");
        }
        return null;
    }

    private String normalizeText(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record CityFence(double lat, double lng, double radiusKm, String keyword) {}

    @Transactional
    public void deleteStop(UUID tripId, UUID stopId, UUID userId) {
        getEditableTrip(tripId, userId);
        var stop = stopRepo.findById(stopId).orElseThrow(() -> GolaException.notFound("Stop"));
        if (!stop.getTrip().getId().equals(tripId)) throw GolaException.forbidden();
        stopRepo.delete(stop);
    }

    @Transactional
    public TripStopResponse completeStop(UUID tripId, UUID stopId, UUID userId) {
        var stop = getTripMemberStop(tripId, stopId, userId);
        stop.setCompletedAt(Instant.now());
        return mapStopToResponse(stopRepo.save(stop));
    }

    @Transactional
    public TripStopResponse uncompleteStop(UUID tripId, UUID stopId, UUID userId) {
        var stop = getTripMemberStop(tripId, stopId, userId);
        stop.setCompletedAt(null);
        return mapStopToResponse(stopRepo.save(stop));
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
    public TripResponse endTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        if (trip.getStatus() == TripStatus.COMPLETED) {
            ensureTripMemory(trip, userId);
            return mapToResponse(trip, userId, null, null);
        }
        sessionRepo.findByTripIdAndStatus(tripId, SessionStatus.ACTIVE).ifPresent(s -> {
            s.setStatus(SessionStatus.ENDED);
            s.setEndedAt(Instant.now());
            sessionRepo.save(s);
        });
        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        Trip saved = tripRepo.save(trip);
        ensureTripMemory(saved, userId);
        memberRepo.findByTripId(tripId).forEach(member ->
                notifyTripUser(member.getUserId(), "TRIP_COMPLETED", "Chuyến đi hoàn thành", "Chuyến đi của bạn đã hoàn thành.", tripId, "/post-trip?id=" + tripId)
        );
        return mapToResponse(saved, userId, null, null);
    }

    public TripMemoryResponse getTripMemory(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        TripMemory memory = trip.getStatus() == TripStatus.COMPLETED
                ? ensureTripMemory(trip, userId)
                : memoryRepo.findByTripIdAndUserId(tripId, userId).orElse(null);
        if (memory == null) {
            throw GolaException.notFound("Trip memory");
        }
        return mapMemoryToResponse(memory);
    }

    public List<TripMemoryResponse> listMyTripMemories(UUID userId) {
        return memoryRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapMemoryToResponse)
                .toList();
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

    private TripStop getTripMemberStop(UUID tripId, UUID stopId, UUID userId) {
        tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) throw GolaException.forbidden();
        var stop = stopRepo.findById(stopId).orElseThrow(() -> GolaException.notFound("Stop"));
        if (!stop.getTrip().getId().equals(tripId)) throw GolaException.forbidden();
        return stop;
    }

    private boolean hasValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null
                && Double.isFinite(lat)
                && Double.isFinite(lng)
                && lat != 0.0
                && lng != 0.0;
    }

    private List<TripStatus> resolveTripStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "all".equalsIgnoreCase(statusFilter.trim())) {
            return null;
        }
        String normalized = statusFilter.trim().toUpperCase();
        return switch (normalized) {
            case "ACTIVE", "CURRENT", "PLANS", "PLANNING" -> List.of(TripStatus.DRAFT, TripStatus.ACTIVE);
            case "COMPLETED", "MEMORIES" -> List.of(TripStatus.COMPLETED);
            case "ARCHIVED", "UNAVAILABLE" -> List.of(TripStatus.ARCHIVED);
            default -> {
                try {
                    yield List.of(TripStatus.valueOf(normalized));
                } catch (IllegalArgumentException ex) {
                    throw GolaException.badRequest("Invalid trip status filter");
                }
            }
        };
    }

    private TripResponse mapToResponse(Trip t) {
        return mapToResponse(t, t.getOwnerId(), null, null);
    }

    private TripResponse mapToResponse(Trip t, UUID viewerId, Double userLat, Double userLng) {
        return mapToResponse(t, viewerId, userLat, userLng, false);
    }

    private TripResponse mapToResponse(Trip t, UUID viewerId, Double userLat, Double userLng, boolean includeRouteMetrics) {
        List<TripStopResponse> stops = t.getStops().stream()
                .sorted(java.util.Comparator.comparingDouble(TripStop::getOrderIdx))
                .map(this::mapStopToResponse)
                .toList();
        List<TripMember> members = memberRepo.findByTripId(t.getId());
        Map<UUID, Profile> profilesById = profileRepo.findAllById(
                        members.stream().map(TripMember::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));
        List<TripMemberResponse> memberResponses = members.stream()
                .map(member -> {
                    Profile profile = profilesById.get(member.getUserId());
                    return TripMemberResponse.builder()
                            .userId(member.getUserId())
                            .displayName(profile != null ? profile.getDisplayName() : null)
                            .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                            .phone(profile != null ? profile.getPhone() : null)
                            .role(member.getRole())
                            .joinedAt(member.getJoinedAt())
                            .build();
                })
                .toList();

        // Calculate total distance and travel time between consecutive stops with coordinates
        double totalDistanceKm = 0.0;
        int totalTravelTimeMin = 0;

        if (includeRouteMetrics) {
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
        }

        // Calculate distance from origin to the first stop
        Double distanceFromUserKm = null;
        Integer travelTimeFromUserMin = null;
        
        if (includeRouteMetrics && !stops.isEmpty()) {
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

        UUID memoryUserId = viewerId != null ? viewerId : t.getOwnerId();
        TripMemory memory = memoryRepo.findByTripIdAndUserId(t.getId(), memoryUserId).orElse(null);
        int questCompletedCount = (int) questProgressRepo.countByTripIdAndUserIdAndStatus(
                t.getId(), memoryUserId, QuestProgressStatus.COMPLETED);

        return TripResponse.builder()
            .id(t.getId()).ownerId(t.getOwnerId()).title(t.getTitle())
            .origin(t.getOrigin()).destination(t.getDestination())
            .startDate(t.getStartDate()).endDate(t.getEndDate())
            .status(t.getStatus()).isPublic(t.isPublic())
            .coverUrl(t.getCoverUrl()).description(t.getDescription())
            .stopsCount(t.getStops().size()).membersCount(memberResponses.size())
            .stops(stops)
            .members(memberResponses)
            .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).completedAt(t.getCompletedAt())
            .totalDistanceKm(totalDistanceKm > 0 ? Math.round(totalDistanceKm * 100.0) / 100.0 : null)
            .totalTravelTimeMin(totalTravelTimeMin > 0 ? totalTravelTimeMin : null)
            .distanceFromUserKm(distanceFromUserKm)
            .travelTimeFromUserMin(travelTimeFromUserMin)
            .questCompletedCount(questCompletedCount)
            .memoryId(memory != null ? memory.getId() : null)
            .memoryStatus(memory != null ? memory.getStatus() : "NOT_GENERATED")
            .memoryShareStatus(memory != null ? memory.getShareStatus() : "PRIVATE")
            .memorySummary(memory != null ? memory.getSummary() : null)
            .albumStatus(memory != null ? memory.getAlbumStatus() : "NOT_GENERATED")
            .bookStatus(memory != null ? memory.getBookStatus() : "NOT_GENERATED")
            .bookDownloadUrl(memory != null && "READY".equals(memory.getBookStatus())
                    ? "/api/trips/" + t.getId() + "/memory/book/download"
                    : null)
            .reelStatus(memory != null ? memory.getReelStatus() : "NOT_GENERATED")
            .qualityScore(t.getQualityScore())
            .qualityWarning(t.getQualityWarning())
            .build();
    }

    private TripMemory ensureTripMemory(Trip trip, UUID userId) {
        return memoryRepo.findByTripIdAndUserId(trip.getId(), userId)
                .map(existing -> {
                    boolean changed = false;
                    if (existing.getTitle() == null || existing.getTitle().isBlank()) {
                        existing.setTitle(memoryTitle(trip));
                        changed = true;
                    }
                    if (existing.getSummary() == null || existing.getSummary().isBlank()) {
                        existing.setSummary(memorySummary(trip));
                        changed = true;
                    }
                    return changed ? memoryRepo.save(existing) : existing;
                })
                .orElseGet(() -> memoryRepo.save(TripMemory.builder()
                        .tripId(trip.getId())
                        .userId(userId)
                        .title(memoryTitle(trip))
                        .summary(memorySummary(trip))
                        .status("NOT_GENERATED")
                        .shareStatus("PRIVATE")
                        .build()));
    }

    private String memoryTitle(Trip trip) {
        if (trip.getTitle() != null && !trip.getTitle().isBlank()) {
            return trip.getTitle();
        }
        if (trip.getDestination() != null && !trip.getDestination().isBlank()) {
            return "Memory in " + trip.getDestination();
        }
        return "Trip Memory";
    }

    private String memorySummary(Trip trip) {
        String route = List.of(trip.getOrigin(), trip.getDestination()).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" to "));
        int stopCount = trip.getStops() != null ? trip.getStops().size() : 0;
        if (route.isBlank()) {
            return "Completed trip with " + stopCount + " stops saved to your memories.";
        }
        return route + " completed with " + stopCount + " stops saved to your memories.";
    }

    private void notifyTripUser(UUID userId, String notificationType, String title, String body, UUID tripId, String targetUrl) {
        try {
            notificationService.notifyTrip(userId, notificationType, title, body, tripId, targetUrl);
        } catch (Exception e) {
            log.warn("Failed to create trip notification type={} trip={} user={}: {}", notificationType, tripId, userId, e.getMessage());
        }
    }

    private TripMemoryResponse mapMemoryToResponse(TripMemory memory) {
        return TripMemoryResponse.builder()
                .id(memory.getId())
                .tripId(memory.getTripId())
                .userId(memory.getUserId())
                .title(memory.getTitle())
                .summary(memory.getSummary())
                .status(memory.getStatus())
                .shareStatus(memory.getShareStatus())
                .generatedAt(memory.getGeneratedAt())
                .albumId(memory.getAlbumId())
                .albumStatus(memory.getAlbumStatus())
                .albumError(memory.getAlbumError())
                .albumGeneratedAt(memory.getAlbumGeneratedAt())
                .bookStatus(memory.getBookStatus())
                .bookUrl(memory.getBookUrl())
                .bookDownloadUrl(memory.getBookStatus() != null && memory.getBookStatus().equals("READY")
                        ? "/api/trips/" + memory.getTripId() + "/memory/book/download"
                        : null)
                .bookError(memory.getBookError())
                .bookGeneratedAt(memory.getBookGeneratedAt())
                .reelStatus(memory.getReelStatus())
                .reelStoryboard(memory.getReelStoryboard())
                .reelError(memory.getReelError())
                .reelGeneratedAt(memory.getReelGeneratedAt())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .build();
    }

    private TripStopResponse mapStopToResponse(TripStop s) {
        return TripStopResponse.builder()
            .id(s.getId()).placeId(s.getPlaceId()).orderIdx(s.getOrderIdx())
            .name(s.getName()).arrivalAt(s.getArrivalAt())
            .durationMin(s.getDurationMin()).estimatedCost(s.getEstimatedCost())
            .category(s.getCategory()).notes(s.getNotes())
            .completedAt(s.getCompletedAt())
            .lat(s.getLat()).lng(s.getLng())
            .imageUrl(s.getImageUrl())
            .rating(s.getRating())
            .reviewCount(s.getReviewCount())
            .imageSource(s.getImageSource())
            .placeAddress(s.getPlaceAddress())
            .dataSource(canonicalDataSource(s.getDataSource()))
            .enrichmentStatus(s.getEnrichmentStatus())
            .hasRealPhoto(s.getHasRealPhoto())
            .hasRealCoordinates(s.getHasRealCoordinates())
            .hasOpeningHours(s.getHasOpeningHours())
            .openingHoursText(s.getOpeningHoursText())
            .openNow(s.getOpenNow())
            .businessStatus(s.getBusinessStatus())
            .nextOpenCloseText(s.getNextOpenCloseText())
            .scheduledOpenStatus(s.getScheduledOpenStatus())
            .placeDataRejectReason(s.getPlaceDataRejectReason())
            .providerTitle(s.getProviderTitle())
            .providerId(s.getProviderId())
            .providerSource(s.getProviderSource())
            .systemStop(s.getSystemStop())
            .build();
    }

    @Transactional
    public void updateTripQuality(UUID tripId, int score, String warning) {
        var trip = tripRepo.findById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        trip.setQualityScore(score);
        trip.setQualityWarning(warning);
        tripRepo.save(trip);
    }

}
