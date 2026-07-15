package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.ExtractedTravelCandidate;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.ai.TravelResearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelCandidateExtractionService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public List<ExtractedTravelCandidate> extractCandidates(TravelResearchContext context, GenerateTripRequest req) {
        if (context == null || !context.hasUsefulContext()) {
            log.info("Travel candidate extraction skipped: no useful research context");
            return List.of();
        }

        List<ExtractedTravelCandidate> candidates = tryGeminiExtraction(context, req);
        if (candidates.isEmpty()) {
            candidates = heuristicExtraction(context, req);
        }

        Map<String, ExtractedTravelCandidate> deduped = new LinkedHashMap<>();
        for (ExtractedTravelCandidate candidate : candidates) {
            if (candidate.getName() == null || candidate.getName().isBlank()) continue;
            String key = normalize(candidate.getName()) + "|" + normalize(candidate.getCategory());
            deduped.putIfAbsent(key, candidate);
        }
        List<ExtractedTravelCandidate> result = deduped.values().stream().limit(60).toList();
        log.info("Travel candidate extraction complete: extractedCandidateCount={}", result.size());
        return result;
    }

    private List<ExtractedTravelCandidate> tryGeminiExtraction(TravelResearchContext context, GenerateTripRequest req) {
        try {
            String response = geminiClient.generateContent(buildPrompt(context, req));
            String cleaned = response.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode array = root.isArray() ? root : root.path("candidates");
            if (!array.isArray()) return List.of();
            return objectMapper.convertValue(array, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Travel candidate Gemini extraction failed, falling back to heuristic extraction: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(TravelResearchContext context, GenerateTripRequest req) {
        String destination = req.getDestination() == null ? context.getDestination() : req.getDestination();
        StringBuilder sources = new StringBuilder();
        if (context.getSourceSummaries() != null) {
            for (TravelResearchContext.SourceSummary source : context.getSourceSummaries().stream().limit(12).toList()) {
                sources.append("- title: ").append(nullToEmpty(source.getTitle())).append("\n")
                        .append("  snippet: ").append(nullToEmpty(source.getSnippet())).append("\n")
                        .append("  link: ").append(nullToEmpty(source.getLink())).append("\n");
            }
        }
        return """
                You extract travel candidate places from web research snippets for %s.
                Rules:
                - Extract concrete venue/place names only when possible.
                - Generic ideas are allowed only as slot hints, not fake real places.
                - Do not invent names that are not present in the context.
                - Return JSON only, no markdown.
                - Each item: name, category, reason, sourceTitle, sourceLink, confidence, searchQuery.
                - category one of FOOD, CAFE, SIGHTSEEING, BEACH, HOTEL_AREA, HOTEL, NIGHT, SHOPPING, TRANSPORT_HINT.
                - searchQuery must be provider-friendly Vietnamese and include destination/area.

                Research context:
                %s
                """.formatted(destination, sources);
    }

    private List<ExtractedTravelCandidate> heuristicExtraction(TravelResearchContext context, GenerateTripRequest req) {
        List<ExtractedTravelCandidate> result = new ArrayList<>();
        String destination = req.getDestination() == null ? context.getDestination() : req.getDestination();
        addAll(result, context.getRecommendedAttractions(), "SIGHTSEEING", destination, "research attraction");
        addAll(result, context.getFoodSuggestions(), "FOOD", destination, "research food");
        addAll(result, context.getHotelAreaSuggestions(), "HOTEL_AREA", destination, "research hotel area");
        return result;
    }

    private void addAll(List<ExtractedTravelCandidate> result, List<String> values, String category, String destination, String reason) {
        if (values == null) return;
        for (String value : values.stream().limit(20).toList()) {
            if (value == null || value.isBlank()) continue;
            result.add(ExtractedTravelCandidate.builder()
                    .name(value)
                    .category(category)
                    .reason(reason)
                    .confidence(55)
                    .searchQuery(value + " " + destination)
                    .build());
        }
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
