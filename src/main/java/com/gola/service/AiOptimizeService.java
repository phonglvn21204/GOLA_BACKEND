package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.OptimizeRouteResponse;
import com.gola.entity.Trip;
import com.gola.entity.TripStop;
import com.gola.entity.enums.MemberRole;
import com.gola.exception.GolaException;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOptimizeService {
    private static final List<MemberRole> EDIT_ROLES = List.of(MemberRole.OWNER, MemberRole.EDITOR);

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final TripRepository tripRepo;
    private final TripStopRepository stopRepo;
    private final TripMemberRepository memberRepo;

    @Transactional
    public OptimizeRouteResponse optimizeRoute(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(tripId, userId, EDIT_ROLES)) {
            throw GolaException.forbidden();
        }

        List<TripStop> allStops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        List<TripStop> movableStops = allStops.stream()
                .filter(stop -> !isSystemStop(stop))
                .filter(this::hasTrustedCoordinates)
                .toList();
        int missingDataRemaining = (int) allStops.stream()
                .filter(stop -> !isSystemStop(stop))
                .filter(stop -> !hasTrustedCoordinates(stop))
                .count();

        if (movableStops.size() < 2) {
            return OptimizeRouteResponse.builder()
                    .stops(allStops.stream().map(this::toResponseStop).toList())
                    .summary("Cần ít nhất 2 địa điểm có tọa độ hợp lệ để tối ưu lộ trình.")
                    .distanceBeforeKm(roundOne(totalRouteKm(allStops)))
                    .distanceAfterKm(roundOne(totalRouteKm(allStops)))
                    .distanceSavedKm(0.0)
                    .conflictsFixed(0)
                    .missingDataRemaining(missingDataRemaining)
                    .build();
        }

        double beforeKm = totalRouteKm(allStops);
        List<TripStop> optimizedOrder = optimizeWithAnchors(allStops);
        int movedStops = countMovedStops(allStops, optimizedOrder);
        applySequentialTimes(trip, optimizedOrder);
        double afterKm = totalRouteKm(optimizedOrder);

        stopRepo.saveAll(optimizedOrder);
        List<OptimizeRouteResponse.OptimizedStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId).stream()
                .map(this::toResponseStop)
                .toList();

        double savedKm = Math.max(0.0, beforeKm - afterKm);
        String summary = savedKm > 0.1
                ? "Đã tối ưu các điểm thật theo cụm gần nhau, giữ mốc hệ thống tại vị trí neo và tính lại giờ bắt đầu tuần tự theo từng ngày."
                : "Lộ trình hiện tại đã gần tối ưu với các tọa độ đáng tin hiện có.";

        return OptimizeRouteResponse.builder()
                .stops(stops)
                .summary(summary)
                .distanceBeforeKm(roundOne(beforeKm))
                .distanceAfterKm(roundOne(afterKm))
                .distanceSavedKm(roundOne(savedKm))
                .conflictsFixed(movedStops)
                .missingDataRemaining(missingDataRemaining)
                .build();
    }

    private String buildPrompt(Trip trip, List<TripStop> stops) {
        List<Map<String, Object>> stopPayload = stops.stream()
                .map(stop -> Map.<String, Object>of(
                        "id", stop.getId().toString(),
                        "name", stop.getName() != null ? stop.getName() : "Địa điểm",
                        "lat", stop.getLat(),
                        "lng", stop.getLng(),
                        "day", getStopDay(stop)
                ))
                .toList();

        String stopsJson;
        try {
            stopsJson = objectMapper.writeValueAsString(stopPayload);
        } catch (Exception e) {
            stopsJson = stopPayload.toString();
        }

        return """
            Bạn là travel optimizer. Sắp xếp lại thứ tự các điểm dừng sau để tối thiểu hóa quãng đường di chuyển và hợp lý về thời gian trong ngày (sáng/chiều/tối).
            Trả về JSON array với thứ tự mới, mỗi item gồm: id (giữ nguyên UUID gốc), orderIdx (số mới), arrivalAt (ISO 8601, giả sử bắt đầu lúc 8h sáng, mỗi stop trung bình 90 phút).
            Quy tắc orderIdx: day * 100000 + minutesFromMidnight. Giữ các điểm trong đúng day được cung cấp nếu có thể.
            Chỉ trả JSON, không markdown.

            Trip: %s
            Destination: %s
            Start date: %s
            Stops: %s
            """.formatted(
                trip.getTitle(),
                trip.getDestination(),
                trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now(),
                stopsJson
        ).trim();
    }

    private List<OptimizedItem> parseOptimizedItems(String text) throws Exception {
        JsonNode root = objectMapper.readTree(extractJsonArray(text));
        if (!root.isArray()) throw new IllegalArgumentException("Optimize response is not a JSON array");

        List<OptimizedItem> items = new ArrayList<>();
        for (JsonNode node : root) {
            UUID id = UUID.fromString(node.path("id").asText());
            double orderIdx = node.path("orderIdx").asDouble();
            Instant arrivalAt = Instant.parse(node.path("arrivalAt").asText());
            if (orderIdx > 0) {
                items.add(new OptimizedItem(id, orderIdx, arrivalAt));
            }
        }
        return items;
    }

    private List<OptimizedItem> greedyOptimize(Trip trip, List<TripStop> stops) {
        Map<Integer, List<TripStop>> byDay = new HashMap<>();
        for (TripStop stop : stops) {
            byDay.computeIfAbsent(getStopDay(stop), ignored -> new ArrayList<>()).add(stop);
        }

        LocalDate baseDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        List<OptimizedItem> result = new ArrayList<>();
        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<TripStop> ordered = nearestNeighbor(entry.getValue());
                    int day = entry.getKey();
                    for (int i = 0; i < ordered.size(); i++) {
                        int minutesFromMidnight = 8 * 60 + i * 90;
                        double orderIdx = day * 100000.0 + minutesFromMidnight;
                        Instant arrivalAt = baseDate.plusDays(day - 1L)
                                .atStartOfDay()
                                .plusMinutes(minutesFromMidnight)
                                .toInstant(ZoneOffset.UTC);
                        result.add(new OptimizedItem(ordered.get(i).getId(), orderIdx, arrivalAt));
                    }
                });
        return result;
    }

    private List<TripStop> nearestNeighbor(List<TripStop> stops) {
        List<TripStop> remaining = new ArrayList<>(stops);
        remaining.sort(Comparator.comparingDouble(TripStop::getOrderIdx));
        List<TripStop> ordered = new ArrayList<>();
        TripStop current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            TripStop from = current;
            TripStop next = remaining.stream()
                    .min(Comparator.comparingDouble(stop -> haversineKm(from.getLat(), from.getLng(), stop.getLat(), stop.getLng())))
                    .orElse(remaining.get(0));
            remaining.remove(next);
            ordered.add(next);
            current = next;
        }
        return ordered;
    }

    private List<TripStop> optimizeWithAnchors(List<TripStop> stops) {
        Map<Integer, List<TripStop>> byDay = new HashMap<>();
        for (TripStop stop : stops) {
            byDay.computeIfAbsent(getStopDay(stop), ignored -> new ArrayList<>()).add(stop);
        }

        List<TripStop> result = new ArrayList<>();
        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.addAll(optimizeDayWithAnchors(entry.getValue())));
        return result;
    }

    private List<TripStop> optimizeDayWithAnchors(List<TripStop> dayStops) {
        List<TripStop> ordered = new ArrayList<>();
        List<TripStop> segment = new ArrayList<>();
        dayStops.stream()
                .sorted(Comparator.comparingDouble(TripStop::getOrderIdx))
                .forEach(stop -> {
                    if (isSystemStop(stop) || !hasTrustedCoordinates(stop)) {
                        ordered.addAll(optimizeSegment(segment));
                        segment.clear();
                        ordered.add(stop);
                    } else {
                        segment.add(stop);
                    }
                });
        ordered.addAll(optimizeSegment(segment));
        return ordered;
    }

    private List<TripStop> optimizeSegment(List<TripStop> segment) {
        if (segment.size() <= 1) return new ArrayList<>(segment);
        return nearestNeighbor(segment);
    }

    private void applySequentialTimes(Trip trip, List<TripStop> orderedStops) {
        LocalDate baseDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        Map<Integer, List<TripStop>> byDay = new HashMap<>();
        for (TripStop stop : orderedStops) {
            byDay.computeIfAbsent(getStopDay(stop), ignored -> new ArrayList<>()).add(stop);
        }

        byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int day = entry.getKey();
                    List<TripStop> dayStops = entry.getValue();
                    int cursor = Math.max(7 * 60, firstKnownStartMinute(dayStops));
                    for (TripStop stop : dayStops) {
                        cursor = adjustForMealWindow(stop, cursor);
                        stop.setOrderIdx(day * 100000.0 + cursor);
                        stop.setArrivalAt(baseDate.plusDays(day - 1L)
                                .atStartOfDay()
                                .plusMinutes(cursor)
                                .toInstant(ZoneOffset.ofHours(7)));
                        cursor += Math.max(20, stop.getDurationMin() != null ? stop.getDurationMin() : defaultDurationMin(stop)) + 15;
                    }
                });
    }

    private int firstKnownStartMinute(List<TripStop> stops) {
        return stops.stream()
                .map(TripStop::getArrivalAt)
                .filter(arrivalAt -> arrivalAt != null)
                .mapToInt(this::minuteInVietnam)
                .min()
                .orElse(8 * 60);
    }

    private int minuteInVietnam(Instant instant) {
        var time = instant.atOffset(ZoneOffset.ofHours(7)).toLocalTime();
        return time.getHour() * 60 + time.getMinute();
    }

    private int adjustForMealWindow(TripStop stop, int cursor) {
        String category = normalizeCategory(stop.getCategory());
        if ("FOOD".equals(category)) {
            if (cursor < 11 * 60) return 11 * 60 + 30;
            if (cursor > 13 * 60 + 30 && cursor < 18 * 60) return 18 * 60 + 15;
        }
        if ("CAFE".equals(category) && cursor > 16 * 60 + 30 && cursor < 18 * 60) {
            return 18 * 60;
        }
        if ("MARKET".equals(category) && cursor < 18 * 60) {
            return 18 * 60;
        }
        return cursor;
    }

    private int defaultDurationMin(TripStop stop) {
        return switch (normalizeCategory(stop.getCategory())) {
            case "FOOD", "CAFE" -> 75;
            case "MARKET", "SIGHTSEEING" -> 90;
            case "TRANSPORT" -> 120;
            case "CHECKIN", "CHECK_IN", "CHECKOUT", "CHECK_OUT" -> 30;
            default -> 60;
        };
    }

    private String normalizeCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private boolean isSystemStop(TripStop stop) {
        return switch (normalizeCategory(stop.getCategory())) {
            case "TRANSPORT", "TRAVEL", "TRANSFER", "TRANSIT", "FLIGHT", "BUS", "TRAIN",
                    "TAXI", "MOTORBIKE", "CHECKIN", "CHECK_IN", "CHECKOUT", "CHECK_OUT",
                    "REST", "NOTE", "EMERGENCY", "DROP_LUGGAGE", "BAG_DROP" -> true;
            default -> Boolean.TRUE.equals(stop.getSystemStop());
        };
    }

    private int countMovedStops(List<TripStop> before, List<TripStop> after) {
        List<UUID> beforeIds = before.stream().map(TripStop::getId).toList();
        List<UUID> afterIds = after.stream().map(TripStop::getId).toList();
        int moved = 0;
        int count = Math.min(beforeIds.size(), afterIds.size());
        for (int i = 0; i < count; i++) {
            if (!beforeIds.get(i).equals(afterIds.get(i))) moved++;
        }
        return moved;
    }

    private double totalRouteKm(List<TripStop> stops) {
        double total = 0.0;
        TripStop previous = null;
        for (TripStop stop : stops.stream().sorted(Comparator.comparingDouble(TripStop::getOrderIdx)).toList()) {
            if (!hasTrustedCoordinates(stop)) {
                continue;
            }
            if (previous != null) {
                total += haversineKm(previous.getLat(), previous.getLng(), stop.getLat(), stop.getLng());
            }
            previous = stop;
        }
        return total;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private OptimizeRouteResponse.OptimizedStop toResponseStop(TripStop stop) {
        return OptimizeRouteResponse.OptimizedStop.builder()
                .id(stop.getId())
                .name(stop.getName())
                .orderIdx(stop.getOrderIdx())
                .arrivalAt(stop.getArrivalAt())
                .lat(stop.getLat())
                .lng(stop.getLng())
                .build();
    }

    private int getStopDay(TripStop stop) {
        double orderIdx = stop.getOrderIdx();
        if (orderIdx < 1000) return 1;
        if (orderIdx >= 100000) return Math.max(1, (int) Math.floor(orderIdx / 100000));
        return Math.max(1, (int) Math.floor(orderIdx / 1000));
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null
                && Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0.0 && lng != 0.0;
    }

    private boolean hasTrustedCoordinates(TripStop stop) {
        return isValidCoordinate(stop.getLat(), stop.getLng())
                && Boolean.TRUE.equals(stop.getHasRealCoordinates())
                && isTrustedCoordinateSource(stop.getDataSource());
    }

    private boolean isTrustedCoordinateSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = source.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "GOONG", "SERPAPI", "MANUAL", "SAVED_PLACE" -> true;
            default -> false;
        };
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

    private String extractJsonArray(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON array found");
        return cleaned.substring(start, end + 1);
    }

    private record OptimizedItem(UUID id, double orderIdx, Instant arrivalAt) {}
}
