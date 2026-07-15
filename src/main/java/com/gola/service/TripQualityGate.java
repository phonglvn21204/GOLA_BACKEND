package com.gola.service;

import com.gola.dto.ai.TripQualityReport;
import com.gola.entity.TripStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class TripQualityGate {

    private static final ZoneId TRIP_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public TripQualityReport evaluate(List<TripStop> savedStops, LocalDate startDate, int totalDays) {
        if (savedStops == null || savedStops.isEmpty()) {
            return TripQualityReport.builder()
                    .qualityScore(0)
                    .qualityWarning("Lịch trình trống.")
                    .build();
        }

        int realStopCount = 0;
        int systemStopCount = 0;
        int fallbackImageCount = 0;
        int missingCoordinateCount = 0;
        int duplicateProviderCount = 0;
        boolean hotelVerified = false;

        Map<Long, List<TripStop>> stopsByDay = new HashMap<>();
        Set<String> uniqueProviders = new HashSet<>();

        for (TripStop stop : savedStops) {
            boolean isSystem = stop.getSystemStop() != null && stop.getSystemStop();
            if (isSystem) {
                systemStopCount++;
            } else {
                realStopCount++;
                if (stop.getImageUrl() == null || stop.getImageUrl().isBlank()
                        || "CATEGORY_FALLBACK".equalsIgnoreCase(stop.getImageSource())) {
                    fallbackImageCount++;
                }
                if (stop.getLat() == null || stop.getLng() == null
                        || stop.getHasRealCoordinates() == null || !stop.getHasRealCoordinates()) {
                    missingCoordinateCount++;
                }

                if ("HOTEL".equalsIgnoreCase(stop.getCategory())
                        || "HOMESTAY".equalsIgnoreCase(stop.getCategory())
                        || "LODGING".equalsIgnoreCase(stop.getCategory())) {
                    hotelVerified = true;
                }

                // Check duplicates by coordinates or placeId
                String key = null;
                if (stop.getPlaceId() != null) {
                    key = stop.getPlaceId().toString();
                } else if (stop.getLat() != null && stop.getLng() != null) {
                    key = String.format("%.5f,%.5f", stop.getLat(), stop.getLng());
                }
                if (key != null) {
                    if (uniqueProviders.contains(key)) {
                        duplicateProviderCount++;
                    } else {
                        uniqueProviders.add(key);
                    }
                }
            }

            if (stop.getArrivalAt() != null) {
                LocalDate stopDate = stop.getArrivalAt().atZone(TRIP_TIME_ZONE).toLocalDate();
                long dayIndex = ChronoUnit.DAYS.between(startDate, stopDate) + 1;
                stopsByDay.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(stop);
            }
        }

        // Coverage calculations
        int mealDays = 0;
        int cafeDays = 0;
        int attractionDays = 0;

        for (long d = 1; d <= totalDays; d++) {
            List<TripStop> dayStops = stopsByDay.getOrDefault(d, Collections.emptyList());
            int foodCount = 0;
            int cafeCount = 0;
            int attractionCount = 0;

            for (TripStop s : dayStops) {
                if (Boolean.TRUE.equals(s.getSystemStop())) continue;

                String cat = s.getCategory() != null ? s.getCategory().toUpperCase() : "";
                if ("FOOD".equals(cat) || "MARKET".equals(cat)) {
                    foodCount++;
                } else if ("CAFE".equals(cat)) {
                    cafeCount++;
                } else if ("SIGHTSEEING".equals(cat) || "OTHER".equals(cat)) {
                    attractionCount++;
                }
            }

            if (foodCount >= 2) mealDays++;
            if (cafeCount >= 1) cafeDays++;
            if (attractionCount >= 1) attractionDays++;
        }

        double mealCoverage = totalDays > 0 ? (double) mealDays / totalDays : 0.0;
        double cafeCoverage = totalDays > 0 ? (double) cafeDays / totalDays : 0.0;
        double attractionCoverage = totalDays > 0 ? (double) attractionDays / totalDays : 0.0;

        // Density score
        // target: 4 real stops per day average
        int targetStops = totalDays * 4;
        int dayDensityScore = targetStops > 0 ? Math.min(100, (realStopCount * 100) / targetStops) : 100;

        // Score calculation
        int score = 100;
        score -= missingCoordinateCount * 5;
        score -= duplicateProviderCount * 10;
        if (totalDays >= 2 && !hotelVerified) {
            score -= 15;
        }
        score -= (int) ((1.0 - mealCoverage) * 15);
        score -= (int) ((1.0 - cafeCoverage) * 10);
        score -= (int) ((1.0 - attractionCoverage) * 10);
        if (dayDensityScore < 80) {
            score -= 15;
        }

        // Specific penalties from sanitizer rules
        boolean hasUnverifiedFoodCafe = false;
        boolean hasHotelNotVerified = !hotelVerified;
        boolean hasDuplicateProvider = duplicateProviderCount > 0;
        boolean hasGenericPlaceholders = false;

        for (TripStop stop : savedStops) {
            String name = stop.getName();
            if (name == null) continue;
            boolean isSystem = stop.getSystemStop() != null && stop.getSystemStop();
            String cat = stop.getCategory() != null ? stop.getCategory().toUpperCase() : "";

            if (isGenericStop(name)) {
                hasGenericPlaceholders = true;
                score -= 10; // Penalize generic placeholders remaining
            }

            if (!isSystem) {
                if ("NONE".equalsIgnoreCase(stop.getDataSource())) {
                    score -= 10;
                }
                if (("FOOD".equals(cat) || "CAFE".equals(cat)) && (stop.getLat() == null || stop.getLng() == null)) {
                    hasUnverifiedFoodCafe = true;
                    score -= 15;
                }
                if (isMostlyBaiSau(name)) {
                    String addr = stop.getPlaceAddress();
                    if (addr != null && (addr.contains("Bản đồ") || addr.contains("chủ quyền") || addr.contains("biển đảo") || !addr.contains("Vũng Tàu"))) {
                        score -= 15; // Bãi Sau weird address penalty
                    }
                }
            } else {
                if (isHotelCheckinName(name)) {
                    if ("NOMINATIM".equalsIgnoreCase(stop.getDataSource()) || "GOONG".equalsIgnoreCase(stop.getDataSource()) || stop.getPlaceAddress() != null) {
                        score -= 15; // Hotel fallback Nominatim/Goong address penalty
                    }
                    if ("HOTEL_NOT_VERIFIED".equals(stop.getPlaceDataRejectReason())) {
                        hasHotelNotVerified = true;
                    }
                }
            }
        }

        score = Math.max(0, Math.min(100, score));

        String warning = null;
        if (score < 80) {
            List<String> warningsList = new ArrayList<>();
            if (hasUnverifiedFoodCafe) {
                warningsList.add("Một số điểm ăn uống chưa xác minh");
            }
            if (hasHotelNotVerified) {
                warningsList.add("Khách sạn chưa xác minh");
            }
            if (hasDuplicateProvider) {
                warningsList.add("Có điểm trùng dữ liệu địa điểm");
            }
            if (hasGenericPlaceholders) {
                warningsList.add("Có điểm check-in còn chung chung");
            }
            if (!warningsList.isEmpty()) {
                warning = String.join(", ", warningsList);
            } else {
                warning = "Lịch trình còn thiếu dữ liệu địa điểm thật cho một số điểm.";
            }
        }

        TripQualityReport report = TripQualityReport.builder()
                .realStopCount(realStopCount)
                .systemStopCount(systemStopCount)
                .fallbackImageCount(fallbackImageCount)
                .missingCoordinateCount(missingCoordinateCount)
                .duplicateProviderCount(duplicateProviderCount)
                .hotelVerified(hotelVerified)
                .mealCoverage(mealCoverage)
                .cafeCoverage(cafeCoverage)
                .attractionCoverage(attractionCoverage)
                .dayDensityScore(dayDensityScore)
                .qualityScore(score)
                .qualityWarning(warning)
                .build();

        log.info("Trip Quality Gate report computed: score={}, warning={}", score, warning);
        return report;
    }

    private boolean isGenericStop(String name) {
        if (name == null || name.isBlank()) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.contains("diem tham quan check-in")
                || norm.contains("diem tham quan check in")
                || norm.contains("an sang dia phuong")
                || norm.contains("an trua dia phuong")
                || norm.contains("ca phe nghi chan")
                || norm.contains("dia diem du lich")
                || norm.contains("quan an dia phuong")
                || norm.contains("nha hang dia phuong");
    }

    private boolean isMostlyBaiSau(String name) {
        if (name == null) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.equals("bai sau") || norm.equals("bai sau vung tau") || norm.equals("back beach");
    }

    private boolean isHotelCheckinName(String name) {
        if (name == null) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.contains("nhan phong khach san da dat") || norm.contains("nhan phong khach san da chon");
    }

    private String normalizeAscii(String value) {
        if (value == null) return "";
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return java.util.regex.Pattern.compile("\\p{M}+").matcher(normalized)
                .replaceAll("")
                .toLowerCase(java.util.Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
