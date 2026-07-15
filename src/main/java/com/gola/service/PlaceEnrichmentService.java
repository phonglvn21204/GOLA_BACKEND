package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gola.config.GolaProperties;
import com.gola.dto.map.PlaceDetail;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PlaceEnrichmentService {
    private static final String IMAGE_SOURCE_WIKIMEDIA = "WIKIMEDIA";
    private static final String IMAGE_SOURCE_SERPAPI = "SERPAPI";
    private static final String IMAGE_SOURCE_CATEGORY_FALLBACK = "CATEGORY_FALLBACK";
    private static final String WIKIMEDIA_USER_AGENT = "GOLA-Travel/1.0 (student-demo; contact: no-reply@gola.local)";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Set<String> SERPAPI_ELIGIBLE_CATEGORIES = Set.of(
            "SIGHTSEEING", "FOOD", "CAFE", "MARKET", "HOTEL", "HOMESTAY", "ACCOMMODATION"
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "vietnam", "viet", "nam", "city", "near", "khu", "tai",
            "dia", "diem", "moi", "nhan", "phong", "tra", "gui", "hanh", "ly", "gan", "den"
    );

    private final RestTemplate nominatimRestTemplate;
    private final RestTemplate overpassRestTemplate;
    private final RestTemplate apiRestTemplate;
    private final GolaProperties properties;
    private final Map<String, PlaceDetail> enrichmentCache = new ConcurrentHashMap<>();
    private final AtomicBoolean missingSerpApiKeyLogged = new AtomicBoolean(false);

    public PlaceEnrichmentService(
            @Qualifier("nominatimRestTemplate") RestTemplate nominatimRestTemplate,
            @Qualifier("overpassRestTemplate") RestTemplate overpassRestTemplate,
            @Qualifier("goongRestTemplate") RestTemplate apiRestTemplate,
            GolaProperties properties) {
        this.nominatimRestTemplate = nominatimRestTemplate;
        this.overpassRestTemplate = overpassRestTemplate;
        this.apiRestTemplate = apiRestTemplate;
        this.properties = properties;
    }

    @PostConstruct
    void logSerpApiConfigured() {
        log.info("SerpApi configured: {}", isSerpApiConfigured());
    }

    public PlaceDetail enrich(String placeName, String city) {
        return enrichForStop(placeName, city, null, null, null, SerpApiBudget.single());
    }

    public PlaceDetail enrichForStop(
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            SerpApiBudget serpApiBudget) {
        return enrichForStop(placeName, city, category, preferredLat, preferredLng, null, null, serpApiBudget);
    }

    public PlaceDetail enrichForStop(
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng,
            SerpApiBudget serpApiBudget) {
        if (placeName == null || placeName.isBlank()) return null;

        String cleanedName = cleanPlaceName(placeName);
        String normalizedCategory = normalizeCategory(category);
        String cacheKey = cacheKey(cleanedName, city, normalizedCategory);
        PlaceDetail cached = enrichmentCache.get(cacheKey);
        if (cached != null) return cached;

        PlaceDetail detail = PlaceDetail.builder()
                .name(cleanedName)
                .imageSource(IMAGE_SOURCE_CATEGORY_FALLBACK)
                .dataSource("NONE")
                .enrichmentStatus("FAILED")
                .hasRealPhoto(false)
                .hasRealCoordinates(false)
                .hasOpeningHours(false)
                .build();

        log.info("Enriching place: '{}' -> cleaned: '{}' in {}", placeName, cleanedName, city);

        String providerQuery = buildProviderQuery(cleanedName, city, normalizedCategory);

        boolean canUseSerpApi = shouldUseSerpApi(normalizedCategory);
        if (isAccommodationCategory(normalizedCategory) && serpApiBudget != null) {
            applySerpApiHotelFallback(detail, cleanedName, city, normalizedCategory, preferredLat, preferredLng, destinationLat, destinationLng, serpApiBudget);
        } else if (canUseSerpApi && serpApiBudget != null) {
            applySerpApiFallback(detail, cleanedName, city, normalizedCategory, preferredLat, preferredLng, destinationLat, destinationLng, serpApiBudget);
        }

        if (!isTrustedCoordinateSource(detail.getDataSource()) || detail.getPlaceAddress() == null || detail.getOpeningHours() == null) {
            enrichNominatimAndWikidata(detail, providerQuery, city, normalizedCategory);
        }

        if (!IMAGE_SOURCE_SERPAPI.equalsIgnoreCase(String.valueOf(detail.getImageSource()))
                && shouldUseWikimediaImage(cleanedName, city, normalizedCategory)) {
            String wikiImage = findWikipediaImage(cleanedName, city, normalizedCategory);
            if (isUsefulWikimediaImageUrl(wikiImage) && isWikimediaImageAllowed(wikiImage, cleanedName, city, normalizedCategory)) {
                detail.setImageUrl(wikiImage);
                detail.setImageSource(IMAGE_SOURCE_WIKIMEDIA);
                detail.setHasRealPhoto(isExactLandmarkCategory(normalizedCategory));
            }
        }

        if (detail.getPlaceAddress() == null) {
            detail.setPlaceAddress(detail.getAddress());
        }
        if (detail.getImageUrl() == null) {
            detail.setImageSource(IMAGE_SOURCE_CATEGORY_FALLBACK);
            detail.setHasRealPhoto(false);
        }
        if (!isValidVietnamCoordinate(detail.getLat(), detail.getLng())
                && IMAGE_SOURCE_WIKIMEDIA.equalsIgnoreCase(String.valueOf(detail.getImageSource()))) {
            detail.setDataSource("WIKIMEDIA_IMAGE_ONLY");
        }
        detail.setHasRealCoordinates(isValidVietnamCoordinate(detail.getLat(), detail.getLng()));
        detail.setHasRealPhoto(isRealPhoto(detail, normalizedCategory));
        detail.setHasOpeningHours(detail.getOpeningHours() != null && !detail.getOpeningHours().isBlank());
        detail.setEnrichmentStatus(resolveEnrichmentStatus(detail));

        enrichmentCache.put(cacheKey, detail);
        log.info("Final enrichment for '{}': imageSource={} hasImage={} rating={} reviews={}",
                placeName,
                detail.getImageSource(),
                detail.getImageUrl() != null,
                detail.getRating(),
                detail.getReviewCount());
        return detail;
    }

    private String resolveEnrichmentStatus(PlaceDetail detail) {
        boolean hasCoords = isValidVietnamCoordinate(detail.getLat(), detail.getLng());
        boolean hasProviderData = Boolean.TRUE.equals(detail.getHasRealPhoto())
                || detail.getRating() != null
                || detail.getReviewCount() != null
                || Boolean.TRUE.equals(detail.getHasOpeningHours())
                || (detail.getPlaceAddress() != null && !detail.getPlaceAddress().isBlank());
        if (hasCoords && hasProviderData) return "ENRICHED";
        if (hasCoords) return "PARTIAL_WITH_COORDINATES";
        if (hasProviderData) return "PARTIAL";
        return "FAILED";
    }

    private void enrichNominatimAndWikidata(PlaceDetail detail, String placeName, String city, String category) {
        try {
            String nominatimUrl = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", placeName + ", " + nullToEmpty(city) + ", Vietnam")
                    .queryParam("format", "json")
                    .queryParam("limit", "3")
                    .queryParam("addressdetails", "1")
                    .build()
                    .toUriString();

            ResponseEntity<JsonNode[]> response = nominatimRestTemplate.getForEntity(nominatimUrl, JsonNode[].class);
            Thread.sleep(1000);

            JsonNode[] body = response.getBody();
            if (body == null || body.length == 0) {
                log.info("Nominatim result for '{}': NULL", placeName);
                return;
            }

            JsonNode nominatimResult = pickBestResult(body);
            if (nominatimResult == null) return;

            Double lat = coordinateOrNull(nominatimResult.path("lat").asText(null));
            Double lng = coordinateOrNull(nominatimResult.path("lon").asText(null));
            if (!isTrustedCoordinateSource(detail.getDataSource()) && lat != null && lng != null && isValidVietnamCoordinate(lat, lng)) {
                detail.setLat(lat);
                detail.setLng(lng);
                detail.setDataSource("NOMINATIM");
                detail.setHasRealCoordinates(true);
            }

            String displayName = nominatimResult.path("display_name").asText(null);
            if (displayName != null && !displayName.isBlank()) {
                detail.setAddress(displayName);
                detail.setPlaceAddress(displayName);
            }

            enrichOverpassTags(detail, placeName, nominatimResult.path("osm_id").asText(), nominatimResult.path("osm_type").asText());
            if (detail.getImageUrl() == null
                    && detail.getWikidataId() != null
                    && !detail.getWikidataId().isBlank()
                    && shouldUseWikimediaImage(placeName, city, category)) {
                String image = findWikidataImage(detail.getWikidataId());
                if (isUsefulWikimediaImageUrl(image) && isWikimediaImageAllowed(image, placeName, city, category)) {
                    detail.setImageUrl(image);
                    detail.setImageSource(IMAGE_SOURCE_WIKIMEDIA);
                    detail.setHasRealPhoto(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Place enrichment interrupted for '{}': {}", placeName, e.getMessage());
        } catch (Exception e) {
            log.warn("Nominatim/Wikidata enrichment failed for '{}': {}", placeName, e.getMessage());
        }
    }

    private void enrichOverpassTags(PlaceDetail detail, String placeName, String osmId, String osmType) {
        String overpassType = mapOsmType(osmType);
        if (overpassType == null || osmId == null || osmId.isBlank()) return;

        String overpassQuery = "[out:json];" + overpassType + "(" + osmId + ");out tags;";
        String overpassUrl = UriComponentsBuilder.fromHttpUrl("https://overpass-api.de/api/interpreter")
                .queryParam("data", overpassQuery)
                .build()
                .toUriString();

        try {
            Thread.sleep(1000);
            ResponseEntity<JsonNode> overpassResponse = overpassRestTemplate.getForEntity(overpassUrl, JsonNode.class);
            JsonNode elements = overpassResponse.getBody() != null ? overpassResponse.getBody().path("elements") : null;
            if (elements == null || !elements.isArray() || elements.isEmpty()) return;

            JsonNode tags = elements.get(0).path("tags");
            if (tags.isMissingNode()) return;

            String viName = tags.path("name:vi").asText(null);
            String name = viName != null && !viName.isBlank() ? viName : tags.path("name").asText(null);
            if (name != null && !name.isBlank()) detail.setName(name);
            detail.setNameEn(tags.path("name:en").asText(null));
            detail.setOpeningHours(tags.path("opening_hours").asText(null));
            if (detail.getOpeningHours() != null && !detail.getOpeningHours().isBlank()) {
                detail.setHasOpeningHours(true);
            }
            detail.setPhone(tags.path("phone").asText(null));
            detail.setWebsite(tags.path("website").asText(null));
            detail.setWikidataId(tags.path("wikidata").asText(null));

            if (!tags.path("fee").isMissingNode()) {
                detail.setHasFee("yes".equalsIgnoreCase(tags.path("fee").asText()));
            }
            if (!tags.path("wheelchair").isMissingNode()) {
                String wheelchair = tags.path("wheelchair").asText();
                detail.setWheelchair("yes".equalsIgnoreCase(wheelchair) || "limited".equalsIgnoreCase(wheelchair));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Overpass enrichment interrupted for '{}': {}", placeName, e.getMessage());
        } catch (Exception e) {
            log.warn("Overpass API failed for '{}': {}", placeName, e.getMessage());
        }
    }

    private String findWikipediaImage(String placeName, String city, String category) {
        String query = String.join(" ", placeName, nullToEmpty(city), "Vietnam").trim();
        for (String lang : List.of("vi", "en")) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://" + lang + ".wikipedia.org/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("generator", "search")
                        .queryParam("gsrsearch", query)
                        .queryParam("gsrlimit", "3")
                        .queryParam("prop", "pageimages")
                        .queryParam("piprop", "thumbnail|original")
                        .queryParam("pithumbsize", "900")
                        .queryParam("format", "json")
                        .build()
                        .toUriString();

                JsonNode body = getWikimediaJson(url);
                JsonNode pages = body != null ? body.path("query").path("pages") : null;
                if (pages == null || !pages.isObject()) continue;

                List<JsonNode> candidates = new ArrayList<>();
                pages.elements().forEachRemaining(candidates::add);
                candidates.sort(Comparator.comparingInt(node -> -wikipediaTitleScore(node.path("title").asText(""), placeName, city)));

                for (JsonNode page : candidates) {
                    String image = page.path("thumbnail").path("source").asText(null);
                    if (!isUsefulImageUrl(image)) {
                        image = page.path("original").path("source").asText(null);
                    }
                    if (isUsefulWikimediaImageUrl(image) && isWikimediaImageAllowed(image, placeName, city, category)) {
                        log.info("Wikipedia image found for '{}' via {}wiki", placeName, lang);
                        return image;
                    }
                }
            } catch (Exception e) {
                log.warn("Wikipedia image lookup failed for '{}' on {}wiki: {}", placeName, lang, e.getMessage());
            }
        }
        return null;
    }

    private int wikipediaTitleScore(String title, String placeName, String city) {
        String haystack = normalizeText(title + " " + nullToEmpty(city));
        int score = 0;
        for (String token : significantTokens(placeName)) {
            if (haystack.contains(token)) score++;
        }
        return score;
    }

    private String findWikidataImage(String wikidataId) {
        try {
            String wikidataUrl = UriComponentsBuilder.fromHttpUrl("https://www.wikidata.org/w/api.php")
                    .queryParam("action", "wbgetclaims")
                    .queryParam("entity", wikidataId)
                    .queryParam("property", "P18")
                    .queryParam("format", "json")
                    .build()
                    .toUriString();
            JsonNode wikidataBody = getWikimediaJson(wikidataUrl);
            JsonNode claims = wikidataBody != null ? wikidataBody.path("claims").path("P18") : null;
            if (claims == null || !claims.isArray() || claims.isEmpty()) return null;

            String imageFileName = claims.get(0)
                    .path("mainsnak")
                    .path("datavalue")
                    .path("value")
                    .asText(null);
            if (imageFileName == null || imageFileName.isBlank()) return null;

            String commonsUrl = UriComponentsBuilder.fromHttpUrl("https://commons.wikimedia.org/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("titles", "File:" + imageFileName)
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url")
                    .queryParam("format", "json")
                    .build()
                    .toUriString();
            JsonNode commonsBody = getWikimediaJson(commonsUrl);
            JsonNode pages = commonsBody != null ? commonsBody.path("query").path("pages") : null;
            if (pages != null && pages.isObject()) {
                JsonNode firstPage = pages.elements().next();
                JsonNode imageInfo = firstPage.path("imageinfo");
                if (imageInfo != null && imageInfo.isArray() && !imageInfo.isEmpty()) {
                    return imageInfo.get(0).path("url").asText(null);
                }
            }
        } catch (Exception e) {
            log.warn("Wikidata image lookup failed for '{}': {}", wikidataId, e.getMessage());
        }
        return null;
    }

    private JsonNode getWikimediaJson(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, WIKIMEDIA_USER_AGENT);
        ResponseEntity<JsonNode> response = apiRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );
        return response.getBody();
    }

    private void applySerpApiHotelFallback(
            PlaceDetail detail,
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng,
            SerpApiBudget serpApiBudget) {
        String apiKey = serpApiKey();
        if (apiKey == null) {
            if (missingSerpApiKeyLogged.compareAndSet(false, true)) {
                log.warn("SERPAPI_API_KEY is not configured; skipping SerpApi hotel enrichment");
            }
            return;
        }

        try {
            for (String query : hotelSearchQueries(placeName, city)) {
                if (!serpApiBudget.tryAcquire()) {
                    log.info("SerpApi hotel budget exhausted before query '{}' for '{}'", query, placeName);
                    break;
                }

                log.info("SerpApi hotel search: stopName='{}' searchQuery='{}' engine=google apiKeyMasked=true", placeName, query);
                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromHttpUrl("https://serpapi.com/search.json")
                        .queryParam("engine", "google")
                        .queryParam("q", query)
                        .queryParam("hl", "vi")
                        .queryParam("gl", "vn")
                        .queryParam("location", nullToEmpty(city))
                        .queryParam("api_key", apiKey);

                URI serpUri = builder.build().encode().toUri();
                ResponseEntity<JsonNode> response = apiRestTemplate.getForEntity(serpUri, JsonNode.class);
                JsonNode body = response.getBody();
                int shoppingResultsCount = countHotelShoppingResults(body);
                log.info("SerpApi hotel search result: stopName='{}' hotelSearchCalled=true query='{}' shoppingResultsCount={}",
                        placeName, query, shoppingResultsCount);
                if (shoppingResultsCount == 0) {
                    detail.setRejectedReason("HOTEL_NO_SHOPPING_RESULTS");
                }
                JsonNode candidate = pickBestHotelResult(body, query, city);
                if (candidate == null) {
                    detail.setRejectedReason(shoppingResultsCount == 0
                            ? "HOTEL_NO_SHOPPING_RESULTS"
                            : firstHotelRejectReason(body, query, city, "HOTEL_ALL_CANDIDATES_REJECTED"));
                    log.info("SerpApi hotel returned no accepted shopping result for '{}' rejectReason={}", query, detail.getRejectedReason());
                    continue;
                }

                String title = candidate.path("title").asText(null);
                String source = candidate.path("source").asText(null);
                BigDecimal price = firstMoney(
                        decimalOrNull(candidate.path("extracted_price")),
                        parseMoney(candidate.path("price").asText(null))
                );
                String thumbnail = serpThumbnail(candidate);
                if (title != null && !title.isBlank()) {
                    detail.setName(title);
                    detail.setProviderTitle(title);
                }
                detail.setProviderId(firstText(candidate.path("product_id").asText(null), candidate.path("link").asText(null), title));
                if (isUsefulImageUrl(thumbnail)) {
                    detail.setImageUrl(thumbnail);
                    detail.setImageSource(IMAGE_SOURCE_SERPAPI);
                    detail.setHasRealPhoto(true);
                }
                BigDecimal rating = decimalOrNull(candidate.path("rating"));
                if (rating != null) detail.setRating(rating);
                Integer reviews = integerOrNull(candidate.path("reviews"));
                if (reviews != null) detail.setReviewCount(reviews);
                if (price != null) detail.setEstimatedCost(price);
                if (source != null && !source.isBlank()) {
                    detail.setProviderSource(source);
                    detail.setBusinessStatus(source);
                }
                detail.setPlaceAddress(firstText(candidate.path("address").asText(null), source));
                detail.setEnrichmentStatus("PARTIAL");

                if (title != null && !title.isBlank()) {
                    resolveHotelCoordinatesFromSerpMaps(detail, title, city, category, preferredLat, preferredLng, destinationLat, destinationLng, serpApiBudget);
                }

                log.info("SerpApi hotel selected: stopName='{}' title='{}' source='{}' image={} rating={} reviews={} price={} dataSource={} imageSource={}",
                        placeName,
                        title,
                        source,
                        detail.getImageUrl() != null,
                        detail.getRating(),
                        detail.getReviewCount(),
                        detail.getEstimatedCost(),
                        detail.getDataSource(),
                        detail.getImageSource());
                detail.setRejectedReason(null);
                return;
            }
        } catch (Exception e) {
            log.warn("SerpApi hotel enrichment failed for '{}': {}", placeName, safeProviderError(e));
        }
    }

    private void resolveHotelCoordinatesFromSerpMaps(
            PlaceDetail detail,
            String hotelTitle,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng,
            SerpApiBudget serpApiBudget) {
        if (!serpApiBudget.tryAcquire()) {
            log.info("SerpApi hotel coordinate budget exhausted for '{}'", hotelTitle);
            return;
        }
        String apiKey = serpApiKey();
        if (apiKey == null) return;

        try {
            String query = normalizeProviderQuery(hotelTitle, city);
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl("https://serpapi.com/search.json")
                    .queryParam("engine", "google_maps")
                    .queryParam("q", query)
                    .queryParam("hl", "vi")
                    .queryParam("gl", "vn")
                    .queryParam("api_key", apiKey);
            Double anchorLat = isValidVietnamCoordinate(destinationLat, destinationLng) ? destinationLat : preferredLat;
            Double anchorLng = isValidVietnamCoordinate(destinationLat, destinationLng) ? destinationLng : preferredLng;
            if (isValidVietnamCoordinate(anchorLat, anchorLng)) {
                builder.queryParam("ll", "@" + anchorLat + "," + anchorLng + ",14z");
            } else {
                builder.queryParam("location", nullToEmpty(city));
            }

            URI serpUri = builder.build().encode().toUri();
            ResponseEntity<JsonNode> response = apiRestTemplate.getForEntity(serpUri, JsonNode.class);
            JsonNode result = pickBestSerpResult(response.getBody(), query, city, category, preferredLat, preferredLng, destinationLat, destinationLng);
            if (result == null) return;

            JsonNode gps = result.path("gps_coordinates");
            Double lat = coordinateOrNull(gps.path("latitude").asText(null));
            Double lng = coordinateOrNull(gps.path("longitude").asText(null));
            if (isAcceptableSerpCoordinate(result, hotelTitle, city, category, lat, lng, destinationLat, destinationLng)) {
                detail.setLat(lat);
                detail.setLng(lng);
                detail.setDataSource(IMAGE_SOURCE_SERPAPI);
                detail.setHasRealCoordinates(true);
                String address = result.path("address").asText(null);
                if (address != null && !address.isBlank()) {
                    detail.setAddress(address);
                    detail.setPlaceAddress(address);
                    String addressHotelName = extractHotelNameFromAddress(address, hotelTitle);
                    if (addressHotelName != null) {
                        detail.setName(addressHotelName);
                        detail.setProviderTitle(addressHotelName);
                        log.info("SerpApi hotel title corrected from address: originalTitle='{}' correctedTitle='{}'",
                                hotelTitle, addressHotelName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("SerpApi hotel coordinate lookup failed for '{}': {}", hotelTitle, safeProviderError(e));
        }
    }

    private void applySerpApiFallback(
            PlaceDetail detail,
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng,
            SerpApiBudget serpApiBudget) {
        String apiKey = serpApiKey();
        if (apiKey == null) {
            if (missingSerpApiKeyLogged.compareAndSet(false, true)) {
                log.warn("SERPAPI_API_KEY is not configured; skipping SerpApi place enrichment");
            }
            return;
        }

        try {
            for (String query : serpSearchQueriesForSerpApi(placeName, city, category)) {
                if (!serpApiBudget.tryAcquire()) {
                    log.info("SerpApi budget exhausted before query '{}' for '{}'", query, placeName);
                    break;
                }
                log.info("SerpApi google_maps call: stopName='{}' searchQuery='{}' serpApiCalled=true category={}",
                        placeName, query, category);
                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromHttpUrl("https://serpapi.com/search.json")
                        .queryParam("engine", "google_maps")
                        .queryParam("q", query)
                        .queryParam("hl", "vi")
                        .queryParam("gl", "vn")
                        .queryParam("api_key", apiKey);
                Double anchorLat = isValidVietnamCoordinate(destinationLat, destinationLng) ? destinationLat : preferredLat;
                Double anchorLng = isValidVietnamCoordinate(destinationLat, destinationLng) ? destinationLng : preferredLng;
                if (isValidVietnamCoordinate(anchorLat, anchorLng)) {
                    builder.queryParam("ll", "@" + anchorLat + "," + anchorLng + ",14z");
                    log.info("SerpApi google_maps scope: searchQuery='{}' scope=ll anchor={},{} apiKeyMasked=true",
                            query, anchorLat, anchorLng);
                } else {
                    builder.queryParam("location", nullToEmpty(city));
                    log.info("SerpApi google_maps scope: searchQuery='{}' scope=location location='{}' apiKeyMasked=true",
                            query, nullToEmpty(city));
                }

                URI serpUri = builder.build().encode().toUri();
                ResponseEntity<JsonNode> response = apiRestTemplate.getForEntity(serpUri, JsonNode.class);
                JsonNode body = response.getBody();
                JsonNode result = pickBestSerpResult(body, query, city, category, preferredLat, preferredLng, destinationLat, destinationLng);
                if (result == null) {
                    detail.setRejectedReason(firstSerpRejectReason(body, query, city, category, destinationLat, destinationLng, "NO_ACCEPTED_SERPAPI_CANDIDATE"));
                    log.info("SerpApi returned no accepted Google Maps candidate for stopName='{}' searchQuery='{}'", placeName, query);
                    continue;
                }

                String metadataRejectReason = serpMetadataRejectReason(result, placeName, city, category, destinationLat, destinationLng);
                if (metadataRejectReason != null) {
                    detail.setRejectedReason(metadataRejectReason);
                    log.warn("SerpApi metadata rejected for '{}': reason={} candidate='{}' address='{}'",
                            placeName, metadataRejectReason, result.path("title").asText(""), result.path("address").asText(""));
                    continue;
                }

                String thumbnail = serpThumbnail(result);
                detail.setProviderTitle(result.path("title").asText(null));
                detail.setProviderId(firstText(result.path("data_id").asText(null), result.path("data_cid").asText(null), result.path("place_id").asText(null), result.path("title").asText(null)));
                if (isUsefulImageUrl(thumbnail) && isAcceptableSerpImageResult(result, placeName, city, category)) {
                    detail.setImageUrl(thumbnail);
                    detail.setImageSource(IMAGE_SOURCE_SERPAPI);
                    detail.setHasRealPhoto(true);
                }

                BigDecimal rating = decimalOrNull(result.path("rating"));
                if (detail.getRating() == null && rating != null) {
                    detail.setRating(rating);
                }

                Integer reviews = integerOrNull(result.path("reviews"));
                if (detail.getReviewCount() == null && reviews != null) {
                    detail.setReviewCount(reviews);
                }

                String address = result.path("address").asText(null);
                if (address != null && !address.isBlank()) {
                    if (detail.getPlaceAddress() == null) detail.setPlaceAddress(address);
                    if (detail.getAddress() == null) detail.setAddress(address);
                }

                applySerpOpeningHours(detail, result);

                JsonNode gps = result.path("gps_coordinates");
                Double lat = coordinateOrNull(gps.path("latitude").asText(null));
                Double lng = coordinateOrNull(gps.path("longitude").asText(null));
                if ((!isValidVietnamCoordinate(detail.getLat(), detail.getLng()) || !isTrustedCoordinateSource(detail.getDataSource()))
                        && isValidVietnamCoordinate(lat, lng)) {
                    if (isAcceptableSerpCoordinate(result, placeName, city, category, lat, lng, destinationLat, destinationLng)) {
                        detail.setLat(lat);
                        detail.setLng(lng);
                        detail.setHasRealCoordinates(true);
                        detail.setDataSource(IMAGE_SOURCE_SERPAPI);
                        log.info("SerpApi GPS accepted for '{}' at [{}, {}]", placeName, lat, lng);
                    } else {
                        log.warn("SerpApi GPS rejected for '{}' result='{}' address='{}' at [{}, {}] for destination '{}'",
                                placeName, result.path("title").asText(""), result.path("address").asText(""), lat, lng, city);
                    }
                }

                log.info("SerpApi enrichment final: stopName='{}' searchQuery='{}' candidate='{}' finalDataSource={} finalImageSource={} finalHasRealPhoto={} rating={} reviews={}",
                        placeName,
                        query,
                        result.path("title").asText(""),
                        detail.getDataSource(),
                        detail.getImageSource(),
                        detail.getHasRealPhoto(),
                        detail.getRating(),
                        detail.getReviewCount());
                if (isTrustedCoordinateSource(detail.getDataSource()) || detail.getImageUrl() != null) {
                    detail.setRejectedReason(null);
                    return;
                }
            }
            if (serpApiBudget.tryAcquire()) {
                applySerpApiGoogleSearchFallback(detail, placeName, city, category, destinationLat, destinationLng);
            }
        } catch (Exception e) {
            log.warn("SerpApi place enrichment failed for '{}': {}", placeName, safeProviderError(e));
        }
    }

    private void applySerpApiGoogleSearchFallback(
            PlaceDetail detail,
            String placeName,
            String city,
            String category,
            Double destinationLat,
            Double destinationLng) {
        String apiKey = serpApiKey();
        if (apiKey == null) return;
        for (String query : serpSearchQueriesForSerpApi(placeName, city, category)) {
            try {
                log.info("SerpApi google search fallback: stopName='{}' searchQuery='{}' category={}", placeName, query, category);
                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromHttpUrl("https://serpapi.com/search.json")
                        .queryParam("engine", "google")
                        .queryParam("q", query)
                        .queryParam("hl", "vi")
                        .queryParam("gl", "vn")
                        .queryParam("location", nullToEmpty(city))
                        .queryParam("api_key", apiKey);

                URI serpUri = builder.build().encode().toUri();
                ResponseEntity<JsonNode> response = apiRestTemplate.getForEntity(serpUri, JsonNode.class);
                JsonNode result = pickBestGoogleSearchPlace(response.getBody(), placeName, city, category, destinationLat, destinationLng);
                if (result == null) {
                    log.info("SerpApi google search fallback found no accepted candidate for '{}'", query);
                    continue;
                }
                String thumbnail = serpThumbnail(result);
                detail.setProviderTitle(result.path("title").asText(null));
                detail.setProviderId(firstText(result.path("data_id").asText(null), result.path("data_cid").asText(null), result.path("place_id").asText(null), result.path("title").asText(null)));
                if (isUsefulImageUrl(thumbnail) && isAcceptableSerpImageResult(result, placeName, city, category)) {
                    detail.setImageUrl(thumbnail);
                    detail.setImageSource(IMAGE_SOURCE_SERPAPI);
                    detail.setHasRealPhoto(true);
                }
                BigDecimal rating = decimalOrNull(result.path("rating"));
                if (detail.getRating() == null && rating != null) detail.setRating(rating);
                Integer reviews = integerOrNull(result.path("reviews"));
                if (detail.getReviewCount() == null && reviews != null) detail.setReviewCount(reviews);
                String address = result.path("address").asText(null);
                if (address != null && !address.isBlank()) {
                    if (detail.getPlaceAddress() == null) detail.setPlaceAddress(address);
                    if (detail.getAddress() == null) detail.setAddress(address);
                }
                if (detail.getImageUrl() != null || detail.getRating() != null || detail.getReviewCount() != null) {
                    detail.setRejectedReason(null);
                    log.info("SerpApi google search fallback accepted metadata: stopName='{}' candidate='{}' imageSource={} rating={} reviews={}",
                            placeName, result.path("title").asText(""), detail.getImageSource(), detail.getRating(), detail.getReviewCount());
                    return;
                }
            } catch (Exception e) {
                log.warn("SerpApi google search fallback failed for '{}': {}", query, safeProviderError(e));
            }
        }
    }

    private void applySerpOpeningHours(PlaceDetail detail, JsonNode result) {
        if (result == null || result.isMissingNode() || result.isNull()) return;

        String openState = firstText(
                result.path("open_state").asText(null),
                result.path("hours").asText(null)
        );
        String hoursText = firstText(
                result.path("hours").asText(null),
                result.path("open_state").asText(null),
                stringifyOperatingHours(result.path("operating_hours"))
        );

        if (hoursText != null && !hoursText.isBlank()) {
            detail.setOpeningHours(hoursText);
            detail.setHasOpeningHours(true);
        }
        if (openState != null && !openState.isBlank()) {
            detail.setNextOpenCloseText(openState);
            String normalized = openState.toLowerCase(Locale.ROOT);
            if (normalized.contains("closed") || normalized.contains("đóng") || normalized.contains("dong")) {
                detail.setOpenNow(false);
            } else if (normalized.contains("open") || normalized.contains("mở") || normalized.contains("mo")) {
                detail.setOpenNow(true);
            }
        }
        String businessStatus = result.path("business_status").asText(null);
        if (businessStatus != null && !businessStatus.isBlank()) {
            detail.setBusinessStatus(businessStatus);
        }
    }

    private String stringifyOperatingHours(JsonNode operatingHours) {
        if (operatingHours == null || operatingHours.isMissingNode() || operatingHours.isNull()) return null;
        if (operatingHours.isTextual()) return operatingHours.asText();
        if (operatingHours.isObject()) {
            List<String> lines = new ArrayList<>();
            operatingHours.fields().forEachRemaining(entry -> {
                String value = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
                if (value != null && !value.isBlank()) {
                    lines.add(entry.getKey() + ": " + value);
                }
            });
            return lines.isEmpty() ? null : String.join("; ", lines);
        }
        if (operatingHours.isArray()) {
            List<String> lines = new ArrayList<>();
            operatingHours.forEach(node -> {
                String value = node.isTextual() ? node.asText() : node.toString();
                if (value != null && !value.isBlank()) lines.add(value);
            });
            return lines.isEmpty() ? null : String.join("; ", lines);
        }
        return null;
    }

    private boolean isSerpApiConfigured() {
        return serpApiKey() != null;
    }

    private boolean isTrustedCoordinateSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = source.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "GOONG", "SERPAPI", "MANUAL", "SAVED_PLACE" -> true;
            default -> false;
        };
    }

    private String serpApiKey() {
        if (properties.getSerpapi() == null) return null;
        String apiKey = properties.getSerpapi().getApiKey();
        return apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    private JsonNode pickBestSerpResult(
            JsonNode body,
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng) {
        if (body == null) return null;

        List<JsonNode> candidates = new ArrayList<>();
        JsonNode localResults = body.path("local_results");
        if (localResults.isArray()) {
            localResults.forEach(candidates::add);
        }
        JsonNode placeResults = body.path("place_results");
        if (placeResults.isObject()) {
            candidates.add(placeResults);
        }
        if (candidates.isEmpty()) return null;

        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        int logged = 0;
        for (JsonNode candidate : candidates) {
            int score = serpMatchScore(candidate, placeName, city, category, preferredLat, preferredLng, destinationLat, destinationLng);
            String rejectReason = serpCandidateRejectReason(candidate, placeName, city, category, destinationLat, destinationLng, score);
            boolean accepted = rejectReason == null;
            if (logged < 5) {
                log.info("SerpApi candidate: stopName='{}' searchQuery='{}' title='{}' address='{}' type='{}' dataId='{}' dataCid='{}' hasGps={} hasThumbnail={} rating={} reviews={} candidateScore={} accepted={} rejectedReason={}",
                        placeName,
                        placeName,
                        candidate.path("title").asText(""),
                        candidate.path("address").asText(""),
                        candidate.path("type").asText(""),
                        candidate.path("data_id").asText(""),
                        candidate.path("data_cid").asText(""),
                        hasSerpGps(candidate),
                        isUsefulImageUrl(serpThumbnail(candidate)),
                        candidate.path("rating").asText(""),
                        candidate.path("reviews").asText(""),
                        score,
                        accepted,
                        rejectReason == null ? "" : rejectReason);
                logged++;
            }
            if (accepted && score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private JsonNode pickBestGoogleSearchPlace(
            JsonNode body,
            String placeName,
            String city,
            String category,
            Double destinationLat,
            Double destinationLng) {
        if (body == null) return null;
        List<JsonNode> candidates = new ArrayList<>();
        JsonNode places = body.path("local_results").path("places");
        if (places.isArray()) {
            places.forEach(candidates::add);
        }
        JsonNode localResults = body.path("local_results");
        if (localResults.isArray()) {
            localResults.forEach(candidates::add);
        }
        JsonNode placeResults = body.path("place_results");
        if (placeResults.isObject()) {
            candidates.add(placeResults);
        }
        if (candidates.isEmpty()) return null;

        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        int logged = 0;
        for (JsonNode candidate : candidates) {
            int score = serpMatchScore(candidate, placeName, city, category, null, null, destinationLat, destinationLng);
            String rejectReason = serpCandidateRejectReason(candidate, placeName, city, category, destinationLat, destinationLng, score);
            boolean accepted = rejectReason == null;
            if (logged < 5) {
                log.info("SerpApi google fallback candidate: stopName='{}' title='{}' address='{}' type='{}' hasThumbnail={} rating={} reviews={} score={} accepted={} rejectedReason={}",
                        placeName,
                        candidate.path("title").asText(""),
                        candidate.path("address").asText(""),
                        candidate.path("type").asText(""),
                        isUsefulImageUrl(serpThumbnail(candidate)),
                        candidate.path("rating").asText(""),
                        candidate.path("reviews").asText(""),
                        score,
                        accepted,
                        rejectReason == null ? "" : rejectReason);
                logged++;
            }
            if (accepted && score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private String firstSerpRejectReason(
            JsonNode body,
            String placeName,
            String city,
            String category,
            Double destinationLat,
            Double destinationLng,
            String fallback) {
        if (body == null) return fallback;
        List<JsonNode> candidates = new ArrayList<>();
        JsonNode localResults = body.path("local_results");
        if (localResults.isArray()) localResults.forEach(candidates::add);
        JsonNode places = body.path("local_results").path("places");
        if (places.isArray()) places.forEach(candidates::add);
        JsonNode placeResults = body.path("place_results");
        if (placeResults.isObject()) candidates.add(placeResults);
        for (JsonNode candidate : candidates) {
            int score = serpMatchScore(candidate, placeName, city, category, null, null, destinationLat, destinationLng);
            String reason = serpCandidateRejectReason(candidate, placeName, city, category, destinationLat, destinationLng, score);
            if (reason != null) return reason;
        }
        return fallback;
    }

    private String serpCandidateRejectReason(
            JsonNode candidate,
            String placeName,
            String city,
            String category,
            Double destinationLat,
            Double destinationLng,
            int score) {
        String title = candidate.path("title").asText("");
        String address = candidate.path("address").asText("");
        if (title.isBlank()) return "MISSING_TITLE";
        if (looksBadProviderResult(title + " " + address)) return "BAD_PROVIDER_RESULT";
        String exactKey = exactVenueKey(placeName);
        if (exactKey != null && !exactVenueTitleMatches(exactKey, title)) return "NAME_MISMATCH";
        String metadataRejectReason = serpMetadataRejectReason(candidate, placeName, city, category, destinationLat, destinationLng);
        if (metadataRejectReason != null) return metadataRejectReason;
        if (score < 2) return "LOW_SCORE";
        String normalizedCategory = normalizeCategory(category);
        if ("FOOD".equals(normalizedCategory) && isMarketCandidate(title, address)) {
            return "CATEGORY_MISMATCH";
        }
        if (Set.of("FOOD", "CAFE").contains(normalizedCategory)
                && !looksVenueLike(title, address)
                && !categoryMatchesSerpResult(candidate, normalizedCategory)) {
            return "CATEGORY_MISMATCH";
        }
        return null;
    }

    private String exactVenueKey(String value) {
        String text = normalizeText(value);
        if (text.contains("banh khot") && (text.contains("goc") || text.contains("vu sua"))) return "BANH_KHOT_GOC_VU_SUA";
        if (text.contains("son dang")) return "SON_DANG_COFFEE";
        if (text.contains("ganh hao")) return "GANH_HAO";
        if (text.contains("marina club")) return "MARINA_CLUB";
        if (text.contains("annata beach")) return "ANNATA_BEACH_HOTEL";
        if (text.contains("premier pearl")) return "PREMIER_PEARL";
        if (text.contains("imperial hotel") || text.contains("the imperial")) return "IMPERIAL_HOTEL";
        if (text.contains("malibu hotel")) return "MALIBU_HOTEL";
        return null;
    }

    private boolean exactVenueTitleMatches(String exactKey, String candidateTitle) {
        String title = normalizeText(candidateTitle);
        return switch (exactKey) {
            case "BANH_KHOT_GOC_VU_SUA" -> title.contains("banh khot") && title.contains("goc") && title.contains("vu sua");
            case "SON_DANG_COFFEE" -> title.contains("son dang");
            case "GANH_HAO" -> title.contains("ganh hao");
            case "MARINA_CLUB" -> title.contains("marina club");
            case "ANNATA_BEACH_HOTEL" -> title.contains("annata") && title.contains("beach");
            case "PREMIER_PEARL" -> title.contains("premier pearl");
            case "IMPERIAL_HOTEL" -> title.contains("imperial");
            case "MALIBU_HOTEL" -> title.contains("malibu");
            default -> true;
        };
    }

    private String serpMetadataRejectReason(
            JsonNode candidate,
            String placeName,
            String city,
            String category,
            Double destinationLat,
            Double destinationLng) {
        String title = candidate.path("title").asText("");
        String address = candidate.path("address").asText("");
        if (isOffDestinationAddress(address, city)) {
            return "OFF_DESTINATION";
        }

        JsonNode gps = candidate.path("gps_coordinates");
        Double lat = coordinateOrNull(gps.path("latitude").asText(null));
        Double lng = coordinateOrNull(gps.path("longitude").asText(null));
        if (isValidVietnamCoordinate(lat, lng)
                && !isAcceptableSerpCoordinate(candidate, placeName, city, category, lat, lng, destinationLat, destinationLng)) {
            return "OFF_DESTINATION";
        }

        if (!isValidVietnamCoordinate(lat, lng)
                && address != null
                && !address.isBlank()
                && !addressStronglyMatchesDestination(address, city)) {
            return "OFF_DESTINATION";
        }

        if (Set.of("FOOD", "CAFE").contains(normalizeCategory(category))
                && !looksVenueLike(title, address)
                && !categoryMatchesSerpResult(candidate, category)) {
            return "CATEGORY_MISMATCH";
        }
        return null;
    }

    private boolean addressStronglyMatchesDestination(String address, String city) {
        if (address == null || address.isBlank()) return false;
        if (isOffDestinationAddress(address, city)) return false;
        return addressMatchesDestination(address, city);
    }

    private boolean isOffDestinationAddress(String address, String city) {
        if (address == null || address.isBlank() || city == null || city.isBlank()) return false;
        String normalizedAddress = normalizeText(address);
        String normalizedCity = normalizeText(city);
        boolean vungTauTrip = normalizedCity.contains("vung tau") || normalizedCity.contains("ba ria");
        if (vungTauTrip) {
            boolean matchesVungTau = normalizedAddress.contains("vung tau") || normalizedAddress.contains("ba ria");
            boolean explicitOtherCity = normalizedAddress.matches(".*\\b(ho chi minh|hcm|tp hcm|tan hung|phu my hung|quan 1|quan 3|quan 4|quan 5|quan 7|binh thanh|thu duc|dong nai|long an|binh duong)\\b.*");
            return explicitOtherCity && !matchesVungTau;
        }
        return false;
    }

    private boolean hasSerpGps(JsonNode candidate) {
        JsonNode gps = candidate.path("gps_coordinates");
        Double lat = coordinateOrNull(gps.path("latitude").asText(null));
        Double lng = coordinateOrNull(gps.path("longitude").asText(null));
        return isValidVietnamCoordinate(lat, lng);
    }

    private String serpThumbnail(JsonNode result) {
        return firstText(
                result.path("thumbnail").asText(null),
                result.path("image").asText(null)
        );
    }

    private JsonNode pickBestHotelResult(JsonNode body, String query, String city) {
        if (body == null) return null;
        JsonNode shoppingResults = body.path("shopping_results");
        if (!shoppingResults.isArray() || shoppingResults.isEmpty()) return null;

        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        int logged = 0;
        for (JsonNode candidate : shoppingResults) {
            int score = hotelMatchScore(candidate, query, city);
            String rejectReason = hotelRejectReason(candidate, query, city, score);
            boolean accepted = rejectReason == null;
            if (logged < 6) {
                log.info("SerpApi hotel candidate: query='{}' title='{}' source='{}' price='{}' rating={} reviews={} hasThumbnail={} score={} accepted={} rejectedReason={}",
                        query,
                        candidate.path("title").asText(""),
                        candidate.path("source").asText(""),
                        candidate.path("price").asText(""),
                        candidate.path("rating").asText(""),
                        candidate.path("reviews").asText(""),
                        isUsefulImageUrl(serpThumbnail(candidate)),
                        score,
                        accepted,
                        rejectReason == null ? "" : rejectReason);
                logged++;
            }
            if (accepted && score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int countHotelShoppingResults(JsonNode body) {
        if (body == null) return 0;
        JsonNode shoppingResults = body.path("shopping_results");
        return shoppingResults.isArray() ? shoppingResults.size() : 0;
    }

    private String firstHotelRejectReason(JsonNode body, String query, String city, String fallback) {
        if (body == null) return fallback;
        JsonNode shoppingResults = body.path("shopping_results");
        if (!shoppingResults.isArray()) return fallback;
        for (JsonNode candidate : shoppingResults) {
            int score = hotelMatchScore(candidate, query, city);
            String reason = hotelRejectReason(candidate, query, city, score);
            if (reason != null) return reason;
        }
        return fallback;
    }

    private int hotelMatchScore(JsonNode candidate, String query, String city) {
        String title = candidate.path("title").asText("");
        String source = candidate.path("source").asText("");
        String text = normalizeText(String.join(" ", title, source, candidate.path("snippet").asText("")));
        String normalizedQuery = normalizeText(query);
        int score = 0;
        if (text.contains("vung tau") || text.contains("ba ria")) score += 5;
        if (normalizedQuery.contains("bai sau") && (text.contains("bai sau") || text.contains("back beach"))) score += 3;
        if (text.matches(".*\\b(hotel|resort|spa|khach san|villa|homestay|apartment|beach)\\b.*")) score += 2;
        if (text.matches(".*\\b(booking|agoda|traveloka|skyscanner|tripadvisor|trip com|expedia)\\b.*")) score += 3;
        if (parseMoney(candidate.path("price").asText(null)) != null || decimalOrNull(candidate.path("extracted_price")) != null) score += 2;
        if (decimalOrNull(candidate.path("rating")) != null) score += 2;
        if (integerOrNull(candidate.path("reviews")) != null) score += 2;
        if (isUsefulImageUrl(serpThumbnail(candidate))) score += 2;
        if (looksBadProviderResult(title + " " + source)) score -= 8;
        if (text.matches(".*\\b(real estate|sale|for rent|facebook|youtube|tiktok)\\b.*")) score -= 6;
        if (!normalizeText(city).isBlank() && isOffDestinationAddress(title + " " + source, city)) score -= 8;
        return score;
    }

    private String hotelRejectReason(JsonNode candidate, String query, String city, int score) {
        String title = candidate.path("title").asText("");
        if (title == null || title.isBlank()) return "MISSING_TITLE";
        if (score < 4) return "LOW_SCORE";
        String text = normalizeText(String.join(" ", title, candidate.path("source").asText(""), candidate.path("snippet").asText("")));
        if (isOffDestinationAddress(text, city)) return "OFF_DESTINATION";
        if (!text.matches(".*\\b(hotel|resort|spa|khach san|villa|homestay|apartment|beach|vung tau)\\b.*")) {
            return "NOT_HOTEL";
        }
        if (normalizeText(query).contains("khach san") && text.matches(".*\\b(private|entire villa|for rent only)\\b.*")) {
            return "PRIVATE_LISTING";
        }
        return null;
    }

    private String extractHotelNameFromAddress(String address, String providerTitle) {
        if (address == null || address.isBlank()) return null;
        String firstSegment = address.split(",", 2)[0].trim();
        if (firstSegment.length() < 4 || firstSegment.length() > 80) return null;
        String normalizedSegment = normalizeText(firstSegment);
        if (!normalizedSegment.matches(".*\\b(hotel|resort|khach san|homestay|villa|imperial|malibu|pearl|vias|ibis)\\b.*")) {
            return null;
        }
        String normalizedTitle = normalizeText(providerTitle);
        if (normalizedTitle.isBlank()) return firstSegment;
        List<String> segmentTokens = significantTokens(firstSegment);
        List<String> titleTokens = significantTokens(providerTitle);
        boolean overlaps = segmentTokens.stream().anyMatch(titleTokens::contains);
        boolean obviousConflict = normalizedTitle.contains("phu quoc") || normalizedTitle.contains("da nang")
                || normalizedTitle.contains("nha trang") || normalizedTitle.contains("ha noi");
        return (!overlaps || obviousConflict) ? firstSegment : null;
    }

    private int serpMatchScore(
            JsonNode node,
            String placeName,
            String city,
            String category,
            Double preferredLat,
            Double preferredLng,
            Double destinationLat,
            Double destinationLng) {
        String title = node.path("title").asText("");
        String address = node.path("address").asText("");
        String text = normalizeText(title + " " + address);
        int score = 0;

        for (String token : significantTokens(placeName)) {
            if (text.contains(token)) score++;
        }
        for (String token : significantTokens(city)) {
            if (text.contains(token)) score++;
        }
        if (categoryMatchesSerpResult(node, category)) score += 2;
        if (looksBadProviderResult(title + " " + address)) score -= 6;
        if (Set.of("FOOD", "CAFE").contains(normalizeCategory(category)) && looksVenueLike(title, address)) score += 2;

        JsonNode gps = node.path("gps_coordinates");
        Double lat = coordinateOrNull(gps.path("latitude").asText(null));
        Double lng = coordinateOrNull(gps.path("longitude").asText(null));
        if (isValidVietnamCoordinate(destinationLat, destinationLng) && isValidVietnamCoordinate(lat, lng)) {
            double km = haversineKm(destinationLat, destinationLng, lat, lng);
            if (km <= destinationRadiusKm(city)) score += 3;
            else score -= 5;
        }
        if (isValidVietnamCoordinate(preferredLat, preferredLng) && isValidVietnamCoordinate(lat, lng)) {
            double km = haversineKm(preferredLat, preferredLng, lat, lng);
            if (km <= 5) score += 3;
            else if (km <= 15) score += 1;
            else score -= 2;
        }
        if (isGenericPlaceName(placeName) && !looksVenueLike(title, address)) score -= 2;
        if (title.isBlank()) score -= 2;
        return score;
    }

    private boolean isAcceptableSerpCoordinate(
            JsonNode result,
            String placeName,
            String city,
            String category,
            Double lat,
            Double lng,
            Double destinationLat,
            Double destinationLng) {
        if (!isValidVietnamCoordinate(lat, lng)) return false;
        String title = result == null ? "" : result.path("title").asText("");
        String address = result == null ? "" : result.path("address").asText("");
        if (looksBadProviderResult(title + " " + address)) {
            return false;
        }
        if (Set.of("FOOD", "CAFE").contains(normalizeCategory(category)) && !looksVenueLike(title, address)) {
            return false;
        }
        if (!isValidVietnamCoordinate(destinationLat, destinationLng)) return true;

        double km = haversineKm(destinationLat, destinationLng, lat, lng);
        if (km <= destinationRadiusKm(city)) return true;
        return addressMatchesDestination(address, city) && km <= widerDestinationRadiusKm(city);
    }

    private boolean isAcceptableSerpImageResult(JsonNode result, String placeName, String city, String category) {
        if (result == null || result.isMissingNode() || result.isNull()) return false;
        String title = result.path("title").asText("");
        String address = result.path("address").asText("");
        if (looksBadProviderResult(title + " " + address)) return false;
        String normalizedCategory = normalizeCategory(category);
        if (Set.of("FOOD", "CAFE").contains(normalizedCategory)) {
            return looksVenueLike(title, address)
                    || categoryMatchesSerpResult(result, normalizedCategory)
                    || addressMatchesDestination(address, city);
        }
        if (isGenericPlaceName(placeName) && !addressMatchesDestination(address, city)) {
            return false;
        }
        return true;
    }

    private boolean categoryMatchesSerpResult(JsonNode result, String category) {
        String normalizedCategory = normalizeCategory(category);
        String text = normalizeText(String.join(" ",
                result.path("title").asText(""),
                result.path("type").asText(""),
                result.path("address").asText("")));
        return switch (normalizedCategory) {
            case "FOOD" -> text.matches(".*\\b(restaurant|food|quan|nha hang|hai san|seafood|banh|bun|pho|com|an|eatery|diner)\\b.*");
            case "CAFE" -> text.matches(".*\\b(cafe|coffee|ca phe|bakery|dessert|tra sua|tea)\\b.*");
            case "MARKET" -> text.matches(".*\\b(market|cho|night market|food court)\\b.*");
            case "HOTEL", "HOMESTAY", "ACCOMMODATION" -> text.matches(".*\\b(hotel|homestay|resort|lodging|khach san)\\b.*");
            case "SIGHTSEEING" -> !text.matches(".*\\b(person|singer|actor|band|album|award|logo|brand)\\b.*");
            default -> true;
        };
    }

    private boolean looksVenueLike(String title, String address) {
        String text = normalizeText(title + " " + address);
        return text.matches(".*\\b(cafe|coffee|ca phe|restaurant|nha hang|quan|bar|club|bakery|banh|bun|pho|com|hai san|seafood|market|cho)\\b.*")
                || addressMatchesDestination(address, "");
    }

    private boolean isMarketCandidate(String title, String address) {
        String text = normalizeText(title + " " + address);
        return text.matches(".*\\b(cho|market|fish market|night market|xom luoi)\\b.*")
                && !text.matches(".*\\b(quan|nha hang|restaurant|cafe|coffee|banh|bun|pho|com|hai san|seafood)\\b.*");
    }

    private boolean looksBadProviderResult(String value) {
        String text = normalizeText(value);
        return text.matches(".*\\b(logo|icon|insignia|vector|svg|person|celebrity|singer|actor|band|album|award|festival|brand|facebook|youtube|tiktok)\\b.*");
    }

    private boolean shouldUseWikimediaImage(String placeName, String city, String category) {
        String normalizedCategory = normalizeCategory(category);
        if (Set.of("FOOD", "CAFE", "HOTEL", "HOMESTAY", "ACCOMMODATION", "CHECKIN", "CHECKOUT").contains(normalizedCategory)) {
            return false;
        }
        if (isGenericPlaceName(placeName)) {
            return false;
        }
        return Set.of("SIGHTSEEING", "MARKET", "OTHER").contains(normalizedCategory);
    }

    private boolean isExactLandmarkCategory(String category) {
        return Set.of("SIGHTSEEING", "MARKET", "OTHER").contains(normalizeCategory(category));
    }

    private boolean isRealPhoto(PlaceDetail detail, String category) {
        if (detail == null || !isUsefulImageUrl(detail.getImageUrl())) return false;
        String source = String.valueOf(detail.getImageSource());
        if (IMAGE_SOURCE_SERPAPI.equalsIgnoreCase(source)) return true;
        return IMAGE_SOURCE_WIKIMEDIA.equalsIgnoreCase(source)
                && isExactLandmarkCategory(category)
                && isUsefulWikimediaImageUrl(detail.getImageUrl());
    }

    private boolean isUsefulWikimediaImageUrl(String imageUrl) {
        if (!isUsefulImageUrl(imageUrl)) return false;
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        return !lower.endsWith(".svg")
                && !lower.contains("logo")
                && !lower.contains("icon")
                && !lower.contains("insignia")
                && !lower.contains("vector");
    }

    private boolean isWikimediaImageAllowed(String imageUrl, String placeName, String city, String category) {
        if (!shouldUseWikimediaImage(placeName, city, category)) return false;
        String text = normalizeText(imageUrl + " " + placeName);
        if (looksBadProviderResult(text)) return false;
        String destination = normalizeText(city);
        if (destination.contains("vung tau") && text.contains("con dao")) return false;
        return true;
    }

    private boolean addressMatchesDestination(String address, String city) {
        String normalizedAddress = normalizeText(address);
        String normalizedCity = normalizeText(city);
        if (normalizedAddress.isBlank()) return false;
        if (!normalizedCity.isBlank()) {
            for (String token : significantTokens(normalizedCity)) {
                if (normalizedAddress.contains(token)) return true;
            }
        }
        return normalizedAddress.contains("vung tau")
                || normalizedAddress.contains("ba ria")
                || normalizedAddress.contains("da lat")
                || normalizedAddress.contains("lam dong")
                || normalizedAddress.contains("ha noi")
                || normalizedAddress.contains("da nang")
                || normalizedAddress.contains("nha trang");
    }

    private double widerDestinationRadiusKm(String city) {
        String normalized = normalizeText(city);
        if (normalized.contains("vung tau") || normalized.contains("ba ria")) return 60.0;
        return destinationRadiusKm(city) + 20.0;
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

    private boolean shouldUseSerpApi(String category) {
        return SERPAPI_ELIGIBLE_CATEGORIES.contains(normalizeCategory(category));
    }

    private boolean isAccommodationCategory(String category) {
        return Set.of("HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING").contains(normalizeCategory(category));
    }

    private String buildProviderQuery(String placeName, String city, String category) {
        String normalizedCategory = normalizeCategory(category);
        String destination = nullToEmpty(city).trim();
        String hinted = curatedProviderQuery(placeName, destination, normalizedCategory);
        if (hinted != null) return hinted;
        boolean generic = isGenericPlaceName(placeName);

        return switch (normalizedCategory) {
            case "FOOD" -> generic
                    ? joinQuery("quán ăn địa phương nổi tiếng", destination)
                    : joinQuery(placeName, destination);
            case "CAFE" -> generic
                    ? joinQuery("quán cafe view biển", destination)
                    : joinQuery("quán cafe", placeName, destination);
            case "HOTEL", "HOMESTAY", "ACCOMMODATION" -> generic
                    ? joinQuery("khách sạn", destination)
                    : joinQuery(placeName, destination);
            case "SIGHTSEEING" -> generic
                    ? joinQuery("địa điểm tham quan", destination)
                    : joinQuery(placeName, destination);
            default -> joinQuery(placeName, destination);
        };
    }

    private String curatedProviderQuery(String placeName, String destination, String category) {
        String text = normalizeText(placeName + " " + destination);
        if (!text.contains("vung tau")) return null;
        if (text.contains("banh khot")) return "Bánh Khọt Gốc Vú Sữa Vũng Tàu";
        if (text.contains("hai san") || text.contains("seafood")) return "Gành Hào Vũng Tàu";
        if (text.contains("cafe") || text.contains("ca phe") || "CAFE".equals(category)) {
            if (text.contains("bai truoc") || text.contains("view bien")) return "Sơn Đăng Coffee Vũng Tàu";
            return "quán cafe view biển Vũng Tàu";
        }
        if (text.contains("cho dem") || "MARKET".equals(category)) return "Chợ đêm Vũng Tàu";
        if (text.contains("bai sau")) return "Bãi Sau Vũng Tàu";
        if (text.contains("bai truoc")) return "Bãi Trước Vũng Tàu";
        if (text.contains("tuong chua") || text.contains("kito")) return "Tượng Chúa Kitô Vua Vũng Tàu";
        if (text.contains("hai dang")) return "Hải đăng Vũng Tàu";
        if (text.contains("nghinh phong")) return "Mũi Nghinh Phong Vũng Tàu";
        if ("FOOD".equals(category) && isGenericPlaceName(placeName)) return "quán ăn sáng nổi tiếng Vũng Tàu";
        return null;
    }

    private List<String> serpSearchQueriesForSerpApi(String placeName, String city, String category) {
        List<String> queries = new ArrayList<>();
        String normalizedCategory = normalizeCategory(category);
        String curated = curatedSerpApiQueryV2(placeName, city, normalizedCategory);
        addUniqueQuery(queries, normalizeProviderQuery(curated, city));
        addUniqueQuery(queries, normalizeProviderQuery(cleanSerpApiQuery(placeName, city, normalizedCategory), city));
        addUniqueQuery(queries, normalizeProviderQuery(placeName, city));
        return queries.stream().limit(3).toList();
    }

    private List<String> hotelSearchQueries(String placeName, String city) {
        List<String> queries = new ArrayList<>();
        String destination = nullToEmpty(city).trim();
        String text = normalizeText(placeName + " " + destination);
        if (text.contains("vung tau")) {
            if (text.contains("bai sau") || text.contains("back beach")) {
                addUniqueQuery(queries, "khach san Bai Sau Vung Tau");
                addUniqueQuery(queries, "hotel Back Beach Vung Tau");
            }
            addUniqueQuery(queries, "khach san Vung Tau");
            addUniqueQuery(queries, "hotel Vung Tau");
            return queries.stream().limit(4).toList();
        }
        if (text.contains("vung tau") && (text.contains("bai sau") || text.contains("back beach"))) {
            addUniqueQuery(queries, "khách sạn Bãi Sau Vũng Tàu");
            addUniqueQuery(queries, "hotel Back Beach Vung Tau");
        }
        addUniqueQuery(queries, joinQuery("khách sạn", destination));
        addUniqueQuery(queries, joinQuery("hotel", destination));
        addUniqueQuery(queries, normalizeProviderQuery(placeName, destination));
        return queries.stream().limit(4).toList();
    }

    private String curatedSerpApiQuery(String placeName, String destination, String category) {
        String text = normalizeText(placeName + " " + destination);
        if (!text.contains("vung tau")) return null;
        if (text.contains("banh khot")) return "Bánh Khọt Gốc Vú Sữa Vũng Tàu";
        if (text.contains("ganh hao")) return "Gành Hào Vũng Tàu";
        if (text.contains("marina club")) return "Marina Club Vũng Tàu";
        if (text.contains("son dang")) return "Sơn Đăng Coffee Vũng Tàu";
        if (text.contains("hai san") || text.contains("seafood")) return "Gành Hào Vũng Tàu";
        if (text.contains("cho dem") || "MARKET".equals(category)) return "Chợ đêm Vũng Tàu";
        if (text.contains("tuong chua") || text.contains("kito")) return "Tượng Chúa Kitô Vua Vũng Tàu";
        if (text.contains("hai dang")) return "Hải Đăng Vũng Tàu";
        if (text.contains("nghinh phong")) return "Mũi Nghinh Phong Vũng Tàu";
        if (text.contains("bai sau")) return "Bãi Sau Vũng Tàu";
        if (text.contains("bai truoc")) return "Bãi Trước Vũng Tàu";
        if ("CAFE".equals(category)) return "Marina Club Vũng Tàu";
        if ("FOOD".equals(category) && isGenericPlaceName(placeName)) return "Bánh Khọt Gốc Vú Sữa Vũng Tàu";
        return null;
    }

    private String curatedSerpApiQueryV2(String placeName, String destination, String category) {
        String text = normalizeText(placeName + " " + destination);
        if (!text.contains("vung tau")) return null;
        String exactKey = exactVenueKey(placeName);
        if (exactKey != null) {
            return switch (exactKey) {
                case "BANH_KHOT_GOC_VU_SUA" -> "Banh Khot Goc Vu Sua Vung Tau";
                case "SON_DANG_COFFEE" -> "Son Dang Coffee Vung Tau";
                case "GANH_HAO" -> "Ganh Hao Vung Tau";
                case "MARINA_CLUB" -> "Marina Club Vung Tau";
                case "ANNATA_BEACH_HOTEL" -> "Annata Beach Hotel Vung Tau";
                case "PREMIER_PEARL" -> "Premier Pearl Hotel Vung Tau";
                case "IMPERIAL_HOTEL" -> "The IMPERIAL Hotel Vung Tau";
                case "MALIBU_HOTEL" -> "The Malibu Hotel Vung Tau";
                default -> placeName;
            };
        }
        if (text.contains("hai san") || text.contains("seafood")) return "nha hang hai san Vung Tau";
        if (text.contains("cho xom luoi")) return "Cho Xom Luoi Vung Tau";
        if (text.contains("cho dem") || "MARKET".equals(category)) return "Cho dem Vung Tau";
        if (text.contains("tuong chua") || text.contains("kito")) return "Tuong Chua Kito Vua Vung Tau";
        if (text.contains("hai dang")) return "Hai Dang Vung Tau";
        if (text.contains("nghinh phong")) return "Mui Nghinh Phong Vung Tau";
        if ("CAFE".equals(category)) {
            if (text.contains("marina club")) return "Marina Club Vung Tau";
            return "quan cafe view bien Vung Tau";
        }
        if ("FOOD".equals(category) && isGenericPlaceName(placeName)) {
            if (text.contains("bai truoc")) return "nha hang gan Bai Truoc Vung Tau";
            if (text.contains("an sang") || text.contains("breakfast")) return "quan an sang Vung Tau";
            if (text.contains("an trua") || text.contains("lunch")) return "quan an trua Vung Tau";
            return "quan an dia phuong Vung Tau";
        }
        if (text.contains("bai sau")) return "Bai Sau Vung Tau";
        if (text.contains("bai truoc")) return "Bai Truoc Vung Tau";
        if (exactKey != null) {
            return switch (exactKey) {
                case "BANH_KHOT_GOC_VU_SUA" -> "Bánh Khọt Gốc Vú Sữa Vũng Tàu";
                case "SON_DANG_COFFEE" -> "Sơn Đăng Coffee Vũng Tàu";
                case "GANH_HAO" -> "Gành Hào Vũng Tàu";
                case "MARINA_CLUB" -> "Marina Club Vũng Tàu";
                case "ANNATA_BEACH_HOTEL" -> "Annata Beach Hotel Vũng Tàu";
                case "PREMIER_PEARL" -> "Premier Pearl Hotel Vũng Tàu";
                case "IMPERIAL_HOTEL" -> "The IMPERIAL Hotel Vũng Tàu";
                case "MALIBU_HOTEL" -> "The Malibu Hotel Vũng Tàu";
                default -> placeName;
            };
        }
        if (text.contains("hai san") || text.contains("seafood")) return "nhà hàng hải sản Vũng Tàu";
        if (text.contains("cho dem") || "MARKET".equals(category)) return "Chợ đêm Vũng Tàu";
        if (text.contains("tuong chua") || text.contains("kito")) return "Tượng Chúa Kitô Vua Vũng Tàu";
        if (text.contains("hai dang")) return "Hải Đăng Vũng Tàu";
        if (text.contains("nghinh phong")) return "Mũi Nghinh Phong Vũng Tàu";
        if ("CAFE".equals(category)) return "quán cafe view biển Vũng Tàu";
        if ("FOOD".equals(category) && isGenericPlaceName(placeName)) {
            if (text.contains("bai truoc")) return "nhà hàng gần Bãi Trước Vũng Tàu";
            if (text.contains("an sang") || text.contains("breakfast")) return "quán ăn sáng Vũng Tàu";
            return "quán ăn địa phương Vũng Tàu";
        }
        if (text.contains("bai sau")) return "Bãi Sau Vũng Tàu";
        if (text.contains("bai truoc")) return "Bãi Trước Vũng Tàu";
        return null;
    }

    private String cleanSerpApiQuery(String placeName, String city, String category) {
        String destination = nullToEmpty(city).trim();
        boolean generic = isGenericPlaceName(placeName);
        String text = normalizeText(placeName + " " + destination);
        if (text.contains("vung tau")) {
            return switch (normalizeCategory(category)) {
                case "FOOD" -> generic ? "quan an dia phuong noi tieng Vung Tau" : joinQuery(placeName, "Vung Tau");
                case "CAFE" -> generic ? "quan cafe view bien Vung Tau" : joinQuery("quan cafe", placeName, "Vung Tau");
                case "MARKET" -> generic ? "cho am thuc Vung Tau" : joinQuery(placeName, "Vung Tau");
                case "HOTEL", "HOMESTAY", "ACCOMMODATION" -> generic ? "khach san Vung Tau" : joinQuery(placeName, "Vung Tau");
                case "SIGHTSEEING" -> generic ? "dia diem tham quan Vung Tau" : joinQuery(placeName, "Vung Tau");
                default -> joinQuery(placeName, "Vung Tau");
            };
        }
        return switch (normalizeCategory(category)) {
            case "FOOD" -> generic ? joinQuery("quán ăn địa phương nổi tiếng", destination) : joinQuery(placeName, destination);
            case "CAFE" -> generic ? joinQuery("quán cafe view biển", destination) : joinQuery("quán cafe", placeName, destination);
            case "MARKET" -> generic ? joinQuery("chợ đêm ẩm thực", destination) : joinQuery(placeName, destination);
            case "HOTEL", "HOMESTAY", "ACCOMMODATION" -> generic ? joinQuery("khách sạn", destination) : joinQuery(placeName, destination);
            case "SIGHTSEEING" -> generic ? joinQuery("địa điểm tham quan", destination) : joinQuery(placeName, destination);
            default -> joinQuery(placeName, destination);
        };
    }

    private List<String> serpSearchQueries(String placeName, String city, String category) {
        List<String> queries = new ArrayList<>();
        addUniqueQuery(queries, normalizeProviderQuery(placeName, city));
        String normalized = buildProviderQuery(placeName, city, category);
        addUniqueQuery(queries, normalizeProviderQuery(normalized, city));
        String curated = curatedProviderQuery(placeName, nullToEmpty(city), normalizeCategory(category));
        if (curated != null) {
            addUniqueQuery(queries, normalizeProviderQuery(curated, city));
        }
        return queries.stream().limit(3).toList();
    }

    private void addUniqueQuery(List<String> queries, String query) {
        if (query == null || query.isBlank()) return;
        String normalized = normalizeText(query);
        boolean exists = queries.stream().map(this::normalizeText).anyMatch(normalized::equals);
        if (!exists) queries.add(query.trim());
    }

    private String normalizeProviderQuery(String query, String destination) {
        String value = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        String dest = destination == null ? "" : destination.trim();
        if (value.isBlank()) return dest;
        if (dest.isBlank() || normalizeText(value).contains(normalizeText(dest))) {
            return value;
        }
        return (value + " " + dest).trim();
    }

    private String joinQuery(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) values.add(part.trim());
        }
        return String.join(" ", values).trim();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String value = category.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "RESTAURANT", "DINING", "BREAKFAST", "LUNCH", "DINNER" -> "FOOD";
            case "COFFEE" -> "CAFE";
            case "SHOPPING", "NIGHT_MARKET" -> "MARKET";
            case "STAY", "LODGING", "CHECKIN", "CHECK_IN" -> "HOTEL";
            case "HOMESTAY" -> "HOMESTAY";
            case "ACCOMMODATION" -> "ACCOMMODATION";
            case "SIGHT", "ATTRACTION", "PLACE", "VISIT" -> "SIGHTSEEING";
            default -> value;
        };
    }

    private String cacheKey(String placeName, String city, String category) {
        return normalizeText(nullToEmpty(city)) + "|" + normalizeText(placeName) + "|" + normalizeCategory(category);
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> significantTokens(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isGenericPlaceName(String placeName) {
        String normalized = normalizeText(placeName);
        if (normalized.isBlank()) return true;

        if (normalized.matches(".*\\b(an trua|an toi|an sang|mua dac san|ca phe|cafe|quan an|nha hang|dia phuong|noi tieng|check in|checkout|tra phong|nhan phong|gui hanh ly|di chuyen)\\b.*")) {
            return true;
        }

        List<String> tokens = significantTokens(placeName);
        return tokens.size() < 2;
    }

    private double destinationRadiusKm(String city) {
        String normalized = normalizeText(city);
        if (normalized.contains("vung tau")) return 35.0;
        if (normalized.contains("ha giang") || normalized.contains("phu quoc") || normalized.contains("sapa") || normalized.contains("sa pa")) {
            return 60.0;
        }
        return 30.0;
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

    private BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            String raw = node.asText(null);
            if (raw == null || raw.isBlank()) return null;
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.isBlank()) return null;
            return new BigDecimal(digits).setScale(0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal firstMoney(BigDecimal... values) {
        if (values == null) return null;
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) return value;
        }
        return null;
    }

    private Integer integerOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) return null;
        try {
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
            if (normalized.endsWith("k")) {
                String number = normalized.substring(0, normalized.length() - 1).replaceAll("[^0-9.]", "");
                if (!number.isBlank()) {
                    return (int) Math.round(Double.parseDouble(number) * 1000);
                }
            }
            String digits = raw.replaceAll("[^0-9]", "");
            if (digits.isBlank()) return null;
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private boolean isUsefulImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        return lower.startsWith("http") && !lower.contains("picsum.photos") && !lower.endsWith(".svg");
    }

    private boolean isValidVietnamCoordinate(Double lat, Double lng) {
        return lat != null && lng != null && lat >= 8.0 && lat <= 24.0 && lng >= 102.0 && lng <= 110.0;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeProviderError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message
                .replaceAll("(?i)(api_key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(access_token=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(token=)[^&\\s\\\"]+", "$1***")
                .replaceAll("https://serpapi\\.com/[^\\s\\\"]+", "https://serpapi.com/***")
                .replaceAll("https://rsapi\\.goong\\.io/[^\\s\\\"]+", "https://rsapi.goong.io/***")
                .replaceAll("https://generativelanguage\\.googleapis\\.com/[^\\s\\\"]+", "https://generativelanguage.googleapis.com/***")
                .replaceAll("https://api\\.scrapingdog\\.com/[^\\s\\\"]+", "https://api.scrapingdog.com/***");
    }

    public static class SerpApiBudget {
        private final AtomicInteger remaining;

        private SerpApiBudget(int maxCalls) {
            this.remaining = new AtomicInteger(Math.max(0, maxCalls));
        }

        public static SerpApiBudget of(int maxCalls) {
            return new SerpApiBudget(maxCalls);
        }

        public static SerpApiBudget single() {
            return new SerpApiBudget(1);
        }

        public boolean tryAcquire() {
            while (true) {
                int current = remaining.get();
                if (current <= 0) return false;
                if (remaining.compareAndSet(current, current - 1)) return true;
            }
        }
    }
}
