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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
public final class AiItineraryParser {

    private static final Pattern FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.MULTILINE);
    private static final Pattern METADATA_LINE = Pattern.compile(
            "(?i)^\\s*(Coordinates|Time|Start\\s*time|Duration|Estimated\\s*cost|Cost|Image)\\s*:\\s*.*$"
    );

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
        int minutes = minutesFromStartTime(stop.getStartTime());
        if (minutes < 0) {
            minutes = switch (normalizeTimeOfDay(stop.getTimeOfDay())) {
                case "MORNING" -> 8 * 60;
                case "AFTERNOON" -> 13 * 60;
                case "EVENING", "NIGHT" -> 18 * 60;
                default -> 9 * 60;
            };
        }
        return day * 100000.0 + minutes;
    }

    public static String buildNotes(AiGeneratedStop stop) {
        String notes = cleanNaturalNotes(stop.getDescription());
        return notes == null || notes.isBlank() ? null : notes;
    }

    public static String cleanNaturalNotes(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !METADATA_LINE.matcher(line).matches())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalizeTimeOfDay(String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank()) {
            return "";
        }
        return timeOfDay.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static int minutesFromStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return -1;
        }
        try {
            LocalTime parsed = LocalTime.parse(startTime.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            return parsed.getHour() * 60 + parsed.getMinute();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }
}
