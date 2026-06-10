package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.trip.AddStopRequest;
import com.gola.dto.map.PlaceDetail;
import com.gola.entity.AiJob;
import com.gola.entity.enums.AiJobKind;
import com.gola.entity.enums.AiJobStatus;
import com.gola.repository.AiJobRepository;
import com.gola.exception.GolaException;
import com.gola.dto.trip.CreateTripRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripService {

    private static final java.util.regex.Pattern COORD_PATTERN =
            java.util.regex.Pattern.compile("Coordinates:\\s*([\\-0-9.]+),\\s*([\\-0-9.]+)");
    private final AiJobRepository jobRepo;
    private final AiQuotaService  quotaService;
    private final TripService     tripService;
    private final GeminiClient    geminiClient;
    private final ObjectMapper    objectMapper;
    private final PlaceEnrichmentService placeEnrichmentService;

    @Transactional
    public Map<String, Object> generateTrip(UUID userId, GenerateTripRequest req, boolean isPremium) {
        quotaService.checkAndIncrement(userId, AiJobKind.TRIP_GENERATE, isPremium);
        var job = AiJob.builder()
                .userId(userId).kind(AiJobKind.TRIP_GENERATE).status(AiJobStatus.RUNNING)
                .input(Map.of(
                        "origin", req.getOrigin(),
                        "destination", req.getDestination(),
                        "days", req.getDays(),
                        "interests", req.getInterests() != null ? req.getInterests() : "[]",
                        "budget", req.getBudget(),
                        "language", req.getLanguage()))
                .build();
        jobRepo.save(job);

        try {
            String prompt = buildPrompt(req);
            String geminiResponse = geminiClient.generateContent(prompt);
            log.info("Gemini response received for user:{} chars={}", userId, geminiResponse.length());

            // Parse 3 plans từ Gemini
            List<Map<String, Object>> plans = parsePlans(geminiResponse);
            if (plans.isEmpty()) {
                throw new RuntimeException("Gemini did not return valid plans");
            }

            // Tạo trip trong DB từ plan đầu tiên
            var tripReq = new CreateTripRequest();
            tripReq.setTitle(req.getDays() + " Days in " + req.getDestination());
            tripReq.setOrigin(req.getOrigin());
            tripReq.setDestination(req.getDestination());
            var trip = tripService.createTrip(userId, tripReq);

            // Save stops từ plan đầu tiên vào DB
            List<AiGeneratedStop> stops = extractStops(plans.getFirst());
            int savedStops = saveStops(trip.getId(), userId, stops, req.getDestination());
            log.info("Saved {} stops for trip:{}", savedStops, trip.getId());

            // Gắn tripId vào plan đầu tiên
            plans.getFirst().put("tripId", trip.getId().toString());

            // Update job
            job.setStatus(AiJobStatus.DONE);
            job.setCompletedAt(Instant.now());
            job.setOutput(Map.of("trip_id", trip.getId(), "stops_count", savedStops));
            jobRepo.save(job);

            log.info("AI trip generated for user:{} trip:{} stops:{}", userId, trip.getId(), savedStops);

            // Trả về plans array cho FE
            return Map.of("plans", plans);

        } catch (GolaException e) {
            failJob(job, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI trip generation failed for user:{} error:{}", userId, e.getMessage(), e);
            failJob(job, e.getMessage());
            throw GolaException.badRequest("AI generation failed: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> parsePlans(String raw) {
        try {
            // Strip markdown fences nếu có
            String cleaned = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

            // Tìm array [...] hoặc object { "plans": [...] }
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode plansNode = root.isArray() ? root : root.path("plans");

            if (plansNode.isArray() && !plansNode.isEmpty()) {
                return objectMapper.convertValue(plansNode, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse plans from Gemini: {}", e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<AiGeneratedStop> extractStops(Map<String, Object> plan) {
        try {
            Object timeline = plan.get("timeline");
            if (!(timeline instanceof List<?> days)) return List.of();

            List<AiGeneratedStop> result = new ArrayList<>();
            for (Object dayObj : days) {
                if (!(dayObj instanceof Map<?, ?> day)) continue;
                Object dayValue = day.get("day");
                int dayNum = dayValue == null ? 1 : ((Number) dayValue).intValue();
                Object itemsObj = day.get("items");
                if (!(itemsObj instanceof List<?> items)) continue;

                for (Object itemObj : items) {

                    if (!(itemObj instanceof Map<?, ?> rawItem)) continue;

                    Map<String, Object> item = (Map<String, Object>) rawItem;

                    var stop = new AiGeneratedStop();

                    stop.setDay(dayNum);

                    stop.setPlaceName(String.valueOf(
                            item.getOrDefault("activity",
                                    item.getOrDefault("placeName", ""))
                    ));

                    stop.setDescription(
                            String.valueOf(item.getOrDefault("description", ""))
                    );

                    stop.setTimeOfDay(String.valueOf(
                            item.getOrDefault("time",
                                    item.getOrDefault("timeOfDay", "MORNING"))
                    ));

                    // Parse lat/lng from direct fields if provided by Gemini
                    Object latObj = item.getOrDefault("lat", item.get("latitude"));
                    Object lngObj = item.getOrDefault("lng", item.get("longitude"));
                    if (latObj instanceof Number latNum && lngObj instanceof Number lngNum) {
                        stop.setLat(latNum.doubleValue());
                        stop.setLng(lngNum.doubleValue());
                    }

                    // Fallback: try parsing from "Coordinates: lat, lng" embedded in description
                    if (!isValidCoordinate(stop.getLat(), stop.getLng()) && stop.getDescription() != null) {
                        java.util.regex.Matcher m = COORD_PATTERN.matcher(stop.getDescription());
                        if (m.find()) {
                            try {
                                stop.setLat(Double.parseDouble(m.group(1)));
                                stop.setLng(Double.parseDouble(m.group(2)));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    result.add(stop);
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to extract stops from plan: {}", e.getMessage());
            return List.of();
        }
    }

    private int saveStops(UUID tripId, UUID userId, List<AiGeneratedStop> stops, String destination) {
        int saved = 0;
        for (AiGeneratedStop aiStop : stops) {
            if (aiStop.getPlaceName() == null || aiStop.getPlaceName().isBlank()) continue;
            try {
                var addReq = new AddStopRequest();
                addReq.setName(aiStop.getPlaceName().trim());
                addReq.setNotes(AiItineraryParser.buildNotes(aiStop));
                addReq.setOrderIdx(AiItineraryParser.orderIdxFor(aiStop));
                boolean hasGeminiCoords = isValidCoordinate(aiStop.getLat(), aiStop.getLng());
                if (hasGeminiCoords) {
                    addReq.setLat(aiStop.getLat());
                    addReq.setLng(aiStop.getLng());
                }
                addReq.setArrivalAt(calculateArrivalAt(aiStop.getDay(), aiStop.getTimeOfDay()));

                boolean isRouteStop = aiStop.getPlaceName().matches(
                        "(?i).*(travel to|travel from|depart|return to|back to|transfer|transit).*"
                );
                PlaceDetail detail = isRouteStop ? null : placeEnrichmentService.enrich(aiStop.getPlaceName(), destination);
                // Cập nhật lat/lng từ Nominatim nếu chính xác hơn
                if (!hasGeminiCoords && detail != null && isValidCoordinate(detail.getLat(), detail.getLng())) {
                    addReq.setLat(detail.getLat());
                    addReq.setLng(detail.getLng());
                }

                // Final fallback if coords are still null
                if (addReq.getLat() == null || addReq.getLng() == null) {
                    double[] coords = getDefaultCityCoordinates(destination);
                    addReq.setLat(coords[0]);
                    addReq.setLng(coords[1]);
                    log.debug("Applied fallback coords for stop '{}' -> [{}, {}]",
                            aiStop.getPlaceName(), addReq.getLat(), addReq.getLng());
                }

                // Set imageUrl from enrichment (Wikimedia / Unsplash API / Picsum)
                if (detail != null && detail.getImageUrl() != null) {
                    addReq.setImageUrl(detail.getImageUrl());
                } else {
                    String picsumUrl = "https://picsum.photos/seed/"
                            + URLEncoder.encode(aiStop.getPlaceName(), StandardCharsets.UTF_8) + "/800/600";
                    addReq.setImageUrl(picsumUrl);
                    log.info("Picsum fallback for '{}': {}", aiStop.getPlaceName(), picsumUrl);
                }

                log.info("Enriching stop: {} → imageUrl: {}", aiStop.getPlaceName(), addReq.getImageUrl());

                tripService.addStop(tripId, userId, addReq);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save stop '{}': {}", aiStop.getPlaceName(), e.getMessage());
            }
        }
        return saved;
    }

    private Instant calculateArrivalAt(int day, String timeOfDay) {
        int hour = switch (timeOfDay == null ? "" : timeOfDay.toLowerCase()) {
            case "morning" -> 8;
            case "afternoon" -> 13;
            case "evening" -> 18;
            default -> 9;
        };
        return LocalDate.now()
            .plusDays(day - 1)
            .atTime(hour, 0)
            .toInstant(ZoneOffset.UTC);
    }

    private void failJob(AiJob job, String message) {
        job.setStatus(AiJobStatus.FAILED);
        job.setError(message);
        jobRepo.save(job);
    }

    private double[] getDefaultCityCoordinates(String city) {
        // Default coordinates for common cities, with fallback to a generic center point
        return switch((city == null ? "" : city).toLowerCase()) {
            case "hanoi", "ha noi", "hà nội" -> new double[]{21.0285, 105.8542};
            case "ho chi minh", "ho chi minh city", "hcmc", "saigon", "sai gon", "sài gòn" -> new double[]{10.7769, 106.7009};
            case "da nang", "đà nẵng" -> new double[]{16.0544, 108.2022};
            case "hoi an", "hội an" -> new double[]{15.8801, 108.3380};
            case "hue", "huế" -> new double[]{16.4637, 107.5909};
            case "da lat", "đà lạt" -> new double[]{11.9404, 108.4583};
            case "nha trang" -> new double[]{12.2388, 109.1967};
            case "sapa", "sa pa" -> new double[]{22.3364, 103.8438};
            case "ha long", "hạ long" -> new double[]{20.9712, 107.0448};
            case "phu quoc", "phú quốc" -> new double[]{10.2899, 103.9840};
            case "can tho", "cần thơ" -> new double[]{10.0452, 105.7469};
            case "vung tau", "vũng tàu" -> new double[]{10.4114, 107.1362};
            case "bangkok" -> new double[]{13.7563, 100.5018};
            case "paris" -> new double[]{48.8566, 2.3522};
            case "london" -> new double[]{51.5074, -0.1278};
            case "new york" -> new double[]{40.7128, -74.0060};
            case "tokyo" -> new double[]{35.6762, 139.6503};
            default -> new double[]{16.0, 106.0}; // Center Vietnam fallback
        };
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null
                && Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0.0 && lng != 0.0;
    }

    private String buildPrompt(GenerateTripRequest req) {
        String interests = req.getInterests() == null || req.getInterests().isEmpty()
                ? "general sightseeing"
                : String.join(", ", req.getInterests());

        return """
            IMPORTANT: Generate a REAL trip plan for %s, NOT the example below.
            You are a travel planner. Generate exactly 3 different trip plans from %s to %s for %d days.
            Traveler interests: %s. Respond in %s.

            Return ONLY a valid JSON array of 3 plan objects (no markdown, no code fences, no extra text).
            Each plan must have exactly these fields:
            - name (string, creative plan name e.g. "Adventure Explorer")
            - badge (string, one of: "AI Recommended", "Budget Friendly", "Premium Experience")
            - budget (string, estimated total e.g. "$95")
            - duration (string, e.g. "3 days")
            - accommodation (string, e.g. "Mountain Hostel")
            - foodStyle (string, e.g. "Street food + local")
            - timeline (array of day objects)

            Each day object:
            - day (integer, 1-based)
            - items (array of stop objects)

            Each stop object MUST include ALL 6 of these fields (no exceptions):
            - "time" (REQUIRED: "Morning", "Afternoon", or "Evening")
            - "activity" (REQUIRED: place name string)
            - "description" (REQUIRED: short description string)
            - "type" (REQUIRED: "sight", "food", "travel", or "stay")
            - "lat" (REQUIRED: decimal degrees latitude, e.g. 11.9404)
            - "lng" (REQUIRED: decimal degrees longitude, e.g. 108.4385)

            IMPORTANT: Every single item MUST have lat and lng fields with real GPS coordinates. Items without lat/lng will be rejected.

            The 3 plans should differ in style:
            Plan 1 (Adventure): active, outdoor, budget %s
            Plan 2 (Budget): affordable, local experiences
            Plan 3 (Premium): luxury, comfort, fine dining

            Example output (follow this exact structure):
            [
              {
                "name": "Hanoi Capital Explorer",
                "badge": "AI Recommended",
                "budget": "$120",
                "duration": "%d days",
                "accommodation": "Old Quarter Hostel",
                "foodStyle": "Street food + local cafe",
                "timeline": [
                  {
                    "day": 1,
                    "items": [
                      { "time": "Morning",   "activity": "Hoan Kiem Lake",       "description": "Morning walk around the scenic lake", "type": "sight", "lat": 21.0285, "lng": 105.8542 },
                      { "time": "Afternoon", "activity": "Old Quarter",          "description": "Explore the ancient streets",         "type": "sight", "lat": 21.0340, "lng": 105.8500 },
                      { "time": "Evening",   "activity": "Ta Hien Beer Street",  "description": "Local food and drinks",               "type": "food",  "lat": 21.0345, "lng": 105.8519 }
                    ]
                  },
                  {
                    "day": 2,
                    "items": [
                      { "time": "Morning",   "activity": "Temple of Literature", "description": "Visit the first university",          "type": "sight", "lat": 21.0236, "lng": 105.8357 },
                      { "time": "Afternoon", "activity": "Ho Chi Minh Mausoleum", "description": "Historic landmark",                  "type": "sight", "lat": 21.0366, "lng": 105.8344 },
                      { "time": "Evening",   "activity": "West Lake",            "description": "Sunset views and dinner",             "type": "sight", "lat": 21.0560, "lng": 105.8239 }
                    ]
                  }
                ]
              }
            ]
            """.formatted(
                req.getDestination(),
                req.getOrigin(), req.getDestination(), req.getDays(),
                interests, req.getLanguage(),
                req.getBudget(),
                req.getDays()
        ).trim();
    }
}
