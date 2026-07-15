package com.gola.service;

import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.ai.VerifiedCandidatePlace;
import com.gola.dto.map.PlaceDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissingSlotFillerService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private final PlaceEnrichmentService placeEnrichmentService;

    public List<AiGeneratedStop> fillMissingSlots(
            List<AiGeneratedStop> draft,
            GenerateTripRequest req,
            List<VerifiedCandidatePlace> verifiedPool) {
        if (draft == null) return List.of();
        List<AiGeneratedStop> result = new ArrayList<>(draft);
        Set<String> used = new HashSet<>();
        result.forEach(stop -> used.add(normalize(stop.getPlaceName())));

        int filled = 0;
        List<String> missing = new ArrayList<>();
        for (int day = 1; day <= Math.max(1, req.getDays()); day++) {
            final int currentDay = day;
            List<AiGeneratedStop> dayStops = result.stream().filter(s -> s.getDay() == currentDay).toList();
            if (isFullTravelDay(req, currentDay) && dayStops.stream().noneMatch(this::isFoodStop)) {
                filled += addSlot(result, used, req, verifiedPool, day, "LUNCH", "FOOD", "12:15", "quán ăn trưa " + req.getDestination(), true);
                missing.add("LUNCH day " + day);
            }
            if (isFullTravelDay(req, currentDay) && dayStops.stream().noneMatch(this::isCafeStop)) {
                filled += addSlot(result, used, req, verifiedPool, day, "CAFE", "CAFE", "15:30", "quán cafe view biển " + req.getDestination(), false);
                missing.add("CAFE day " + day);
            }
            if (day == 1 && req.getDays() >= 2 && dayStops.stream().noneMatch(this::isDinnerOrNightStop)) {
                filled += addSlot(result, used, req, verifiedPool, day, "DINNER", "FOOD", "18:45", "nhà hàng hải sản " + req.getDestination(), true);
                missing.add("DINNER day 1");
            }
        }

        if (req.getDays() >= 2 && Boolean.TRUE.equals(req.getNeedAccommodation())
                && result.stream().noneMatch(this::isHotelStop)) {
            filled += addSlot(result, used, req, verifiedPool, 1, "HOTEL", "HOTEL", "14:00", hotelQuery(req), true);
            missing.add("HOTEL");
        }

        result.sort(Comparator.comparingInt(AiGeneratedStop::getDay).thenComparing(s -> s.getStartTime() == null ? "23:59" : s.getStartTime()));
        log.info("Missing slot filler complete: missingSlots={} filledSlotCount={}", missing, filled);
        return result;
    }

    private int addSlot(
            List<AiGeneratedStop> result,
            Set<String> used,
            GenerateTripRequest req,
            List<VerifiedCandidatePlace> verifiedPool,
            int day,
            String slotType,
            String category,
            String startTime,
            String searchQuery,
            boolean essential) {
        VerifiedCandidatePlace candidate = findUnusedVerified(verifiedPool, category, used);
        if (candidate == null) {
            candidate = verifyTargeted(req, category, searchQuery);
        }

        AiGeneratedStop stop = new AiGeneratedStop();
        stop.setDay(day);
        stop.setCategory(category);
        stop.setTimeOfDay(timeOfDay(startTime));
        stop.setStartTime(startTime);
        stop.setDurationMinutes(defaultDuration(slotType));
        stop.setEstimatedCost(defaultCost(slotType));
        stop.setMustHave(essential);
        stop.setFlexibility(essential ? "FIXED" : "FLEXIBLE");
        stop.setSearchQuery(searchQuery);

        if (candidate != null && candidate.isVerified()) {
            stop.setPlaceName(candidate.getName());
            stop.setLat(candidate.getLat());
            stop.setLng(candidate.getLng());
            stop.setImageUrl(candidate.getImageUrl());
            stop.setSystemStop(false);
            stop.setDescription(descriptionForVerified(slotType, candidate.getName()));
            used.add(normalize(candidate.getName()));
            result.add(stop);
            return 1;
        }

        if (essential || "CAFE".equalsIgnoreCase(category)) {
            stop.setSystemStop(true);
            stop.setPlaceName(fallbackName(slotType));
            stop.setDescription("Mốc linh hoạt để người dùng chọn địa điểm phù hợp tại khu vực hiện tại.");
            if ("CAFE".equalsIgnoreCase(category)) {
                stop.setDescription("Cafe linh hoạt gần biển để nghỉ chân, không gắn tọa độ khi chưa xác minh được địa điểm.");
            }
            result.add(stop);
            return 1;
        }
        return 0;
    }

    private VerifiedCandidatePlace verifyTargeted(GenerateTripRequest req, String category, String searchQuery) {
        try {
            PlaceDetail detail = placeEnrichmentService.enrichForStop(
                    searchQuery,
                    req.getDestination(),
                    category,
                    null,
                    null,
                    "HOTEL".equalsIgnoreCase(category) ? PlaceEnrichmentService.SerpApiBudget.of(4) : PlaceEnrichmentService.SerpApiBudget.of(2)
            );
            if (detail == null || detail.getLat() == null || detail.getLng() == null || !Boolean.TRUE.equals(detail.getHasRealCoordinates())) {
                return null;
            }
            return VerifiedCandidatePlace.builder()
                    .name(detail.getName())
                    .providerTitle(detail.getProviderTitle() != null ? detail.getProviderTitle() : detail.getName())
                    .category(category)
                    .address(detail.getPlaceAddress() != null ? detail.getPlaceAddress() : detail.getAddress())
                    .lat(detail.getLat())
                    .lng(detail.getLng())
                    .rating(detail.getRating())
                    .reviewCount(detail.getReviewCount())
                    .imageUrl(detail.getImageUrl())
                    .providerId(detail.getProviderId())
                    .providerSource(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getDataSource())
                    .imageSource(detail.getImageSource())
                    .estimatedCost(detail.getEstimatedCost())
                    .searchQuery(searchQuery)
                    .verified(true)
                    .build();
        } catch (Exception e) {
            log.warn("Missing slot targeted search failed: query='{}' reason={}", searchQuery, e.getMessage());
            return null;
        }
    }

    private VerifiedCandidatePlace findUnusedVerified(List<VerifiedCandidatePlace> pool, String category, Set<String> used) {
        if (pool == null) return null;
        for (VerifiedCandidatePlace candidate : pool) {
            if (!candidate.isVerified()) continue;
            if (!categoryCompatible(category, candidate.getCategory())) continue;
            if (used.contains(normalize(candidate.getName()))) continue;
            return candidate;
        }
        return null;
    }

    private boolean categoryCompatible(String wanted, String actual) {
        if (wanted == null || actual == null) return false;
        if (wanted.equalsIgnoreCase(actual)) return true;
        return "FOOD".equalsIgnoreCase(wanted) && ("MARKET".equalsIgnoreCase(actual) || "NIGHT".equalsIgnoreCase(actual));
    }

    private boolean isFullTravelDay(GenerateTripRequest req, int day) {
        return req.getDays() > 1 || day == 1;
    }

    private boolean isFoodStop(AiGeneratedStop stop) {
        return "FOOD".equalsIgnoreCase(stop.getCategory()) || "MARKET".equalsIgnoreCase(stop.getCategory());
    }

    private boolean isCafeStop(AiGeneratedStop stop) {
        return "CAFE".equalsIgnoreCase(stop.getCategory()) || normalize(stop.getExperienceType()).contains("chill");
    }

    private boolean isDinnerOrNightStop(AiGeneratedStop stop) {
        String text = normalize(String.join(" ", String.valueOf(stop.getPlaceName()), String.valueOf(stop.getMealType()), String.valueOf(stop.getExperienceType())));
        return text.contains("dinner") || text.contains("toi") || text.contains("night") || text.contains("cho dem");
    }

    private boolean isHotelStop(AiGeneratedStop stop) {
        String category = normalize(stop.getCategory());
        String name = normalize(stop.getPlaceName());
        return category.contains("hotel") || category.contains("homestay") || category.contains("accommodation") || name.contains("nhan phong");
    }

    private String hotelQuery(GenerateTripRequest req) {
        String dest = req.getDestination();
        return normalize(dest).contains("vung tau") ? "khách sạn Bãi Sau Vũng Tàu" : "khách sạn gần trung tâm " + dest;
    }

    private String fallbackName(String slotType) {
        return switch (slotType) {
            case "HOTEL" -> "Nhận phòng khách sạn đã đặt";
            case "DINNER" -> "Ăn tối tự do gần khu trung tâm";
            case "LUNCH" -> "Ăn trưa tự do gần khu trung tâm";
            case "CAFE" -> "Cafe tự chọn gần biển";
            default -> "Mốc linh hoạt trong lịch trình";
        };
    }

    private String descriptionForVerified(String slotType, String name) {
        return switch (slotType) {
            case "HOTEL" -> "Nhận phòng và gửi hành lý tại " + name + ".";
            case "DINNER" -> "Ăn tối và nghỉ ngơi tại " + name + ".";
            case "LUNCH" -> "Dừng chân ăn trưa tại " + name + ".";
            case "CAFE" -> "Nghỉ chân, uống nước và thư giãn tại " + name + ".";
            default -> "Ghé " + name + " theo lịch trình.";
        };
    }

    private String timeOfDay(String startTime) {
        int hour = Integer.parseInt(startTime.substring(0, 2));
        if (hour < 12) return "Morning";
        if (hour < 18) return "Afternoon";
        return "Evening";
    }

    private int defaultDuration(String slotType) {
        return switch (slotType) {
            case "HOTEL" -> 30;
            case "CAFE" -> 60;
            case "DINNER" -> 90;
            default -> 60;
        };
    }

    private BigDecimal defaultCost(String slotType) {
        return switch (slotType) {
            case "CAFE" -> BigDecimal.valueOf(60000);
            case "DINNER" -> BigDecimal.valueOf(180000);
            case "LUNCH" -> BigDecimal.valueOf(120000);
            default -> BigDecimal.ZERO;
        };
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
