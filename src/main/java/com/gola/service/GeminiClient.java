package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.GolaProperties;
import com.gola.exception.GolaException;
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
public class GeminiClient {

    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta";

    private final GolaProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiClient(GolaProperties properties,
                       @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateContent(String prompt) {
        return generateContentWithParts(List.of(Map.of("text", prompt)));
    }

    public String generateContentWithImages(String prompt, List<InlineImagePart> images) {
        var parts = new java.util.ArrayList<Map<String, Object>>();
        parts.add(Map.of("text", prompt));
        for (InlineImagePart image : images) {
            if (image == null || image.dataBase64() == null || image.dataBase64().isBlank()) {
                continue;
            }
            parts.add(Map.of(
                "inline_data", Map.of(
                    "mime_type", image.mimeType() == null || image.mimeType().isBlank() ? MediaType.IMAGE_JPEG_VALUE : image.mimeType(),
                    "data", image.dataBase64()
                )
            ));
        }
        return generateContentWithParts(parts);
    }

    private String generateContentWithParts(List<Map<String, Object>> parts) {
        var gemini = properties.getGemini();
        String apiKey = gemini.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw GolaException.badRequest("Gemini API key is not configured");
        }

        String model = gemini.getModel() != null && !gemini.getModel().isBlank()
            ? gemini.getModel()
            : GolaProperties.DEFAULT_GEMINI_MODEL;

        String url = BASE_URL + "/models/" + model + ":generateContent?key=" + apiKey;

        var body = Map.of(
            "contents", List.of(
                Map.of("parts", parts)
            )
        );

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return extractText(response.getBody());
        } catch (RestClientResponseException e) {
            String error = summarizeError(e.getResponseBodyAsString());
            log.error("Gemini API error status={} model={} error={}", e.getStatusCode(), model, error);
            if (isModelNotFound(error)) {
                throw GolaException.badRequest("Gemini model is unavailable or not supported for generateContent: " + model);
            }
            throw GolaException.badRequest("Gemini API error: " + error);
        } catch (Exception e) {
            String safeError = safeProviderError(e);
            log.error("Gemini API call failed: {}", safeError);
            throw GolaException.badRequest("Gemini API call failed: " + safeError);
        }
    }

    public record InlineImagePart(String mimeType, String dataBase64) {}

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

    private boolean isModelNotFound(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase();
        return lower.contains("not found")
            || lower.contains("not supported for generatecontent")
            || lower.contains("model") && lower.contains("generatecontent");
    }

    private String safeProviderError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message
                .replaceAll("(?i)(key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(api_key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("https://generativelanguage\\.googleapis\\.com/[^\\s\\\"]+", "https://generativelanguage.googleapis.com/***");
    }
}
