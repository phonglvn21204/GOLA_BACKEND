package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gola.config.GolaProperties;
import com.gola.dto.ai.CandidatePlace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CandidatePoolService {

    private final RestTemplate restTemplate;
    private final GolaProperties properties;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public CandidatePoolService(
            @Qualifier("goongRestTemplate") RestTemplate restTemplate,
            GolaProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<CandidatePlace> buildPool(String destination, List<String> interests) {
        String apiKey = getSerpApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SerpApi key is not configured; returning empty candidate pool");
            return Collections.emptyList();
        }

        List<String> queries = getQueriesForDestination(destination);
        Map<String, CandidatePlace> poolMap = new LinkedHashMap<>();

        for (String query : queries) {
            try {
                log.info("Fetching candidate pool places for query: {}", query);
                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromHttpUrl("https://serpapi.com/search.json")
                        .queryParam("engine", "google_maps")
                        .queryParam("q", query)
                        .queryParam("hl", "vi")
                        .queryParam("gl", "vn")
                        .queryParam("api_key", apiKey);

                URI uri = builder.build().encode().toUri();
                ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class);
                JsonNode body = response.getBody();
                if (body == null) continue;

                JsonNode localResults = body.path("local_results");
                if (localResults.isArray()) {
                    for (JsonNode node : localResults) {
                        String title = node.path("title").asText(null);
                        if (title == null || title.isBlank()) continue;

                        String normTitle = normalizeKey(title);
                        if (poolMap.containsKey(normTitle)) continue;

                        String category = determineCategory(query, node.path("type").asText(""));
                        String address = node.path("address").asText(null);
                        Double lat = null;
                        Double lng = null;
                        JsonNode gps = node.path("gps_coordinates");
                        if (gps.isObject()) {
                            lat = gps.path("latitude").asDouble();
                            lng = gps.path("longitude").asDouble();
                        }

                        BigDecimal rating = null;
                        if (node.has("rating")) {
                            rating = BigDecimal.valueOf(node.path("rating").asDouble());
                        }
                        Integer reviews = null;
                        if (node.has("reviews")) {
                            reviews = node.path("reviews").asInt();
                        }
                        String thumbnail = null;
                        if (node.has("thumbnail")) {
                            thumbnail = node.path("thumbnail").asText();
                        } else if (node.has("image")) {
                            thumbnail = node.path("image").asText();
                        }

                        String providerId = node.path("data_id").asText(null);
                        if (providerId == null) {
                            providerId = node.path("place_id").asText(null);
                        }

                        CandidatePlace candidate = CandidatePlace.builder()
                                .title(title)
                                .category(category)
                                .address(address)
                                .lat(lat)
                                .lng(lng)
                                .rating(rating)
                                .reviewCount(reviews)
                                .imageUrl(thumbnail)
                                .providerId(providerId)
                                .source("SERPAPI")
                                .searchQuery(query)
                                .build();

                        poolMap.put(normTitle, candidate);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch SerpApi candidates for query '{}': {}", query, e.getMessage());
            }
        }

        List<CandidatePlace> pool = new ArrayList<>(poolMap.values());
        // Sort by review count desc then rating desc
        pool.sort((a, b) -> {
            int aRev = a.getReviewCount() != null ? a.getReviewCount() : 0;
            int bRev = b.getReviewCount() != null ? b.getReviewCount() : 0;
            if (aRev != bRev) return Integer.compare(bRev, aRev);
            double aRat = a.getRating() != null ? a.getRating().doubleValue() : 0.0;
            double bRat = b.getRating() != null ? b.getRating().doubleValue() : 0.0;
            return Double.compare(bRat, aRat);
        });

        // Limit to 30 candidates to avoid blowing up prompt size
        if (pool.size() > 30) {
            pool = pool.subList(0, 30);
        }

        log.info("Built candidate pool with {} items for destination: {}", pool.size(), destination);
        return pool;
    }

    private String getSerpApiKey() {
        if (properties.getSerpapi() == null) return null;
        return properties.getSerpapi().getApiKey();
    }

    private List<String> getQueriesForDestination(String destination) {
        String norm = normalizeAscii(destination);
        List<String> queries = new ArrayList<>();
        if (norm.contains("vung tau")) {
            queries.add("địa điểm du lịch Vũng Tàu");
            queries.add("quán ăn ngon Vũng Tàu");
            queries.add("bánh khọt Vũng Tàu");
            queries.add("khách sạn Bãi Sau Vũng Tàu");
            queries.add("quán cafe view biển Vũng Tàu");
        } else {
            queries.add("địa điểm du lịch " + destination);
            queries.add("quán ăn ngon " + destination);
            queries.add("quán cafe đẹp " + destination);
            queries.add("khách sạn trung tâm " + destination);
            queries.add("địa điểm đi chơi buổi tối " + destination);
        }
        return queries;
    }

    private String determineCategory(String query, String type) {
        String qNorm = query.toLowerCase();
        String tNorm = type.toLowerCase();
        if (qNorm.contains("khách sạn") || qNorm.contains("hotel") || tNorm.contains("hotel") || tNorm.contains("resort") || tNorm.contains("lodging")) {
            return "HOTEL";
        }
        if (qNorm.contains("cafe") || qNorm.contains("cà phê") || tNorm.contains("cafe") || tNorm.contains("coffee")) {
            return "CAFE";
        }
        if (qNorm.contains("quán ăn") || qNorm.contains("nhà hàng") || qNorm.contains("hải sản") || qNorm.contains("bánh khọt") || tNorm.contains("restaurant") || tNorm.contains("food")) {
            return "FOOD";
        }
        if (qNorm.contains("chợ đêm") || tNorm.contains("market") || tNorm.contains("shopping")) {
            return "MARKET";
        }
        return "ATTRACTION";
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
