package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gola.config.GolaProperties;
import com.gola.dto.map.AutocompleteSuggestion;
import com.gola.dto.map.PlaceDetail;
import com.gola.dto.map.PlaceResponse;
import com.gola.dto.map.ReverseGeocodeResponse;
import com.gola.entity.Place;
import com.gola.exception.GolaException;
import com.gola.repository.PlaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlaceService {
    private final PlaceRepository placeRepo;
    private final RestTemplate goongRestTemplate;
    private final RestTemplate nominatimRestTemplate;
    private final GolaProperties properties;
    private final PlaceEnrichmentService placeEnrichmentService;
    
    // In-memory cache for reverse geocoding: key = "lat,lng" (rounded to 4 decimals ~11m), value = {response, timestamp}
    private final Map<String, CachedReverseGeocode> reverseGeocodeCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours
    
    private static class CachedReverseGeocode {
        final ReverseGeocodeResponse response;
        final long timestamp;
        
        CachedReverseGeocode(ReverseGeocodeResponse response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public PlaceService(
            PlaceRepository placeRepo,
            @Qualifier("goongRestTemplate") RestTemplate goongRestTemplate,
            @Qualifier("nominatimRestTemplate") RestTemplate nominatimRestTemplate,
            GolaProperties properties,
            PlaceEnrichmentService placeEnrichmentService) {
        this.placeRepo = placeRepo;
        this.goongRestTemplate = goongRestTemplate;
        this.nominatimRestTemplate = nominatimRestTemplate;
        this.properties = properties;
        this.placeEnrichmentService = placeEnrichmentService;
    }

    public List<PlaceResponse> searchPlaces(String query, Double lat, Double lng) {
        return placeRepo.searchLocalPlaces(query, lat, lng).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public PlaceResponse getPlaceById(UUID id) {
        var place = placeRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Place"));
        return mapToResponse(place);
    }

    public List<AutocompleteSuggestion> searchAutocomplete(String input) {
        if (input == null || input.isBlank()) return List.of();

        try {
            String apiKey = properties.getGoong().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("GOONG_API_KEY is not configured");
            }

            String url = UriComponentsBuilder
                    .fromHttpUrl("https://rsapi.goong.io/Place/AutoComplete")
                    .queryParam("input", input)
                    .queryParam("api_key", apiKey)
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode> response = goongRestTemplate.getForEntity(url, JsonNode.class);
            JsonNode predictions = response.getBody() != null ? response.getBody().path("predictions") : null;
            if (predictions == null || !predictions.isArray()) return List.of();

            List<AutocompleteSuggestion> suggestions = new ArrayList<>();
            for (JsonNode prediction : predictions) {
                String placeId = prediction.path("place_id").asText(null);
                String description = prediction.path("description").asText(null);
                if (placeId != null && description != null) {
                    suggestions.add(AutocompleteSuggestion.builder()
                            .placeId(placeId)
                            .description(description)
                            .build());
                }
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("Goong autocomplete failed for '{}': {}", input, safeProviderError(e));
            return searchNominatimAutocomplete(input);
        }
    }

    public PlaceDetail getPlaceDetail(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            throw GolaException.badRequest("placeId is required");
        }

        if (placeId.startsWith("nominatim:")) {
            return decodeNominatimPlaceDetail(placeId);
        }

        try {
            String apiKey = properties.getGoong().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("GOONG_API_KEY is not configured");
            }

            String url = UriComponentsBuilder
                    .fromHttpUrl("https://rsapi.goong.io/Place/Detail")
                    .queryParam("place_id", placeId)
                    .queryParam("api_key", apiKey)
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode> response = goongRestTemplate.getForEntity(url, JsonNode.class);
            JsonNode result = response.getBody() != null ? response.getBody().path("result") : null;
            JsonNode location = result != null ? result.path("geometry").path("location") : null;
            Double lat = coordinateOrNull(location != null ? location.path("lat").asText(null) : null);
            Double lng = coordinateOrNull(location != null ? location.path("lng").asText(null) : null);
            if (lat == null || lng == null) {
                throw new IllegalStateException("Goong detail response missing coordinates");
            }

            return PlaceDetail.builder()
                    .name(result.path("name").asText(null))
                    .address(result.path("formatted_address").asText(result.path("description").asText(null)))
                    .lat(lat)
                    .lng(lng)
                    .dataSource("GOONG")
                    .enrichmentStatus("PARTIAL_WITH_COORDINATES")
                    .hasRealCoordinates(true)
                    .hasRealPhoto(false)
                    .hasOpeningHours(false)
                    .build();
        } catch (Exception e) {
            log.warn("Goong place detail failed for '{}': {}", placeId, safeProviderError(e));
            PlaceDetail fallback = placeEnrichmentService.enrich(placeId, "Vietnam");
            if (fallback != null) return fallback;
            throw GolaException.notFound("Place detail");
        }
    }

    /**
     * Reverse geocode coordinates to get formatted address.
     * Uses Goong Geocoding API with in-memory cache (24h TTL, rounded to ~11m precision).
     */
    public ReverseGeocodeResponse reverseGeocode(double lat, double lng) {
        // Validate coordinates
        if (!isValidCoordinate(lat) || !isValidCoordinate(lng)) {
            throw GolaException.badRequest("Invalid coordinates");
        }

        // Round to 4 decimals (~11m precision) for cache key
        String cacheKey = String.format("%.4f,%.4f", lat, lng);
        
        // Check cache first
        CachedReverseGeocode cached = reverseGeocodeCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Reverse geocode cache hit for {}", cacheKey);
            return cached.response;
        }

        try {
            String apiKey = properties.getGoong().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("GOONG_API_KEY is not configured");
            }

            String url = UriComponentsBuilder
                    .fromHttpUrl("https://rsapi.goong.io/Geocode")
                    .queryParam("latlng", lat + "," + lng)
                    .queryParam("api_key", apiKey)
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode> response = goongRestTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            JsonNode results = body != null ? body.path("results") : null;

            if (results == null || !results.isArray() || results.isEmpty()) {
                log.warn("Goong reverse geocode returned no results for {},{}", lat, lng);
                return buildFallbackResponse(lat, lng);
            }

            JsonNode firstResult = results.get(0);
            String formattedAddress = firstResult.path("formatted_address").asText(null);
            
            if (formattedAddress == null || formattedAddress.isBlank()) {
                log.warn("Goong reverse geocode returned empty address for {},{}", lat, lng);
                return buildFallbackResponse(lat, lng);
            }

            // Build short formatted version (first 2-3 parts)
            String[] parts = formattedAddress.split(",");
            String shortFormatted = parts.length <= 3 
                ? formattedAddress 
                : String.join(",", Arrays.copyOfRange(parts, 0, Math.min(3, parts.length))).trim();

            ReverseGeocodeResponse result = ReverseGeocodeResponse.builder()
                    .address(formattedAddress)
                    .formatted(shortFormatted)
                    .build();

            // Cache the result
            reverseGeocodeCache.put(cacheKey, new CachedReverseGeocode(result));
            log.debug("Reverse geocode cached for {} -> {}", cacheKey, shortFormatted);

            return result;

        } catch (Exception e) {
            log.warn("Goong reverse geocode failed for {},{}: {}", lat, lng, safeProviderError(e));
            // Return fallback instead of throwing error
            return buildFallbackResponse(lat, lng);
        }
    }
    
    private boolean isValidCoordinate(double value) {
        return Double.isFinite(value) && value != 0.0 && value >= -180.0 && value <= 180.0;
    }
    
    private ReverseGeocodeResponse buildFallbackResponse(double lat, double lng) {
        String fallback = String.format("%.5f, %.5f", lat, lng);
        return ReverseGeocodeResponse.builder()
                .address(fallback)
                .formatted(fallback)
                .build();
    }

    @Transactional
    public PlaceResponse upsertPlace(Place place) {
        if (place.getGooglePlaceId() != null) {
            var existingOpt = placeRepo.findByGooglePlaceId(place.getGooglePlaceId());
            if (existingOpt.isPresent()) {
                var existing = existingOpt.get();
                existing.setName(place.getName());
                existing.setCategory(place.getCategory());
                existing.setAddress(place.getAddress());
                existing.setCity(place.getCity());
                existing.setCountry(place.getCountry());
                existing.setPhotos(place.getPhotos());
                existing.setOpeningHours(place.getOpeningHours());
                existing.setRating(place.getRating());
                existing.setRefreshedAt(Instant.now());
                return mapToResponse(placeRepo.save(existing));
            }
        }
        place.setRefreshedAt(Instant.now());
        return mapToResponse(placeRepo.save(place));
    }

    private PlaceResponse mapToResponse(Place p) {
        return PlaceResponse.builder()
            .id(p.getId())
            .googlePlaceId(p.getGooglePlaceId())
            .name(p.getName())
            .category(p.getCategory())
            .address(p.getAddress())
            .city(p.getCity())
            .country(p.getCountry())
            .photos(p.getPhotos())
            .openingHours(p.getOpeningHours())
            .rating(p.getRating() != null ? p.getRating().doubleValue() : null)
            .refreshedAt(p.getRefreshedAt())
            .createdAt(p.getCreatedAt())
            .build();
    }

    private List<AutocompleteSuggestion> searchNominatimAutocomplete(String input) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", input)
                    .queryParam("format", "json")
                    .queryParam("limit", "5")
                    .queryParam("countrycodes", "vn")
                    .queryParam("addressdetails", "1")
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode[]> response = nominatimRestTemplate.getForEntity(url, JsonNode[].class);
            JsonNode[] body = response.getBody();
            if (body == null || body.length == 0) return List.of();

            List<AutocompleteSuggestion> suggestions = new ArrayList<>();
            for (JsonNode node : body) {
                String displayName = node.path("display_name").asText(null);
                Double lat = coordinateOrNull(node.path("lat").asText(null));
                Double lng = coordinateOrNull(node.path("lon").asText(null));
                if (displayName == null || lat == null || lng == null) continue;

                suggestions.add(AutocompleteSuggestion.builder()
                        .placeId(encodeNominatimPlaceId(displayName, lat, lng))
                        .description(displayName)
                        .build());
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("Nominatim autocomplete fallback failed for '{}': {}", input, e.getMessage());
            return List.of();
        }
    }

    private String encodeNominatimPlaceId(String displayName, double lat, double lng) {
        String raw = lat + "\n" + lng + "\n" + displayName;
        return "nominatim:" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private PlaceDetail decodeNominatimPlaceDetail(String placeId) {
        try {
            String encoded = placeId.substring("nominatim:".length());
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\n", 3);
            Double lat = parts.length > 0 ? coordinateOrNull(parts[0]) : null;
            Double lng = parts.length > 1 ? coordinateOrNull(parts[1]) : null;
            String address = parts.length > 2 ? parts[2] : null;
            if (lat == null || lng == null) {
                throw new IllegalArgumentException("Invalid Nominatim placeId");
            }
            return PlaceDetail.builder()
                    .name(address != null ? address.split(",")[0].trim() : null)
                    .address(address)
                    .lat(lat)
                    .lng(lng)
                    .dataSource("NOMINATIM")
                    .enrichmentStatus("PARTIAL_WITH_COORDINATES")
                    .hasRealCoordinates(true)
                    .hasRealPhoto(false)
                    .hasOpeningHours(false)
                    .build();
        } catch (Exception e) {
            log.warn("Invalid Nominatim fallback placeId: {}", e.getMessage());
            throw GolaException.notFound("Place detail");
        }
    }

    private Double coordinateOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw);
            return value == 0.0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeProviderError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message
                .replaceAll("(?i)(api_key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(access_token=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(token=)[^&\\s\\\"]+", "$1***")
                .replaceAll("https://rsapi\\.goong\\.io/[^\\s\\\"]+", "https://rsapi.goong.io/***");
    }
}
