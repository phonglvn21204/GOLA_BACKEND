package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.AiGeneratedStop;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public final class AiItineraryParser {

    private static final Pattern FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.MULTILINE);

    private AiItineraryParser() {}

    public static List<AiGeneratedStop> parseStops(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = FENCE.matcher(raw.trim()).replaceAll("").trim();
        try {
            JsonNode root = mapper.readTree(cleaned);
            if (root.isArray()) {
                return toList(root, mapper);
            }
            for (String field : List.of("stops", "itinerary", "places", "data")) {
                JsonNode arr = root.get(field);
                if (arr != null && arr.isArray()) {
                    return toList(arr, mapper);
                }
            }
        } catch (Exception e) {
            log.debug("Direct JSON parse failed, trying array extraction: {}", e.getMessage());
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            try {
                return mapper.readValue(
                    cleaned.substring(start, end + 1),
                    new TypeReference<List<AiGeneratedStop>>() {}
                );
            } catch (Exception e) {
                log.warn("Failed to parse Gemini stops JSON array: {}", e.getMessage());
            }
        } else {
            log.warn("Gemini response contained no JSON array; skipping stop creation");
        }
        return List.of();
    }

    private static List<AiGeneratedStop> toList(JsonNode array, ObjectMapper mapper) {
        List<AiGeneratedStop> stops = new ArrayList<>();
        for (JsonNode item : array) {
            try {
                stops.add(mapper.treeToValue(item, AiGeneratedStop.class));
            } catch (Exception e) {
                log.warn("Skipping invalid stop entry: {}", e.getMessage());
            }
        }
        return stops;
    }

    public static double orderIdxFor(AiGeneratedStop stop) {
        int day = Math.max(1, stop.getDay());
        int slot = switch (normalizeTimeOfDay(stop.getTimeOfDay())) {
            case "MORNING" -> 100;
            case "AFTERNOON" -> 200;
            case "EVENING" -> 300;
            case "NIGHT" -> 400;
            default -> 150;
        };
        return day * 1000.0 + slot;
    }

    public static String buildNotes(AiGeneratedStop stop) {
        StringBuilder sb = new StringBuilder();
        if (stop.getDescription() != null && !stop.getDescription().isBlank()) {
            sb.append(stop.getDescription().trim());
        }
        if (stop.getLat() != null && stop.getLng() != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Coordinates: ").append(stop.getLat()).append(", ").append(stop.getLng());
        }
        if (stop.getTimeOfDay() != null && !stop.getTimeOfDay().isBlank()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Time: ").append(stop.getTimeOfDay());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String normalizeTimeOfDay(String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank()) {
            return "";
        }
        return timeOfDay.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}