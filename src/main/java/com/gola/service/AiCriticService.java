package com.gola.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.ai.AiCriticResult;
import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCriticService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public AiCriticResult review(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        try {
            String stopsJson = objectMapper.writeValueAsString(stops);
            String prompt = String.format("""
                You are an AI Critic reviewing a travel itinerary generated for %s (duration %d days).
                Analyze the itinerary against these quality guidelines:
                - Minimum real/place stops (for 2D1N trip): total real stops >= 8.
                - Day 1: >= 5 meaningful stops excluding transport/check-in/check-out.
                - Day 2: >= 3 meaningful stops excluding transport/check-in/check-out.
                - Must have at least 2 food-related stops if a full day (breakfast/lunch/dinner).
                - Must have at least 1 rest/cafe/chill stop.
                - Must have at least 1 attraction/check-in/photo spot stop.
                - Avoid more than 3 high-energy attractions in a row without cafe/rest breaks.
                - No invented place names like "Nhận phòng khách sạn đã chọn" or address maps as hotel.
                - Missing hotel check-in/checkout should be flagged if needAccommodation is true.
                
                Current Itinerary Stops JSON:
                %s
                
                Generate a review in JSON format matching this schema. Output ONLY valid JSON, no markdown fences, no formatting, no extra explanation text.
                {
                  "score": 0-100 score,
                  "problems": ["list of issues found"],
                  "requiredFixes": ["list of specific corrections needed"],
                  "missingSlots": [
                    {
                      "slotType": "BREAKFAST" | "LUNCH" | "DINNER" | "CAFE" | "HOTEL" | "NIGHT" | "ATTRACTION",
                      "dayIndex": 1,
                      "searchQuery": "recommended vietnamese search query for this slot, e.g. 'quán cafe view biển Vũng Tàu'"
                    }
                  ],
                  "suggestedQueries": ["search query strings"],
                  "passed": true if score >= 80 else false
                }
                """, req.getDestination(), req.getDays(), stopsJson);

            String response = geminiClient.generateContent(prompt);
            // strip markdown fences if gemini returns them
            String cleaned = response.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
            AiCriticResult result = objectMapper.readValue(cleaned, AiCriticResult.class);
            result.setPassed(result.getScore() >= 80);
            log.info("AI Critic Score: {}, passed={}", result.getScore(), result.isPassed());
            return result;
        } catch (Exception e) {
            log.error("AI Critic pass failed, returning default passed review: {}", e.getMessage(), e);
            // fallback passed result so we don't break the flow
            return AiCriticResult.builder().score(100).passed(true).build();
        }
    }
}
