package com.gola.service;

import com.gola.dto.ai.AiCriticResult;
import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.CandidatePlace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GapFillerService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public List<AiGeneratedStop> fill(
            List<AiGeneratedStop> stops,
            AiCriticResult critic,
            String destination,
            List<CandidatePlace> pool) {
        if (critic == null || critic.getMissingSlots() == null || critic.getMissingSlots().isEmpty()) {
            return stops;
        }

        List<AiGeneratedStop> filled = new ArrayList<>(stops);
        Set<String> usedTitles = new HashSet<>();
        for (AiGeneratedStop s : stops) {
            if (s.getPlaceName() != null) {
                usedTitles.add(normalizeKey(s.getPlaceName()));
            }
        }

        for (AiCriticResult.MissingSlot slot : critic.getMissingSlots()) {
            log.info("Filling missing slot: {} for day: {}", slot.getSlotType(), slot.getDayIndex());
            CandidatePlace match = findUnusedCandidate(slot.getSlotType(), pool, usedTitles);

            AiGeneratedStop stop = new AiGeneratedStop();
            stop.setDay(slot.getDayIndex());
            stop.setMustHave(true);
            stop.setFlexibility("FLEXIBLE");

            String slotType = slot.getSlotType().toUpperCase();
            switch (slotType) {
                case "BREAKFAST" -> {
                    stop.setCategory("FOOD");
                    stop.setMealType("BREAKFAST");
                    stop.setTimeOfDay("Morning");
                    stop.setStartTime("08:00");
                    stop.setDurationMinutes(45);
                    stop.setEstimatedCost(BigDecimal.valueOf(50000));
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Ăn sáng với món ăn địa phương tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Ăn sáng món ngon " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "quán ăn sáng ngon " + destination);
                        stop.setDescription("Thưởng thức bữa sáng đặc sắc của địa phương.");
                    }
                }
                case "LUNCH" -> {
                    stop.setCategory("FOOD");
                    stop.setMealType("LUNCH");
                    stop.setTimeOfDay("Afternoon");
                    stop.setStartTime("12:15");
                    stop.setDurationMinutes(60);
                    stop.setEstimatedCost(BigDecimal.valueOf(120000));
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Thưởng thức bữa trưa tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Ăn trưa đặc sản " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "nhà hàng ăn trưa ngon " + destination);
                        stop.setDescription("Dừng chân nghỉ ngơi và ăn trưa.");
                    }
                }
                case "DINNER" -> {
                    stop.setCategory("FOOD");
                    stop.setMealType("DINNER");
                    stop.setTimeOfDay("Evening");
                    stop.setStartTime("18:45");
                    stop.setDurationMinutes(90);
                    stop.setEstimatedCost(BigDecimal.valueOf(180000));
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Ăn tối ngon miệng tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Ăn tối hải sản/đặc sản " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "nhà hàng hải sản ngon " + destination);
                        stop.setDescription("Ăn tối thoải mái sau ngày dài tham quan.");
                    }
                }
                case "CAFE" -> {
                    stop.setCategory("CAFE");
                    stop.setMealType("DRINK");
                    stop.setExperienceType("CAFE_CHILL");
                    stop.setTimeOfDay("Afternoon");
                    stop.setStartTime("15:30");
                    stop.setDurationMinutes(60);
                    stop.setEstimatedCost(BigDecimal.valueOf(60000));
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Nghỉ ngơi thưởng thức cà phê tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Thưởng thức cà phê " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "quán cafe view đẹp " + destination);
                        stop.setDescription("Thư giãn thưởng thức đồ uống địa phương.");
                    }
                }
                case "HOTEL" -> {
                    stop.setCategory("CHECKIN");
                    stop.setExperienceType("ACCOMMODATION");
                    stop.setSystemStop(true);
                    stop.setTimeOfDay("Afternoon");
                    stop.setStartTime("14:00");
                    stop.setDurationMinutes(30);
                    stop.setEstimatedCost(BigDecimal.ZERO);
                    if (match != null) {
                        stop.setPlaceName("Nhận phòng tại " + match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Làm thủ tục nhận phòng nghỉ ngơi tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Nhận phòng khách sạn đã đặt");
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "khách sạn " + destination);
                        stop.setDescription("Check-in nhận phòng khách sạn và gửi hành lý.");
                    }
                }
                case "NIGHT" -> {
                    stop.setCategory("MARKET");
                    stop.setExperienceType("MARKET");
                    stop.setTimeOfDay("Evening");
                    stop.setStartTime("20:15");
                    stop.setDurationMinutes(90);
                    stop.setEstimatedCost(BigDecimal.valueOf(100000));
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Khám phá vui chơi buổi tối tại " + match.getTitle());
                    } else {
                        stop.setPlaceName("Vui chơi buổi tối " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "chợ đêm " + destination);
                        stop.setDescription("Khám phá hoạt động giải trí về đêm tại địa phương.");
                    }
                }
                default -> { // ATTRACTION / default
                    stop.setCategory("SIGHTSEEING");
                    stop.setTimeOfDay("Morning");
                    stop.setStartTime("09:30");
                    stop.setDurationMinutes(90);
                    stop.setEstimatedCost(BigDecimal.ZERO);
                    if (match != null) {
                        stop.setPlaceName(match.getTitle());
                        stop.setSearchQuery(match.getSearchQuery());
                        stop.setLat(match.getLat());
                        stop.setLng(match.getLng());
                        stop.setImageUrl(match.getImageUrl());
                        stop.setDescription("Tham quan điểm check-in nổi tiếng " + match.getTitle());
                    } else {
                        stop.setPlaceName("Điểm tham quan check-in " + destination);
                        stop.setSearchQuery(slot.getSearchQuery() != null ? slot.getSearchQuery() : "địa điểm du lịch check in " + destination);
                        stop.setDescription("Ghé thăm các địa điểm check-in hấp dẫn.");
                    }
                }
            }

            if (match != null) {
                usedTitles.add(normalizeKey(match.getTitle()));
            }
            filled.add(stop);
        }

        // Sort items for each day chronologically
        filled.sort(Comparator.comparingInt(AiGeneratedStop::getDay)
                .thenComparing(AiGeneratedStop::getStartTime));

        return filled;
    }

    private CandidatePlace findUnusedCandidate(String slotType, List<CandidatePlace> pool, Set<String> usedTitles) {
        if (pool == null) return null;
        for (CandidatePlace candidate : pool) {
            if (usedTitles.contains(normalizeKey(candidate.getTitle()))) continue;

            String cCat = candidate.getCategory();
            boolean matches = false;
            switch (slotType.toUpperCase()) {
                case "BREAKFAST", "LUNCH", "DINNER" -> matches = "FOOD".equalsIgnoreCase(cCat);
                case "CAFE" -> matches = "CAFE".equalsIgnoreCase(cCat);
                case "HOTEL" -> matches = "HOTEL".equalsIgnoreCase(cCat);
                case "NIGHT" -> matches = "MARKET".equalsIgnoreCase(cCat) || "FOOD".equalsIgnoreCase(cCat) || "CAFE".equalsIgnoreCase(cCat);
                case "ATTRACTION" -> matches = "ATTRACTION".equalsIgnoreCase(cCat);
            }

            if (matches) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeKey(String val) {
        if (val == null) return "";
        return normalizeAscii(val).replaceAll("\\s+", "");
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
