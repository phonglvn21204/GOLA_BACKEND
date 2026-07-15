package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.map.AutocompleteSuggestion;
import com.gola.dto.map.ExplorePlaceResponse;
import com.gola.dto.map.PlaceDetail;
import com.gola.entity.enums.ExploreCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExploreService {
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_RESULTS = 10;

    private final PlaceService placeService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public List<ExplorePlaceResponse> getNearbyPlaces(String destination, ExploreCategory category) {
        if (destination == null || destination.isBlank() || category == null) {
            return List.of();
        }

        String cacheKey = cacheKey(destination, category);
        List<ExplorePlaceResponse> cached = readCache(cacheKey);
        if (cached != null) {
            log.debug("Explore cache hit for key {}", cacheKey);
            return cached;
        }

        log.info("Explore cache miss for key {}", cacheKey);
        List<ExplorePlaceResponse> places = fetchPlaces(destination.trim(), category);
        writeCache(cacheKey, places);
        return places;
    }

    private List<ExplorePlaceResponse> fetchPlaces(String destination, ExploreCategory category) {
        String keyword = keywordFor(destination, category);
        List<AutocompleteSuggestion> suggestions = placeService.searchAutocomplete(keyword);
        Map<String, ExplorePlaceResponse> deduped = new LinkedHashMap<>();

        for (AutocompleteSuggestion suggestion : suggestions.stream().limit(MAX_RESULTS).toList()) {
            try {
                PlaceDetail detail = placeService.getPlaceDetail(suggestion.getPlaceId());
                if (detail == null || !isValidCoordinate(detail.getLat(), detail.getLng())) {
                    continue;
                }

                String name = firstNonBlank(detail.getName(), shortName(suggestion.getDescription()));
                String address = firstNonBlank(detail.getAddress(), suggestion.getDescription());
                ExplorePlaceResponse place = ExplorePlaceResponse.builder()
                        .placeId(suggestion.getPlaceId())
                        .name(name)
                        .lat(detail.getLat())
                        .lng(detail.getLng())
                        .address(address)
                        .category(category)
                        .build();

                String key = suggestion.getPlaceId() != null
                        ? suggestion.getPlaceId()
                        : detail.getLat() + "," + detail.getLng();
                deduped.putIfAbsent(key, place);
            } catch (Exception e) {
                log.warn("Skipping explore suggestion '{}': {}", suggestion.getDescription(), e.getMessage());
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private String keywordFor(String destination, ExploreCategory category) {
        return switch (category) {
            case SIGHT -> "địa điểm du lịch " + destination;
            case HOTEL -> "khách sạn " + destination;
            case RESTAURANT -> "nhà hàng " + destination;
            case SERVICE -> "dịch vụ du lịch " + destination;
        };
    }

    private List<ExplorePlaceResponse> readCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            return objectMapper.convertValue(value, new TypeReference<List<ExplorePlaceResponse>>() {});
        } catch (Exception e) {
            log.warn("Explore Redis read failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<ExplorePlaceResponse> places) {
        try {
            redisTemplate.opsForValue().set(key, places, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Explore Redis write failed for {}: {}", key, e.getMessage());
        }
    }

    private String cacheKey(String destination, ExploreCategory category) {
        return "explore:" + normalize(destination) + ":" + category.name().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String shortName(String description) {
        if (description == null || description.isBlank()) return "Địa điểm";
        return description.split(",")[0].trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "Địa điểm";
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null
                && lng != null
                && Double.isFinite(lat)
                && Double.isFinite(lng)
                && !Objects.equals(lat, 0.0)
                && !Objects.equals(lng, 0.0);
    }
}
