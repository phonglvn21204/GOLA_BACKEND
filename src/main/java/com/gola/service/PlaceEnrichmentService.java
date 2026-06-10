package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gola.dto.map.PlaceDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class PlaceEnrichmentService {

    private final RestTemplate restTemplate;

    @Value("${unsplash.access-key}")
    private String unsplashAccessKey;

    public PlaceEnrichmentService(@Qualifier("nominatimRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PlaceDetail enrich(String placeName, String city) {
        String cleanedName = cleanPlaceName(placeName);
        log.info("Enriching place: '{}' -> cleaned: '{}' in {}", placeName, cleanedName, city);
        PlaceDetail detail = new PlaceDetail();
        
        try {
            // Bước 1: Nominatim
            String nominatimUrl = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", cleanedName + ", " + city + ", Vietnam")
                    .queryParam("format", "json")
                    .queryParam("limit", "3")
                    .queryParam("addressdetails", "1")
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode[]> response = restTemplate.getForEntity(nominatimUrl, JsonNode[].class);

            // Bắt buộc sleep theo ToS
            Thread.sleep(1000);

            JsonNode[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.info("Nominatim result for '{}': NULL", placeName);
                return null; // Không tìm thấy
            }

            JsonNode nominatimResult = pickBestResult(body);
            if (nominatimResult == null) {
                log.info("Nominatim result for '{}': NULL after scoring", placeName);
                return null;
            }
            Double lat = coordinateOrNull(nominatimResult.path("lat").asText(null));
            Double lng = coordinateOrNull(nominatimResult.path("lon").asText(null));
            if (lat == null || lng == null) {
                log.info("Nominatim result for '{}': invalid coords lat={} lng={}", placeName,
                    nominatimResult.path("lat").asText(null), nominatimResult.path("lon").asText(null));
                return null;
            }
            if (!isValidVietnamCoordinate(lat, lng)) {
                log.warn("Nominatim result for '{}': coords outside Vietnam bounds lat={} lng={}", placeName, lat, lng);
                return null;
            }
            detail.setLat(lat);
            detail.setLng(lng);
            
            String osmId = nominatimResult.path("osm_id").asText();
            String osmType = nominatimResult.path("osm_type").asText();
            log.info("Nominatim result for '{}': lat={} lng={} osmId={}", placeName, 
                detail.getLat(), detail.getLng(), osmId);
            detail.setAddress(nominatimResult.path("display_name").asText());

            // Bước 2: Overpass API
            String overpassType = mapOsmType(osmType);
            if (overpassType != null && !osmId.isEmpty()) {
                String overpassQuery = "[out:json];" + overpassType + "(" + osmId + ");out tags;";
                String overpassUrl = UriComponentsBuilder.fromHttpUrl("https://overpass-api.de/api/interpreter")
                        .queryParam("data", overpassQuery)
                        .build()
                        .toUriString();

                try {
                    Thread.sleep(2000);
                    ResponseEntity<JsonNode> overpassResponse = restTemplate.getForEntity(overpassUrl, JsonNode.class);
                    JsonNode elements = overpassResponse.getBody() != null ? overpassResponse.getBody().path("elements") : null;
                    if (elements != null && elements.isArray() && !elements.isEmpty()) {
                        JsonNode tags = elements.get(0).path("tags");
                        if (!tags.isMissingNode()) {
                            detail.setName(tags.path("name:vi").isMissingNode() ? tags.path("name").asText(null) : tags.path("name:vi").asText());
                            detail.setNameEn(tags.path("name:en").asText(null));
                            detail.setOpeningHours(tags.path("opening_hours").asText(null));
                            detail.setPhone(tags.path("phone").asText(null));
                            detail.setWebsite(tags.path("website").asText(null));
                            
                            if (!tags.path("fee").isMissingNode()) {
                                detail.setHasFee("yes".equalsIgnoreCase(tags.path("fee").asText()));
                            }
                            if (!tags.path("wheelchair").isMissingNode()) {
                                String wheelchair = tags.path("wheelchair").asText();
                                detail.setWheelchair("yes".equalsIgnoreCase(wheelchair) || "limited".equalsIgnoreCase(wheelchair));
                            }
                            detail.setWikidataId(tags.path("wikidata").asText(null));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Overpass API failed for {}: {}", placeName, e.getMessage());
                }
            }
            log.info("Overpass result for '{}': wikidataId={}", placeName,
                detail.getWikidataId() != null ? detail.getWikidataId() : "NULL");

            // Bước 3: Wikidata
            String wikidataId = detail.getWikidataId();
            String imageFileName = null;
            if (wikidataId != null && !wikidataId.isEmpty()) {
                String wikidataUrl = UriComponentsBuilder.fromHttpUrl("https://www.wikidata.org/w/api.php")
                        .queryParam("action", "wbgetclaims")
                        .queryParam("entity", wikidataId)
                        .queryParam("property", "P18")
                        .queryParam("format", "json")
                        .build()
                        .toUriString();

                try {
                    ResponseEntity<JsonNode> wikidataResponse = restTemplate.getForEntity(wikidataUrl, JsonNode.class);
                    JsonNode claims = wikidataResponse.getBody() != null ? wikidataResponse.getBody().path("claims").path("P18") : null;
                    if (claims != null && claims.isArray() && !claims.isEmpty()) {
                        imageFileName = claims.get(0)
                                .path("mainsnak")
                                .path("datavalue")
                                .path("value")
                                .asText(null);
                    }
                } catch (Exception e) {
                    log.warn("Wikidata API failed for {}: {}", placeName, e.getMessage());
                }
            }
            log.info("Wikidata image file for '{}': {}", placeName,
                imageFileName != null ? imageFileName : "NULL");

            // Bước 4: Wikimedia Commons
            if (imageFileName != null && !imageFileName.isEmpty()) {
                String wikimediaUrl = UriComponentsBuilder.fromHttpUrl("https://commons.wikimedia.org/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("titles", "File:" + imageFileName)
                        .queryParam("prop", "imageinfo")
                        .queryParam("iiprop", "url")
                        .queryParam("format", "json")
                        .build()
                        .toUriString();

                try {
                    ResponseEntity<JsonNode> wikimediaResponse = restTemplate.getForEntity(wikimediaUrl, JsonNode.class);
                    JsonNode pages = wikimediaResponse.getBody() != null ? wikimediaResponse.getBody().path("query").path("pages") : null;
                    if (pages != null && pages.isObject()) {
                        JsonNode firstPage = pages.elements().next();
                        JsonNode imageInfo = firstPage.path("imageinfo");
                        if (imageInfo != null && imageInfo.isArray() && !imageInfo.isEmpty()) {
                            detail.setImageUrl(imageInfo.get(0).path("url").asText(null));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Wikimedia API failed for {}: {}", placeName, e.getMessage());
                }
            }

            if (detail.getImageUrl() == null) {
                try {
                    String query = URLEncoder.encode(placeName + " Vietnam", StandardCharsets.UTF_8);
                    String unsplashUrl = "https://api.unsplash.com/photos/random?query="
                            + query + "&orientation=landscape&client_id=" + unsplashAccessKey;

                    ResponseEntity<JsonNode> unsplashResp = restTemplate.getForEntity(unsplashUrl, JsonNode.class);
                    if (unsplashResp.getBody() != null) {
                        String imgUrl = unsplashResp.getBody().path("urls").path("regular").asText(null);
                        if (imgUrl != null) {
                            detail.setImageUrl(imgUrl);
                            log.info("Unsplash fallback image for '{}': {}", placeName, imgUrl);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Unsplash fallback failed for {}: {}", placeName, e.getMessage());
                    detail.setImageUrl("https://picsum.photos/seed/"
                            + URLEncoder.encode(placeName, StandardCharsets.UTF_8) + "/800/600");
                }
            }

            log.info("Final imageUrl for '{}': {}", placeName,
                detail.getImageUrl() != null ? detail.getImageUrl() : "NULL");

            return detail;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted during sleep: {}", e.getMessage());
            return detail;
        } catch (Exception e) {
            log.warn("Place enrichment failed for {}: {}", placeName, e.getMessage());
            // Return whatever we have populated so far
            return detail.getLat() != null ? detail : null;
        }
    }

    private String mapOsmType(String nominatimType) {
        if ("way".equalsIgnoreCase(nominatimType)) return "way";
        if ("node".equalsIgnoreCase(nominatimType)) return "node";
        if ("relation".equalsIgnoreCase(nominatimType)) return "rel";
        return null;
    }

    private String cleanPlaceName(String placeName) {
        if (placeName == null) return "";

        String cleaned = placeName
                .replaceAll("(?i)\\btravel\\s+(to|from|back\\s+to)\\b.*", "")
                .replaceAll("(?i)\\b(depart|departure|return|check-in|check-out)\\b.*", "")
                .replaceAll("(?i)\\bho\\s+chi\\s+minh\\s+to\\b", "")
                .replaceAll("(?i)\\bfrom\\s+\\w+\\s+to\\s+", "")
                .replaceAll("(?i)\\bday\\s+\\d+\\b", "")
                .replaceAll("(?i)\\b(morning|afternoon|evening)\\s+(at|in|near)\\b", "")
                .trim();

        return cleaned.length() >= 3 ? cleaned : placeName;
    }

    private JsonNode pickBestResult(JsonNode[] results) {
        if (results == null || results.length == 0) return null;

        Map<String, Integer> classScore = Map.of(
                "tourism", 10,
                "amenity", 9,
                "historic", 8,
                "leisure", 7,
                "natural", 6,
                "shop", 5,
                "place", 2,
                "boundary", 1,
                "administrative", 0
        );

        JsonNode best = null;
        int bestScore = -1;
        for (JsonNode node : results) {
            String cls = node.path("class").asText("");
            int score = classScore.getOrDefault(cls, 3);
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best != null ? best : results[0];
    }

    private Double coordinateOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw);
            return value == 0.0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidVietnamCoordinate(double lat, double lng) {
        return lat >= 8.0 && lat <= 24.0 && lng >= 102.0 && lng <= 110.0;
    }
}
