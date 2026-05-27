package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.GolaProperties;
import com.gola.exception.GolaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClient {

    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta";

    private final GolaProperties properties;
    @Qualifier("geminiRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String generateContent(String prompt) {
        var gemini = properties.getGemini();
        String apiKey = gemini.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw GolaException.badRequest("Gemini API key is not configured");
        }

        String model = gemini.getModel() != null && !gemini.getModel().isBlank()
            ? gemini.getModel()
            : "gemini-2.5-flash";

        String url = BASE_URL + "/models/" + model + ":generateContent?key=" + apiKey;

        var body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            )
        );

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return extractText(response.getBody());
        } catch (RestClientResponseException e) {
            log.error("Gemini API error status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw GolaException.badRequest("Gemini API error: " + summarizeError(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw GolaException.badRequest("Gemini API call failed: " + e.getMessage());
        }
    }

    private String extractText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw GolaException.badRequest("Gemini API returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                JsonNode error = root.path("error").path("message");
                if (!error.isMissingNode()) {
                    throw GolaException.badRequest("Gemini API error: " + error.asText());
                }
                throw GolaException.badRequest("Gemini API returned no candidates");
            }
            JsonNode textNode = candidates.get(0).path("content").path("parts");
            if (!textNode.isArray() || textNode.isEmpty()) {
                throw GolaException.badRequest("Gemini API returned no text parts");
            }
            String text = textNode.get(0).path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw GolaException.badRequest("Gemini API returned empty text");
            }
            return text.trim();
        } catch (GolaException e) {
            throw e;
        } catch (Exception e) {
            throw GolaException.badRequest("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private String summarizeError(String body) {
        if (body == null || body.isBlank()) {
            return "unknown error";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error").path("message");
            if (!error.isMissingNode()) {
                return error.asText();
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}