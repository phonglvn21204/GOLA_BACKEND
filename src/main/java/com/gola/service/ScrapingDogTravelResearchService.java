package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.ScrapingDogProperties;
import com.gola.dto.ai.TravelResearchContext;
import com.gola.entity.TravelResearchCache;
import com.gola.repository.TravelResearchCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ScrapingDogTravelResearchService {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final int MAX_RESULTS_PER_QUERY = 5;
    private static final int MAX_SOURCE_SUMMARIES = 10;

    private final ScrapingDogProperties properties;
    private final TravelResearchCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ScrapingDogTravelResearchService(
            ScrapingDogProperties properties,
            TravelResearchCacheRepository cacheRepository,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
        int timeoutMs = Math.max(1000, properties.getTimeoutMs());
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @PostConstruct
    void logConfigured() {
        log.info("ScrapingDog enabled: {} configured: {}", properties.isEnabled(), hasApiKey());
    }

    public TravelResearchContext researchDestination(
            String destination,
            LocalDate startDate,
            Integer durationDays,
            List<String> interests) {
        return researchDestination(destination, startDate, durationDays, interests, true);
    }

    public TravelResearchContext researchDestination(
            String destination,
            LocalDate startDate,
            Integer durationDays,
            List<String> interests,
            boolean isPremium) {
        log.info("ScrapingDog usage gate: scrapingDogEnabled={} cacheEnabled={} onlyForPremium={} premiumUser={} maxQueriesPerTrip={}",
                properties.isEnabled(),
                properties.isCacheEnabled(),
                properties.isOnlyForPremium(),
                isPremium,
                properties.getMaxQueriesPerTrip());

        if (!properties.isEnabled() || !hasApiKey()) {
            log.info("ScrapingDog disabled or missing API key");
            return TravelResearchContext.empty(destination);
        }
        if (properties.isOnlyForPremium() && !isPremium) {
            log.info("ScrapingDog skipped for non-premium user");
            return TravelResearchContext.empty(destination);
        }

        LocalDate date = startDate == null ? LocalDate.now() : startDate;
        int month = date.getMonthValue();
        String cacheKey = cacheKey(destination, month, interests);
        if (properties.isCacheEnabled()) {
            TravelResearchContext cached = readCache(cacheKey);
            if (cached != null) {
                log.info("ScrapingDog research usage: scrapingDogEnabled=true scrapingDogCacheHit=true queryCount=0 estimatedCreditCost=0");
                log.info("ScrapingDog research used: cacheHit=true queryCount=0 organicResultsCount={} extractedCandidateSourceCount={}",
                        cached.getSourceSummaries() == null ? 0 : cached.getSourceSummaries().size(),
                        candidateSignalCount(cached));
                return cached;
            }
        }

        TravelResearchContext context = TravelResearchContext.empty(destination);
        context.setMonthText("tháng " + month);

        Set<String> seasonalNotes = new LinkedHashSet<>();
        Set<String> attractions = new LinkedHashSet<>();
        Set<String> foods = new LinkedHashSet<>();
        Set<String> hotelAreas = new LinkedHashSet<>();
        Set<String> tips = new LinkedHashSet<>();
        List<TravelResearchContext.SourceSummary> summaries = new ArrayList<>();

        List<String> queries = buildQueries(destination, date, interests);
        int queryLimit = Math.min(Math.max(0, properties.getMaxQueriesPerTrip()), queries.size());
        int queryCount = 0;
        int totalOrganicCount = 0;

        for (String query : queries.stream().limit(queryLimit).toList()) {
            try {
                queryCount++;
                URI uri = UriComponentsBuilder
                        .fromHttpUrl(properties.getBaseUrl())
                        .queryParam("api_key", properties.getApiKey())
                        .queryParam("query", query)
                        .queryParam("country", properties.getCountry())
                        .queryParam("domain", properties.getDomain())
                        .queryParam("combined_output", properties.isCombinedOutput())
                        .build()
                        .encode()
                        .toUri();

                ResponseEntity<JsonNode> response = restTemplate.getForEntity(uri, JsonNode.class);
                JsonNode root = response.getBody();
                JsonNode organicResults = root == null ? null : root.path("organic_results");
                int organicCount = organicResults != null && organicResults.isArray() ? organicResults.size() : 0;
                totalOrganicCount += organicCount;

                int processed = 0;
                if (organicResults != null && organicResults.isArray()) {
                    for (JsonNode result : organicResults) {
                        if (processed >= MAX_RESULTS_PER_QUERY) break;
                        processed++;
                        SourceText sourceText = sourceText(result);
                        extractSignals(sourceText.fullText(), seasonalNotes, attractions, foods, hotelAreas, tips);
                        if (summaries.size() < MAX_SOURCE_SUMMARIES) {
                            summaries.add(TravelResearchContext.SourceSummary.builder()
                                    .title(sourceText.title())
                                    .snippet(sourceText.snippet())
                                    .sourceDomain(sourceDomain(sourceText.displayedLink(), sourceText.link()))
                                    .link(sourceText.link())
                                    .rank(integerOrNull(result.path("rank").asText(null)))
                                    .build());
                        }
                    }
                }

                log.info("ScrapingDog research: scrapingDogCalled=true query='{}' organicResultsCount={} extractedAttractionsCount={} extractedFoodCount={} extractedHotelAreasCount={}",
                        query,
                        organicCount,
                        attractions.size(),
                        foods.size(),
                        hotelAreas.size());
            } catch (Exception e) {
                log.warn("ScrapingDog research failed for query='{}': {}", query, safeProviderError(e));
            }
        }

        context.setSeasonalNotes(limitedList(seasonalNotes, 8));
        context.setRecommendedAttractions(limitedList(attractions, 12));
        context.setFoodSuggestions(limitedList(foods, 10));
        context.setHotelAreaSuggestions(limitedList(hotelAreas, 8));
        context.setWarningsOrTips(limitedList(tips, 8));
        context.setSourceSummaries(summaries.stream().limit(MAX_SOURCE_SUMMARIES).toList());

        log.info("ScrapingDog research usage: scrapingDogEnabled=true scrapingDogCacheHit=false queryCount={} estimatedCreditCost={} organicResultsCount={}",
                queryCount,
                queryCount * 5,
                totalOrganicCount);
        log.info("ScrapingDog research used: cacheHit=false queryCount={} firstQuery=\"{}\" organicResultsCount={} extractedCandidateSourceCount={}",
                queryCount,
                queries.stream().findFirst().orElse(""),
                totalOrganicCount,
                candidateSignalCount(context));

        if (properties.isCacheEnabled() && context.hasUsefulContext()) {
            writeCache(cacheKey, destination, month, String.join(" | ", queries.stream().limit(queryLimit).toList()), context);
        }
        return context;
    }

    private boolean hasApiKey() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private int candidateSignalCount(TravelResearchContext context) {
        if (context == null) return 0;
        return sizeOf(context.getRecommendedAttractions())
                + sizeOf(context.getFoodSuggestions())
                + sizeOf(context.getHotelAreaSuggestions());
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private TravelResearchContext readCache(String cacheKey) {
        try {
            return cacheRepository.findByCacheKey(cacheKey)
                    .filter(cache -> cache.getExpiresAt() != null && cache.getExpiresAt().isAfter(Instant.now()))
                    .map(cache -> {
                        try {
                            TravelResearchContext context = objectMapper.readValue(cache.getContextJson(), TravelResearchContext.class);
                            log.info("ScrapingDog cache lookup: scrapingDogCacheHit=true cacheKey={}", cacheKey);
                            return context;
                        } catch (Exception e) {
                            log.warn("ScrapingDog cache parse failed for key={}: {}", cacheKey, e.getMessage());
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.warn("ScrapingDog cache read failed for key={}: {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, String destination, int month, String query, TravelResearchContext context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            Instant expiresAt = Instant.now().plus(Duration.ofDays(Math.max(1, properties.getCacheTtlDays())));
            TravelResearchCache cache = cacheRepository.findByCacheKey(cacheKey)
                    .orElseGet(TravelResearchCache::new);
            cache.setCacheKey(cacheKey);
            cache.setDestination(destination == null || destination.isBlank() ? "Vietnam" : destination.trim());
            cache.setMonth(month);
            cache.setQuery(query);
            cache.setContextJson(json);
            cache.setExpiresAt(expiresAt);
            cacheRepository.save(cache);
            log.info("ScrapingDog cache saved: cacheKey={} expiresAt={}", cacheKey, expiresAt);
        } catch (Exception e) {
            log.warn("ScrapingDog cache write failed for key={}: {}", cacheKey, e.getMessage());
        }
    }

    private String cacheKey(String destination, int month, List<String> interests) {
        String interestsHash = shortHash(interests == null ? "" : String.join("|", interests));
        return normalize(destination) + ":" + month + ":" + interestsHash;
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(String.valueOf(value).hashCode());
        }
    }

    private List<String> buildQueries(String destination, LocalDate date, List<String> interests) {
        String dest = destination == null || destination.isBlank() ? "Việt Nam" : destination.trim();
        int month = date.getMonthValue();
        List<String> queries = new ArrayList<>();
        queries.add("địa điểm ăn uống, tham quan, tiện ích du lịch tại " + dest + " tháng " + month);
        queries.add("địa điểm du lịch " + dest + " tháng " + month);
        queries.add("ăn gì ở " + dest + " tháng " + month);
        queries.add("kinh nghiệm du lịch " + dest + " tự túc");
        String normalized = normalize(dest);
        if (normalized.contains("vung tau")) {
            queries.add("khách sạn Bãi Sau Vũng Tàu");
            queries.add("quán cafe view biển Vũng Tàu");
            queries.add("nhà hàng hải sản Vũng Tàu");
        }
        if (interests != null && !interests.isEmpty()) {
            String joined = String.join(" ", interests.stream().filter(s -> s != null && !s.isBlank()).limit(3).toList());
            if (!joined.isBlank()) {
                queries.add(joined + " " + dest + " gợi ý du lịch");
            }
        }
        return queries;
    }

    private SourceText sourceText(JsonNode result) {
        String title = textOrNull(result.path("title"));
        String snippet = firstText(
                textOrNull(result.path("snippet")),
                textOrNull(result.path("inline_snippet"))
        );
        String displayedLink = textOrNull(result.path("displayed_link"));
        String link = textOrNull(result.path("link"));
        String sitelinkTitles = extendedSitelinkTitles(result.path("extended_sitelinks"));
        return new SourceText(
                title,
                snippet,
                displayedLink,
                link,
                String.join(" ", nullToEmpty(title), nullToEmpty(snippet), nullToEmpty(sitelinkTitles), nullToEmpty(displayedLink))
        );
    }

    private String extendedSitelinkTitles(JsonNode extendedSitelinks) {
        if (extendedSitelinks == null || extendedSitelinks.isMissingNode() || extendedSitelinks.isNull()) return null;
        List<String> titles = new ArrayList<>();
        if (extendedSitelinks.isArray()) {
            extendedSitelinks.forEach(node -> {
                String title = textOrNull(node.path("title"));
                if (title != null && !title.isBlank()) titles.add(title);
            });
        } else {
            JsonNode inline = extendedSitelinks.path("inline");
            if (inline.isArray()) {
                inline.forEach(node -> {
                    String title = textOrNull(node.path("title"));
                    if (title != null && !title.isBlank()) titles.add(title);
                });
            }
        }
        return titles.isEmpty() ? null : String.join(" ", titles);
    }

    private void extractSignals(
            String text,
            Set<String> seasonalNotes,
            Set<String> attractions,
            Set<String> foods,
            Set<String> hotelAreas,
            Set<String> tips) {
        String normalized = normalize(text);
        addIfContains(normalized, attractions, "Bãi Sau", "bai sau", "back beach");
        addIfContains(normalized, attractions, "Bãi Trước", "bai truoc", "front beach");
        addIfContains(normalized, attractions, "Mũi Nghinh Phong", "nghinh phong");
        addIfContains(normalized, attractions, "Tượng Chúa Kitô", "tuong chua", "kito", "christ");
        addIfContains(normalized, attractions, "Đồi Con Heo", "doi con heo");
        addIfContains(normalized, attractions, "Bạch Dinh", "bach dinh");
        addIfContains(normalized, attractions, "Hồ Mây", "ho may");
        addIfContains(normalized, attractions, "Bến du thuyền Marina", "ben du thuyen marina", "marina");
        addIfContains(normalized, attractions, "Long Hải", "long hai");
        addIfContains(normalized, attractions, "Hồ Tràm", "ho tram");
        addIfContains(normalized, attractions, "Hồ Cốc", "ho coc");
        addIfContains(normalized, attractions, "Chợ Xóm Lưới", "cho xom luoi", "xom luoi");

        addIfContains(normalized, foods, "Hải sản", "hai san", "seafood");
        addIfContains(normalized, foods, "Bánh khọt", "banh khot");
        addIfContains(normalized, foods, "Lẩu cá đuối", "lau ca duoi");
        addIfContains(normalized, foods, "Cafe view biển", "cafe view bien", "coffee sea view");
        addIfContains(normalized, foods, "Chợ hải sản", "cho hai san", "seafood market");

        addIfContains(normalized, hotelAreas, "Bãi Sau", "bai sau", "back beach");
        addIfContains(normalized, hotelAreas, "Bãi Trước", "bai truoc", "front beach");
        addIfContains(normalized, hotelAreas, "Trung tâm", "trung tam", "center");
        addIfContains(normalized, hotelAreas, "Gần biển", "gan bien", "near beach", "beachfront");

        addIfContains(normalized, seasonalNotes, "Tháng 7 là mùa hè, phù hợp lịch biển nhưng nên tránh nắng gắt giữa trưa.", "thang 7", "mua he");
        addIfContains(normalized, seasonalNotes, "Ưu tiên hoạt động biển vào sáng sớm hoặc chiều muộn.", "bien", "binh minh", "hoang hon");
        addIfContains(normalized, seasonalNotes, "Nên dự phòng mưa ngắn và lịch trong nhà khi thời tiết xấu.", "mua", "rain");
        addIfContains(normalized, tips, "Kiểm tra thời tiết và giờ mở cửa trước khi đi.", "thoi tiet", "gio mo cua");
        addIfContains(normalized, tips, "Đặt bàn hoặc đi sớm với quán hải sản nổi tiếng vào cuối tuần.", "hai san", "cuoi tuan", "dong khach");
    }

    private void addIfContains(String normalizedText, Set<String> target, String value, String... tokens) {
        for (String token : tokens) {
            if (normalizedText.contains(normalize(token))) {
                target.add(value);
                return;
            }
        }
    }

    private List<String> limitedList(Set<String> values, int limit) {
        return values.stream().filter(s -> s != null && !s.isBlank()).limit(limit).toList();
    }

    private String sourceDomain(String displayedLink, String link) {
        if (displayedLink != null && !displayedLink.isBlank()) return displayedLink;
        if (link == null || link.isBlank()) return null;
        try {
            return new URI(link).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private Integer integerOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            node.forEach(child -> {
                if (child.isTextual() && !child.asText().isBlank()) parts.add(child.asText());
            });
            return parts.isEmpty() ? null : String.join(" ", parts);
        }
        return node.asText(null);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private String safeProviderError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message
                .replaceAll("(?i)(api_key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("https://api\\.scrapingdog\\.com/[^\\s\\\"]+", "https://api.scrapingdog.com/***");
    }

    private record SourceText(String title, String snippet, String displayedLink, String link, String fullText) {}
}
