package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.AiInsightsResponse;
import com.gola.entity.Trip;
import com.gola.exception.GolaException;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightsService {
    private static final List<String> DEFAULT_TIPS = List.of(
            "Hãy đặt bàn trước tại các nhà hàng nổi tiếng.",
            "Mang theo kem chống nắng khi tham quan ngoài trời.",
            "Tránh di chuyển vào giờ cao điểm 7-8h và 17-18h."
    );

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TripRepository tripRepo;
    private final TripMemberRepository memberRepo;

    public AiInsightsResponse getInsights(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.isPublic() && !memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }

        String cacheKey = "insights:" + tripId;
        AiInsightsResponse cached = readCache(cacheKey);
        if (cached != null) return cached;

        AiInsightsResponse response;
        try {
            response = AiInsightsResponse.builder()
                    .tips(parseTips(geminiClient.generateContent(buildPrompt(trip))))
                    .generatedAt(Instant.now().toString())
                    .build();
        } catch (Exception e) {
            log.warn("Gemini insights failed for trip {}. Returning defaults: {}", tripId, e.getMessage());
            response = AiInsightsResponse.builder()
                    .tips(DEFAULT_TIPS)
                    .generatedAt(Instant.now().toString())
                    .build();
        }

        writeCache(cacheKey, response);
        return response;
    }

    private String buildPrompt(Trip trip) {
        List<Map<String, Object>> stopPayload = trip.getStops().stream()
                .sorted((a, b) -> Double.compare(a.getOrderIdx(), b.getOrderIdx()))
                .map(stop -> Map.<String, Object>of(
                        "name", stop.getName() != null ? stop.getName() : "Địa điểm",
                        "arrivalAt", stop.getArrivalAt() != null ? stop.getArrivalAt().toString() : ""
                ))
                .toList();

        String stopsJson;
        try {
            stopsJson = objectMapper.writeValueAsString(stopPayload);
        } catch (Exception e) {
            stopsJson = stopPayload.toString();
        }

        return """
            Bạn là hướng dẫn viên địa phương tại %s.
            Khách đang có lịch trình: %s.
            Hãy đưa ra đúng 3 gợi ý thực tế ngắn gọn (mỗi gợi ý 1-2 câu) về: thời tiết/trang phục, giờ cao điểm cần tránh, hoặc tip địa phương hữu ích.
            Trả về JSON array of strings.
            Ngôn ngữ: Tiếng Việt. Chỉ trả JSON, không markdown.
            """.formatted(
                trip.getDestination() != null ? trip.getDestination() : trip.getTitle(),
                stopsJson
        ).trim();
    }

    private List<String> parseTips(String text) throws Exception {
        JsonNode root = objectMapper.readTree(extractJsonArray(text));
        if (!root.isArray()) throw new IllegalArgumentException("Insights response is not a JSON array");

        List<String> tips = new ArrayList<>();
        for (JsonNode node : root) {
            String tip = node.asText("").trim();
            if (!tip.isBlank()) tips.add(tip);
            if (tips.size() == 3) break;
        }
        return tips.isEmpty() ? DEFAULT_TIPS : tips;
    }

    private AiInsightsResponse readCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (value instanceof AiInsightsResponse response) return response;
            return objectMapper.convertValue(value, AiInsightsResponse.class);
        } catch (Exception e) {
            log.debug("Failed to read insights cache {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, AiInsightsResponse response) {
        try {
            redisTemplate.opsForValue().set(key, response, Duration.ofHours(1));
        } catch (Exception e) {
            log.debug("Failed to write insights cache {}: {}", key, e.getMessage());
        }
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
}
