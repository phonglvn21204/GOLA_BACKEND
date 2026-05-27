package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.trip.AddStopRequest;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripService {
    private final AiJobRepository jobRepo;
    private final AiQuotaService  quotaService;
    private final TripService     tripService;
    private final GeminiClient    geminiClient;
    private final ObjectMapper    objectMapper;

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
            List<AiGeneratedStop> stops = extractStops(plans.get(0));
            int savedStops = saveStops(trip.getId(), userId, stops);
            log.info("Saved {} stops for trip:{}", savedStops, trip.getId());

            // Gắn tripId vào plan đầu tiên
            plans.get(0).put("tripId", trip.getId().toString());

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
            failJob(job, e.getMessage());
            throw GolaException.badRequest("AI generation failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
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
                Object dayValue = ((Map<?, ?>) day).get("day");
                int dayNum = dayValue == null ? 1 : ((Number) dayValue).intValue();
                Object itemsObj = ((Map<?, ?>) day).get("items");
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

                    result.add(stop);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to extract stops from plan: {}", e.getMessage());
            return List.of();
        }
    }

    private int saveStops(UUID tripId, UUID userId, List<AiGeneratedStop> stops) {
        int saved = 0;
        for (AiGeneratedStop aiStop : stops) {
            if (aiStop.getPlaceName() == null || aiStop.getPlaceName().isBlank()) continue;
            try {
                var addReq = new AddStopRequest();
                addReq.setName(aiStop.getPlaceName().trim());
                addReq.setNotes(AiItineraryParser.buildNotes(aiStop));
                addReq.setOrderIdx(AiItineraryParser.orderIdxFor(aiStop));
                tripService.addStop(tripId, userId, addReq);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save stop '{}': {}", aiStop.getPlaceName(), e.getMessage());
            }
        }
        return saved;
    }

    private void failJob(AiJob job, String message) {
        job.setStatus(AiJobStatus.FAILED);
        job.setError(message);
        jobRepo.save(job);
    }

    private String buildPrompt(GenerateTripRequest req) {
        String interests = req.getInterests() == null || req.getInterests().isEmpty()
                ? "general sightseeing"
                : req.getInterests().stream().collect(Collectors.joining(", "));

        return """
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

            Each stop object:
            - time (string: "Morning", "Afternoon", or "Evening")
            - activity (string, place name)
            - description (string, short description)
            - type (string, one of: "sight", "food", "travel", "stay")

            The 3 plans should differ in style:
            Plan 1 (Adventure): active, outdoor, budget %s
            Plan 2 (Budget): affordable, local experiences
            Plan 3 (Premium): luxury, comfort, fine dining

            Example:
            [
              {
                "name": "Adventure Explorer",
                "badge": "AI Recommended",
                "budget": "$95",
                "duration": "%d days",
                "accommodation": "Mountain Hostel",
                "foodStyle": "Street food + local",
                "timeline": [
                  {
                    "day": 1,
                    "items": [
                      { "time": "Morning", "activity": "Da Nang Beach", "description": "Start with a swim", "type": "sight" },
                      { "time": "Afternoon", "activity": "Marble Mountains", "description": "Explore caves", "type": "sight" },
                      { "time": "Evening", "activity": "Han River Night Market", "description": "Street food dinner", "type": "food" }
                    ]
                  }
                ]
              }
            ]
            """.formatted(
                req.getOrigin(), req.getDestination(), req.getDays(),
                interests, req.getLanguage(),
                req.getBudget(),
                req.getDays()
        ).trim();
    }
}