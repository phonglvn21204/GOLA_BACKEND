package com.gola.service;

import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.map.PlaceDetail;
import com.gola.entity.TripStop;
import com.gola.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalItinerarySanitizerService {

    private final PlaceEnrichmentService placeEnrichmentService;
    private final TripStopRepository stopRepo;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /**
     * Deduplicates and filters generic placeholders before saving the draft.
     */
    public List<AiGeneratedStop> sanitizeBeforeSave(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        if (stops == null || stops.isEmpty()) return List.of();

        List<AiGeneratedStop> result = new ArrayList<>();
        Set<String> genericTypesSaved = new HashSet<>();

        for (AiGeneratedStop stop : stops) {
            String name = stop.getPlaceName();
            if (name == null || name.isBlank()) continue;

            if (isGenericStop(name)) {
                String slotType = getGenericSlotType(name, stop.getCategory());
                if (genericTypesSaved.contains(slotType)) {
                    log.info("sanitizeBeforeSave: Removing duplicate generic stop '{}' (type={})", name, slotType);
                    continue;
                }
                genericTypesSaved.add(slotType);
            }
            result.add(stop);
        }
        return result;
    }

    /**
     * Post-enrichment sanitization: repairs coordinates, address leaks, hotel fallbacks,
     * re-searches failed/generic slots, removes optional failed stops, and handles duplicates.
     */
    @Transactional
    public List<TripStop> sanitizeAfterEnrichment(List<TripStop> stops, GenerateTripRequest req) {
        if (stops == null || stops.isEmpty()) return List.of();

        String destination = req.getDestination();
        List<TripStop> result = new ArrayList<>();
        List<TripStop> toDelete = new ArrayList<>();

        // 1. Process individual stops for canonical overrides, generic fallbacks & name mismatch retries
        for (TripStop stop : stops) {
            String name = stop.getName();
            if (name == null || name.isBlank()) continue;

            // Handle strict Hotel Check-in fallback first
            if (isHotelCheckinName(name) && (Boolean.TRUE.equals(stop.getSystemStop()) || "CHECKIN".equalsIgnoreCase(stop.getCategory()))) {
                forceHotelFallback(stop);
                result.add(stop);
                continue;
            }

            // Handle Bãi Sau canonical override
            if (isMostlyBaiSau(name)) {
                forceBaiSauOverride(stop);
                result.add(stop);
                continue;
            }

            // Clean bad address markers (maps, sovereignty, etc.)
            cleanBadAddresses(stop);

            // Handle name mismatch retry for known exact venues
            if (isKnownExactVenue(name) && isNameMismatch(name, stop.getName())) {
                log.info("Name mismatch detected for '{}'. Original provider name was '{}'. Retrying exact search once.", name, stop.getName());
                PlaceDetail retryDetail = retrySearch(name, destination, stop.getCategory());
                if (retryDetail != null && !isNameMismatch(name, retryDetail.getName())) {
                    applyPlaceDetail(stop, retryDetail);
                } else {
                    // Fallback to name mismatch state
                    stop.setPlaceDataRejectReason("NAME_MISMATCH");
                    clearStopMetadata(stop);
                }
            }

            // Handle failed enrichment retry or fallback conversion
            if (isRealPlaceStop(stop) && isFailedEnrichment(stop)) {
                String retryQuery = determineRetryQuery(name, destination, stop.getCategory());
                if (retryQuery != null) {
                    log.info("Enrichment failed for '{}'. Retrying once with query '{}'", name, retryQuery);
                    PlaceDetail retryDetail = retrySearch(retryQuery, destination, stop.getCategory());
                    if (retryDetail != null && isVerifiedDetail(retryDetail)) {
                        applyPlaceDetail(stop, retryDetail);
                    }
                }

                // If still failed after retry
                if (isFailedEnrichment(stop)) {
                    if (isMustHaveMeal(stop)) {
                        convertToSystemFallback(stop);
                    } else {
                        log.info("Keeping optional failed stop as honest fallback: '{}'", name);
                        convertToSystemFallback(stop);
                    }
                }
            }

            result.add(stop);
        }

        // Delete optional failed stops
        if (!toDelete.isEmpty()) {
            stopRepo.deleteAll(toDelete);
        }

        // 2. Process duplicate provider keys after enrichment
        resolveDuplicateProviders(result, destination);

        // Save and return
        return stopRepo.saveAll(result);
    }

    private void resolveDuplicateProviders(List<TripStop> stops, String destination) {
        Map<String, List<TripStop>> keyGroups = new HashMap<>();
        for (TripStop stop : stops) {
            if (stop.getSystemStop() != null && stop.getSystemStop()) continue;
            String key = buildProviderKey(stop);
            keyGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(stop);
        }

        for (Map.Entry<String, List<TripStop>> entry : keyGroups.entrySet()) {
            List<TripStop> dupStops = entry.getValue();
            if (dupStops.size() <= 1) continue;

            log.info("Duplicate provider key '{}' found for stops: {}", entry.getKey(), 
                    dupStops.stream().map(TripStop::getName).collect(Collectors.toList()));

            // Find the one with stronger name match
            TripStop bestMatch = null;
            double bestScore = -1.0;
            for (TripStop stop : dupStops) {
                double score = computeNameMatchScore(stop.getName(), stop.getName()); // compare original to final
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = stop;
                }
            }

            for (TripStop stop : dupStops) {
                if (stop == bestMatch) continue;

                // Rerun targeted search once
                PlaceDetail retryDetail = retrySearch(stop.getName(), destination, stop.getCategory());
                if (retryDetail != null && isVerifiedDetail(retryDetail) && !buildProviderKeyFromDetail(retryDetail).equals(entry.getKey())) {
                    applyPlaceDetail(stop, retryDetail);
                } else {
                    // Still duplicate/failed: remove if optional, else convert to system fallback
                    if (isMustHaveMeal(stop)) {
                        convertToSystemFallback(stop);
                        stop.setPlaceDataRejectReason("DUPLICATE_PROVIDER_CANDIDATE");
                    } else {
                        log.info("Removing duplicate optional stop: '{}'", stop.getName());
                        stops.remove(stop);
                        stopRepo.delete(stop);
                    }
                }
            }
        }
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

    private String getGenericSlotType(String name, String category) {
        String norm = normalizeAscii(name.toLowerCase());
        if (norm.contains("an sang")) return "BREAKFAST";
        if (norm.contains("an trua")) return "LUNCH";
        if (norm.contains("an toi") || norm.contains("an toi")) return "DINNER";
        if (norm.contains("quan an") || norm.contains("nha hang")) return "FOOD";
        if (norm.contains("ca phe") || norm.contains("cafe")) return "CAFE";
        if (norm.contains("diem tham quan") || norm.contains("dia diem du lich")) return "ATTRACTION";
        return category != null ? category.toUpperCase() : "OTHER";
    }

    private boolean isHotelCheckinName(String name) {
        if (name == null) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.contains("nhan phong khach san da dat") || norm.contains("nhan phong khach san da chon");
    }

    private void forceHotelFallback(TripStop stop) {
        stop.setName("Nhận phòng khách sạn đã đặt");
        stop.setCategory("CHECKIN");
        stop.setSystemStop(true);
        stop.setDataSource("SYSTEM");
        stop.setImageSource("CATEGORY_FALLBACK");
        stop.setHasRealCoordinates(false);
        stop.setHasRealPhoto(false);
        stop.setLat(null);
        stop.setLng(null);
        stop.setPlaceAddress(null);
        stop.setImageUrl(null);
        stop.setRating(null);
        stop.setReviewCount(null);
        stop.setBusinessStatus(null);
        stop.setPlaceDataRejectReason("HOTEL_NOT_VERIFIED");
    }

    private boolean isMostlyBaiSau(String name) {
        if (name == null) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.equals("bai sau") || norm.equals("bai sau vung tau") || norm.equals("back beach");
    }

    private void forceBaiSauOverride(TripStop stop) {
        stop.setName("Bãi Sau");
        stop.setCategory("SIGHTSEEING");
        stop.setPlaceAddress("Bãi Sau, Vũng Tàu");
        stop.setLat(10.337890831000038);
        stop.setLng(107.09215712800005);
        stop.setDataSource("CURATED");
        stop.setHasRealCoordinates(true);
        stop.setSystemStop(false);
    }

    private void cleanBadAddresses(TripStop stop) {
        if (stop.getPlaceAddress() != null) {
            String addr = stop.getPlaceAddress();
            if (addr.contains("Bản đồ") || addr.contains("chủ quyền") || addr.contains("biển đảo")) {
                stop.setPlaceAddress(null);
            }
        }
    }

    private boolean isRealPlaceStop(TripStop stop) {
        return stop.getSystemStop() == null || !stop.getSystemStop();
    }

    private boolean isFailedEnrichment(TripStop stop) {
        return "NONE".equalsIgnoreCase(stop.getDataSource())
                || "UNKNOWN".equalsIgnoreCase(stop.getDataSource())
                || stop.getDataSource() == null
                || stop.getLat() == null || stop.getLng() == null
                || stop.getHasRealCoordinates() == null || !stop.getHasRealCoordinates();
    }

    private String determineRetryQuery(String name, String destination, String category) {
        String norm = normalizeAscii(name.toLowerCase());
        if ("FOOD".equalsIgnoreCase(category)) {
            if (norm.contains("sang") || norm.contains("breakfast")) {
                return "quán ăn sáng " + destination;
            } else if (norm.contains("trua") || norm.contains("lunch")) {
                return "quán ăn trưa " + destination;
            } else {
                return "quán ăn ngon " + destination;
            }
        } else if ("CAFE".equalsIgnoreCase(category)) {
            return "quán cafe view biển " + destination;
        } else if ("SIGHTSEEING".equalsIgnoreCase(category) || "OTHER".equalsIgnoreCase(category)) {
            return "điểm check in " + destination;
        }
        return null;
    }

    private PlaceDetail retrySearch(String query, String destination, String category) {
        try {
            return placeEnrichmentService.enrichForStop(
                    query,
                    destination,
                    category,
                    null,
                    null,
                    PlaceEnrichmentService.SerpApiBudget.of(3)
            );
        } catch (Exception e) {
            log.warn("sanitizeAfterEnrichment: retry search failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isVerifiedDetail(PlaceDetail detail) {
        return detail != null 
                && !"NONE".equalsIgnoreCase(detail.getDataSource())
                && detail.getLat() != null && detail.getLng() != null
                && Boolean.TRUE.equals(detail.getHasRealCoordinates());
    }

    private void applyPlaceDetail(TripStop stop, PlaceDetail detail) {
        stop.setName(detail.getName());
        stop.setLat(detail.getLat());
        stop.setLng(detail.getLng());
        stop.setImageUrl(detail.getImageUrl());
        stop.setRating(detail.getRating());
        stop.setReviewCount(detail.getReviewCount());
        stop.setImageSource(detail.getImageSource());
        stop.setPlaceAddress(detail.getPlaceAddress() != null ? detail.getPlaceAddress() : detail.getAddress());
        stop.setOpeningHoursText(detail.getOpeningHours());
        stop.setOpenNow(detail.getOpenNow());
        stop.setBusinessStatus(detail.getProviderSource() != null ? detail.getProviderSource() : detail.getBusinessStatus());
        stop.setNextOpenCloseText(detail.getNextOpenCloseText());
        stop.setHasOpeningHours(detail.getHasOpeningHours());
        stop.setDataSource(detail.getDataSource());
        stop.setEnrichmentStatus(detail.getEnrichmentStatus());
        stop.setHasRealCoordinates(detail.getHasRealCoordinates());
        stop.setHasRealPhoto(detail.getHasRealPhoto());
        stop.setSystemStop(false);
    }

    private boolean isMustHaveMeal(TripStop stop) {
        String cat = stop.getCategory();
        if (!"FOOD".equalsIgnoreCase(cat)) return false;
        String name = stop.getName() != null ? stop.getName().toLowerCase() : "";
        return name.contains("sáng") || name.contains("trưa") || name.contains("tối") || name.contains("ăn");
    }

    private void convertToSystemFallback(TripStop stop) {
        String name = stop.getName();
        String fallbackName = "Ăn uống tự do tại địa phương";
        if (name != null) {
            if (name.contains("sáng")) fallbackName = "Ăn sáng tự do gần khu trung tâm";
            else if (name.contains("trưa")) fallbackName = "Ăn trưa tự do gần khu trung tâm";
            else if (name.contains("tối")) fallbackName = "Ăn tối tự do gần khu trung tâm";
        }
        stop.setName(fallbackName);
        stop.setSystemStop(true);
        stop.setDataSource("SYSTEM");
        stop.setLat(null);
        stop.setLng(null);
        stop.setPlaceAddress(null);
        stop.setImageUrl(null);
        stop.setRating(null);
        stop.setReviewCount(null);
        stop.setPlaceDataRejectReason("NO_VERIFIED_PLACE");
    }

    private String buildProviderKey(TripStop stop) {
        if (stop.getPlaceId() != null) {
            return stop.getPlaceId().toString();
        }
        if (stop.getName() != null && stop.getPlaceAddress() != null) {
            return normalizeAscii(stop.getName() + "||" + stop.getPlaceAddress()).toLowerCase();
        }
        if (stop.getLat() != null && stop.getLng() != null) {
            return String.format(Locale.US, "%.5f,%.5f", stop.getLat(), stop.getLng()) + "||" + (stop.getImageUrl() != null ? stop.getImageUrl() : "");
        }
        return UUID.randomUUID().toString();
    }

    private String buildProviderKeyFromDetail(PlaceDetail detail) {
        if (detail.getLat() != null && detail.getLng() != null) {
            return String.format(Locale.US, "%.5f,%.5f", detail.getLat(), detail.getLng()) + "||" + (detail.getImageUrl() != null ? detail.getImageUrl() : "");
        }
        return UUID.randomUUID().toString();
    }

    private boolean isKnownExactVenue(String name) {
        if (name == null) return false;
        String norm = normalizeAscii(name.toLowerCase());
        return norm.contains("banh khot goc vu sua")
                || norm.contains("banh canh ghe anh vy")
                || norm.contains("lau ca duoi hoang minh")
                || norm.contains("com nieu thien ly")
                || norm.contains("ganh hao")
                || norm.contains("son dang coffee")
                || norm.contains("marina club")
                || norm.contains("tuong chua kito vua")
                || norm.contains("mui nghinh phong");
    }

    private boolean isNameMismatch(String stopName, String providerName) {
        if (stopName == null || providerName == null) return false;
        String stopNorm = normalizeAscii(stopName.toLowerCase());
        String provNorm = normalizeAscii(providerName.toLowerCase());

        if (stopNorm.contains("goc vu sua") && !provNorm.contains("goc vu sua")) return true;
        if (stopNorm.contains("anh vy") && !provNorm.contains("anh vy")) return true;
        if (stopNorm.contains("hoang minh") && !provNorm.contains("hoang minh")) return true;
        if (stopNorm.contains("thien ly") && !provNorm.contains("thien ly")) return true;
        if (stopNorm.contains("ganh hao") && !provNorm.contains("ganh hao")) return true;
        if (stopNorm.contains("son dang") && !provNorm.contains("son dang")) return true;
        if (stopNorm.contains("marina club") && !provNorm.contains("marina club")) return true;
        if (stopNorm.contains("kito vua") && !provNorm.contains("kito vua") && !provNorm.contains("christ")) return true;
        if (stopNorm.contains("nghinh phong") && !provNorm.contains("nghinh phong")) return true;

        return false;
    }

    private void clearStopMetadata(TripStop stop) {
        stop.setLat(null);
        stop.setLng(null);
        stop.setPlaceAddress(null);
        stop.setImageUrl(null);
        stop.setRating(null);
        stop.setReviewCount(null);
        stop.setDataSource("SYSTEM");
        stop.setHasRealCoordinates(false);
        stop.setHasRealPhoto(false);
    }

    private double computeNameMatchScore(String stopName, String providerName) {
        if (stopName == null || providerName == null) return 0.0;
        String s1 = normalizeAscii(stopName.toLowerCase());
        String s2 = normalizeAscii(providerName.toLowerCase());
        Set<String> w1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> w2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
        Set<String> intersection = new HashSet<>(w1);
        intersection.retainAll(w2);
        Set<String> union = new HashSet<>(w1);
        union.addAll(w2);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private String normalizeAscii(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
