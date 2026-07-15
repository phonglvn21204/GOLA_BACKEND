package com.gola.service;

import com.gola.dto.ai.CandidatePlace;
import com.gola.dto.ai.ExtractedTravelCandidate;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.ai.TravelResearchContext;
import com.gola.dto.ai.VerifiedCandidatePlace;
import com.gola.dto.map.PlaceDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifiedCandidatePoolService {

    private static final int MAX_POOL_SIZE = 40;
    private static final int DEFAULT_MAX_PROVIDER_CALLS = 10;
    private static final int DEFAULT_MAX_CANDIDATES_TO_VERIFY = 12;
    private static final int DEFAULT_TARGET_VERIFIED = 10;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Set<String> GENERIC_TOKENS = new HashSet<>(List.of(
            "quan", "nha", "hang", "tiem", "co", "so", "vung", "tau", "tp", "ho", "chi", "minh",
            "chi", "nhanh", "gan", "bien", "view", "ngon", "dia", "phuong", "du", "lich", "diem",
            "check", "in", "khach", "san"
    ));

    private final PlaceEnrichmentService placeEnrichmentService;

    public List<VerifiedCandidatePlace> buildVerifiedPool(
            GenerateTripRequest req,
            List<ExtractedTravelCandidate> extractedCandidates,
            TravelResearchContext researchContext) {
        return buildVerifiedPool(req, extractedCandidates, researchContext, VerificationOptions.defaults());
    }

    public List<VerifiedCandidatePlace> buildVerifiedPool(
            GenerateTripRequest req,
            List<ExtractedTravelCandidate> extractedCandidates,
            TravelResearchContext researchContext,
            VerificationOptions options) {
        VerificationOptions effectiveOptions = options == null ? VerificationOptions.defaults() : options.sanitized();
        List<ExtractedTravelCandidate> candidates = new ArrayList<>();
        if (extractedCandidates != null) {
            candidates.addAll(extractedCandidates.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing((ExtractedTravelCandidate c) -> c.getConfidence() == null ? 0 : c.getConfidence()).reversed())
                    .limit(effectiveOptions.maxCandidatesToVerify())
                    .toList());
        }

        Map<String, VerifiedCandidatePlace> accepted = new LinkedHashMap<>();
        VerificationContext context = new VerificationContext(effectiveOptions);
        int rejected = 0;
        for (ExtractedTravelCandidate candidate : candidates) {
            if (shouldStopVerification(accepted, context, effectiveOptions, req)) break;
            rejected += verifyAndAccept(req, candidate, context, accepted);
        }

        if (effectiveOptions.fallbackCategorySearchEnabled()) {
            for (ExtractedTravelCandidate fallback : fallbackQuotaCandidates(req)) {
                if (shouldStopVerification(accepted, context, effectiveOptions, req)) break;
                if (quotaMetForCandidate(accepted, fallback, req)) continue;
                rejected += verifyAndAccept(req, fallback, context, accepted);
            }
        }

        List<VerifiedCandidatePlace> pool = accepted.values().stream()
                .sorted(Comparator
                        .comparing((VerifiedCandidatePlace p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating()).reversed()
                        .thenComparing((VerifiedCandidatePlace p) -> p.getReviewCount() == null ? 0 : p.getReviewCount(), Comparator.reverseOrder()))
                .limit(MAX_POOL_SIZE)
                .toList();

        Map<String, Long> counts = categoryCounts(pool);
        log.info("Verified candidate pool built: extractedCandidateCount={} verifiedCandidateCount={} rejectedCandidateCount={} providerBudgetUsed={} providerBudgetRemaining={} targetVerifiedCandidates={} verifiedHotelFound={} counts FOOD={} CAFE={} SIGHTSEEING={} BEACH={} HOTEL={} MARKET={}",
                extractedCandidates == null ? 0 : extractedCandidates.size(),
                pool.size(),
                rejected,
                context.providerCallsUsed(),
                context.remainingProviderCalls(),
                effectiveOptions.targetVerifiedCandidates(),
                pool.stream().anyMatch(p -> "HOTEL".equalsIgnoreCase(p.getCategory())),
                counts.getOrDefault("FOOD", 0L),
                counts.getOrDefault("CAFE", 0L),
                counts.getOrDefault("SIGHTSEEING", 0L),
                counts.getOrDefault("BEACH", 0L),
                counts.getOrDefault("HOTEL", 0L),
                counts.getOrDefault("MARKET", 0L));
        return pool;
    }

    private int verifyAndAccept(
            GenerateTripRequest req,
            ExtractedTravelCandidate candidate,
            VerificationContext context,
            Map<String, VerifiedCandidatePlace> accepted) {
        VerifiedCandidatePlace verified = verifyCandidate(req, candidate, context);
        if (verified.isVerified()) {
            String key = providerKey(verified);
            if (accepted.containsKey(key)) {
                logCandidateReject(candidate, verified.getSearchQuery(), verified, "DUPLICATE_PROVIDER_CANDIDATE");
                return 1;
            }
            accepted.put(key, verified);
            return 0;
        }
        return 1;
    }

    private boolean shouldStopVerification(
            Map<String, VerifiedCandidatePlace> accepted,
            VerificationContext context,
            VerificationOptions options,
            GenerateTripRequest req) {
        if (accepted.size() >= MAX_POOL_SIZE) return true;
        if (context.remainingProviderCalls() <= 0) return true;
        return accepted.size() >= options.targetVerifiedCandidates() && requiredQuotasMet(accepted, req);
    }

    public List<CandidatePlace> toCandidatePlaces(List<VerifiedCandidatePlace> verifiedPool) {
        if (verifiedPool == null || verifiedPool.isEmpty()) return List.of();
        return verifiedPool.stream()
                .filter(VerifiedCandidatePlace::isVerified)
                .map(place -> CandidatePlace.builder()
                        .title(firstNonBlank(place.getName(), place.getProviderTitle()))
                        .category(place.getCategory())
                        .address(place.getAddress())
                        .lat(place.getLat())
                        .lng(place.getLng())
                        .rating(place.getRating())
                        .reviewCount(place.getReviewCount())
                        .imageUrl(place.getImageUrl())
                        .providerId(place.getProviderId())
                        .source(place.getProviderSource())
                        .estimatedCost(place.getEstimatedCost())
                        .matchReason(place.getSourceCandidateName())
                        .searchQuery(place.getSearchQuery())
                        .build())
                .toList();
    }

    private VerifiedCandidatePlace verifyCandidate(GenerateTripRequest req, ExtractedTravelCandidate candidate, VerificationContext context) {
        String destination = req.getDestination();
        String category = normalizeCategory(candidate.getCategory(), candidate.getName());
        VerifiedCandidatePlace lastRejected = null;

        for (String query : searchQueries(candidate, destination, category)) {
            int budgetCalls = Math.min(context.remainingProviderCalls(), isHotelCategory(category) ? 2 : 1);
            if (budgetCalls <= 0) {
                lastRejected = rejected(candidate, category, query, "PROVIDER_BUDGET_EXHAUSTED", null);
                break;
            }
            context.reserveProviderCalls(budgetCalls);
            try {
                PlaceDetail detail = placeEnrichmentService.enrichForStop(
                        query,
                        destination,
                        category,
                        null,
                        null,
                        PlaceEnrichmentService.SerpApiBudget.of(budgetCalls)
                );
                if (detail == null) {
                    lastRejected = rejected(candidate, category, query, isHotelCategory(category) ? "HOTEL_NO_RESULTS" : "NO_RESULTS", null);
                    logCandidateReject(candidate, query, null, lastRejected.getRejectReason());
                    continue;
                }

                String reason = rejectReason(req, candidate, detail, category);
                if (reason != null) {
                    lastRejected = rejected(candidate, category, query, reason, detail);
                    logCandidateReject(candidate, query, lastRejected, reason);
                    continue;
                }
                return accepted(candidate, category, query, detail);
            } catch (Exception e) {
                lastRejected = rejected(candidate, category, query, "PROVIDER_ERROR", null);
                log.warn("Verified candidate provider error: candidateName='{}' category={} query='{}' reason={}",
                        candidate.getName(), category, query, safeMessage(e));
            }
        }
        return lastRejected != null ? lastRejected : rejected(candidate, category, defaultSearchQuery(candidate, destination), "NO_RESULTS", null);
    }

    private VerifiedCandidatePlace accepted(ExtractedTravelCandidate candidate, String category, String searchQuery, PlaceDetail detail) {
        String providerTitle = firstNonBlank(detail.getProviderTitle(), detail.getName(), candidate.getName());
        boolean serpMetadata = hasSerpMetadata(detail);
        return VerifiedCandidatePlace.builder()
                .name(providerTitle)
                .providerTitle(providerTitle)
                .category(category)
                .address(firstNonBlank(detail.getPlaceAddress(), detail.getAddress()))
                .lat(detail.getLat())
                .lng(detail.getLng())
                .rating(detail.getRating())
                .reviewCount(detail.getReviewCount())
                .imageUrl(detail.getImageUrl())
                .providerId(detail.getProviderId())
                .providerSource(serpMetadata ? "SERPAPI" : firstNonBlank(detail.getProviderSource(), detail.getDataSource()))
                .imageSource(detail.getImageSource())
                .estimatedCost(detail.getEstimatedCost())
                .sourceCandidateName(candidate.getName())
                .sourceArticleTitle(candidate.getSourceTitle())
                .searchQuery(searchQuery)
                .verified(true)
                .build();
    }

    private VerifiedCandidatePlace rejected(ExtractedTravelCandidate candidate, String category, String query, String reason, PlaceDetail detail) {
        return VerifiedCandidatePlace.builder()
                .name(candidate.getName())
                .providerTitle(detail == null ? null : firstNonBlank(detail.getProviderTitle(), detail.getName()))
                .category(category)
                .address(detail == null ? null : firstNonBlank(detail.getPlaceAddress(), detail.getAddress()))
                .lat(detail == null ? null : detail.getLat())
                .lng(detail == null ? null : detail.getLng())
                .providerId(detail == null ? null : detail.getProviderId())
                .providerSource(detail == null ? null : firstNonBlank(detail.getProviderSource(), detail.getDataSource()))
                .imageSource(detail == null ? null : detail.getImageSource())
                .sourceCandidateName(candidate.getName())
                .sourceArticleTitle(candidate.getSourceTitle())
                .searchQuery(query)
                .verified(false)
                .rejectReason(reason)
                .build();
    }

    private String rejectReason(GenerateTripRequest req, ExtractedTravelCandidate candidate, PlaceDetail detail, String category) {
        if (detail.getLat() == null || detail.getLng() == null || !Boolean.TRUE.equals(detail.getHasRealCoordinates())) {
            return isHotelCategory(category) ? "HOTEL_COORD_RESOLVE_FAILED" : "MISSING_COORDINATES";
        }
        if (!insideDestination(req.getDestination(), detail.getLat(), detail.getLng(), firstNonBlank(detail.getPlaceAddress(), detail.getAddress()))) {
            return "OFF_DESTINATION";
        }
        if (isHotelCategory(category) && !hasSerpMetadata(detail)) {
            return "HOTEL_NO_RESULTS";
        }
        if (categoryMismatch(category, detail)) {
            return "CATEGORY_MISMATCH";
        }
        String requestedName = normalize(candidate.getName());
        if (!isGenericIdea(requestedName) && tokenOverlapScore(requestedName, normalize(firstNonBlank(detail.getProviderTitle(), detail.getName()))) < 0.55) {
            return "NAME_MISMATCH";
        }
        return null;
    }

    private List<String> searchQueries(ExtractedTravelCandidate candidate, String destination, String category) {
        LinkedHashMap<String, Boolean> queries = new LinkedHashMap<>();
        String name = candidate.getName() == null ? "" : candidate.getName().trim();
        String preferred = firstNonBlank(candidate.getSearchQuery(), defaultSearchQuery(candidate, destination));
        addQuery(queries, preferred);
        if (!name.isBlank()) {
            addQuery(queries, name + " " + destination);
            if (normalize(destination).contains("vung tau")) {
                addQuery(queries, name + " Vũng Tàu");
            }
            addQuery(queries, name);
        }

        if ("FOOD".equalsIgnoreCase(category)) {
            addQuery(queries, "quán ăn ngon " + destination);
            addQuery(queries, "quán hải sản " + destination);
            addQuery(queries, "quán ăn sáng " + destination);
            addQuery(queries, "bánh khọt " + destination);
            addQuery(queries, "bánh canh ghẹ " + destination);
            addQuery(queries, "lẩu cá đuối " + destination);
        } else if ("CAFE".equalsIgnoreCase(category)) {
            addQuery(queries, "quán cafe view biển " + destination);
            addQuery(queries, "cafe đẹp " + destination);
            addQuery(queries, "cafe gần biển " + destination);
        } else if ("SIGHTSEEING".equalsIgnoreCase(category) || "BEACH".equalsIgnoreCase(category)) {
            addQuery(queries, "địa điểm du lịch " + destination);
            addQuery(queries, "điểm check in " + destination);
            addQuery(queries, "bãi biển " + destination);
        } else if (isHotelCategory(category)) {
            if (normalize(destination).contains("vung tau")) {
                addQuery(queries, "khách sạn Bãi Sau Vũng Tàu");
                addQuery(queries, "hotel near Back Beach Vung Tau");
                addQuery(queries, "khách sạn gần biển Vũng Tàu");
                addQuery(queries, "khách sạn Vũng Tàu");
            } else {
                addQuery(queries, "khách sạn gần biển " + destination);
                addQuery(queries, "khách sạn " + destination);
            }
        }
        return new ArrayList<>(queries.keySet());
    }

    private void addQuery(Map<String, Boolean> queries, String query) {
        if (query != null && !query.isBlank()) {
            queries.putIfAbsent(query.trim(), true);
        }
    }

    private List<ExtractedTravelCandidate> fallbackQuotaCandidates(GenerateTripRequest req) {
        String dest = req.getDestination();
        List<ExtractedTravelCandidate> candidates = new ArrayList<>();
        candidates.add(hint("quán ăn ngon", "FOOD", "quán ăn ngon " + dest, 70));
        candidates.add(hint("quán hải sản", "FOOD", "quán hải sản " + dest, 70));
        candidates.add(hint("bánh khọt", "FOOD", "bánh khọt " + dest, 68));
        candidates.add(hint("bánh canh ghẹ", "FOOD", "bánh canh ghẹ " + dest, 62));
        candidates.add(hint("lẩu cá đuối", "FOOD", "lẩu cá đuối " + dest, 62));

        if (normalize(dest).contains("vung tau")) {
            candidates.add(hint("quán cafe view biển", "CAFE", "quán cafe view biển Vũng Tàu", 72));
            candidates.add(hint("cafe gần Bãi Sau", "CAFE", "cafe gần Bãi Sau Vũng Tàu", 68));
            candidates.add(hint("cafe đẹp", "CAFE", "cafe đẹp Vũng Tàu", 62));
            candidates.add(hint("địa điểm du lịch", "SIGHTSEEING", "địa điểm du lịch Vũng Tàu", 70));
            candidates.add(hint("điểm check in", "SIGHTSEEING", "điểm check in Vũng Tàu", 68));
            candidates.add(hint("Bãi Sau", "BEACH", "Bãi Sau Vũng Tàu", 72));
            candidates.add(hint("Tượng Chúa Kitô Vua", "SIGHTSEEING", "Tượng Chúa Kitô Vua Vũng Tàu", 72));
            candidates.add(hint("Mũi Nghinh Phong", "SIGHTSEEING", "Mũi Nghinh Phong Vũng Tàu", 68));
            candidates.add(hint("Bạch Dinh", "SIGHTSEEING", "Bạch Dinh Vũng Tàu", 64));
            candidates.add(hint("chợ đêm", "MARKET", "chợ đêm Vũng Tàu", 62));
            if (req.getDays() >= 2 && Boolean.TRUE.equals(req.getNeedAccommodation())) {
                candidates.add(hint("khách sạn Bãi Sau", "HOTEL", "khách sạn Bãi Sau Vũng Tàu", 75));
                candidates.add(hint("hotel near Back Beach", "HOTEL", "hotel near Back Beach Vung Tau", 72));
                candidates.add(hint("ibis Styles Vũng Tàu", "HOTEL", "ibis Styles Vũng Tàu", 68));
                candidates.add(hint("Premier Pearl Hotel", "HOTEL", "Premier Pearl Hotel Vũng Tàu", 68));
                candidates.add(hint("Vias Hotel", "HOTEL", "Vias Hotel Vung Tau", 68));
            }
        } else {
            candidates.add(hint("quán cafe view đẹp", "CAFE", "quán cafe view đẹp " + dest, 66));
            candidates.add(hint("địa điểm du lịch", "SIGHTSEEING", "địa điểm du lịch " + dest, 68));
            candidates.add(hint("điểm check in", "SIGHTSEEING", "điểm check in " + dest, 64));
            candidates.add(hint("chợ đêm", "MARKET", "chợ đêm " + dest, 58));
            if (req.getDays() >= 2 && Boolean.TRUE.equals(req.getNeedAccommodation())) {
                candidates.add(hint("khách sạn trung tâm", "HOTEL", "khách sạn trung tâm " + dest, 70));
            }
        }
        return candidates;
    }

    private boolean quotaMetForCandidate(
            Map<String, VerifiedCandidatePlace> accepted,
            ExtractedTravelCandidate candidate,
            GenerateTripRequest req) {
        String category = normalizeCategory(candidate.getCategory(), candidate.getName());
        Map<String, Long> counts = categoryCounts(new ArrayList<>(accepted.values()));
        return switch (category) {
            case "FOOD" -> counts.getOrDefault("FOOD", 0L) >= 3;
            case "CAFE" -> counts.getOrDefault("CAFE", 0L) >= 1;
            case "SIGHTSEEING", "BEACH" -> sightseeingBeachCount(counts) >= 3;
            case "HOTEL" -> !needsHotelQuota(req) || counts.getOrDefault("HOTEL", 0L) >= 1;
            case "MARKET" -> counts.getOrDefault("MARKET", 0L) >= 1;
            default -> false;
        };
    }

    private boolean requiredQuotasMet(Map<String, VerifiedCandidatePlace> accepted, GenerateTripRequest req) {
        Map<String, Long> counts = categoryCounts(new ArrayList<>(accepted.values()));
        boolean hotelMet = !needsHotelQuota(req) || counts.getOrDefault("HOTEL", 0L) >= 1;
        return counts.getOrDefault("FOOD", 0L) >= 3
                && counts.getOrDefault("CAFE", 0L) >= 1
                && sightseeingBeachCount(counts) >= 3
                && hotelMet;
    }

    private long sightseeingBeachCount(Map<String, Long> counts) {
        return counts.getOrDefault("SIGHTSEEING", 0L) + counts.getOrDefault("BEACH", 0L);
    }

    private boolean needsHotelQuota(GenerateTripRequest req) {
        return req != null && req.getDays() >= 2 && Boolean.TRUE.equals(req.getNeedAccommodation());
    }

    private List<ExtractedTravelCandidate> defaultSlotHints(GenerateTripRequest req) {
        String dest = req.getDestination();
        List<ExtractedTravelCandidate> hints = new ArrayList<>();
        hints.add(hint("quán ăn sáng", "FOOD", "quán ăn sáng " + dest));
        hints.add(hint("nhà hàng ăn trưa", "FOOD", "quán ăn trưa " + dest));
        hints.add(hint("nhà hàng hải sản", "FOOD", "nhà hàng hải sản " + dest));
        hints.add(hint("quán cafe view biển", "CAFE", "quán cafe view biển " + dest));
        hints.add(hint("điểm check in", "SIGHTSEEING", "điểm check in " + dest));
        hints.add(hint("chợ đêm", "MARKET", "chợ đêm " + dest));
        if (req.getDays() >= 2 && Boolean.TRUE.equals(req.getNeedAccommodation())) {
            if (normalize(dest).contains("vung tau")) {
                hints.add(hint("khách sạn Bãi Sau", "HOTEL", "khách sạn Bãi Sau Vũng Tàu"));
                hints.add(hint("hotel near Back Beach", "HOTEL", "hotel near Back Beach Vung Tau"));
                hints.add(hint("khách sạn gần biển", "HOTEL", "khách sạn gần biển Vũng Tàu"));
                hints.add(hint("khách sạn Vũng Tàu", "HOTEL", "khách sạn Vũng Tàu"));
            } else {
                hints.add(hint("khách sạn gần biển", "HOTEL", "khách sạn gần biển " + dest));
                hints.add(hint("khách sạn", "HOTEL", "khách sạn " + dest));
            }
        }
        return hints;
    }

    private ExtractedTravelCandidate hint(String name, String category, String query) {
        return hint(name, category, query, 50);
    }

    private ExtractedTravelCandidate hint(String name, String category, String query, int confidence) {
        return ExtractedTravelCandidate.builder()
                .name(name)
                .category(category)
                .searchQuery(query)
                .reason("required itinerary slot")
                .confidence(confidence)
                .build();
    }

    private String defaultSearchQuery(ExtractedTravelCandidate candidate, String destination) {
        String name = candidate.getName() == null ? "" : candidate.getName().trim();
        String category = normalize(candidate.getCategory());
        if (isGenericIdea(normalize(name))) {
            if (category.contains("cafe")) return "quán cafe view biển " + destination;
            if (category.contains("food")) return "quán ăn ngon " + destination;
            if (category.contains("hotel")) return "khách sạn " + destination;
            if (category.contains("night") || category.contains("market")) return "chợ đêm " + destination;
        }
        return name + " " + destination;
    }

    private String normalizeCategory(String category, String name) {
        String text = normalize(firstNonBlank(category, name));
        if (text.contains("hotel") || text.contains("khach san") || text.contains("homestay") || text.contains("resort") || text.contains("hotel area")) return "HOTEL";
        if (text.contains("cafe") || text.contains("coffee")) return "CAFE";
        if (text.contains("food") || text.contains("an ") || text.contains("restaurant") || text.contains("hai san") || text.contains("banh")) return "FOOD";
        if (text.contains("beach") || text.contains("bai sau") || text.contains("bai truoc")) return "BEACH";
        if (text.contains("night") || text.contains("cho dem") || text.contains("market")) return "MARKET";
        if (text.contains("shopping")) return "SHOPPING";
        return "SIGHTSEEING";
    }

    private boolean categoryMismatch(String category, PlaceDetail detail) {
        String providerText = normalize(String.join(" ",
                Objects.toString(detail.getProviderTitle(), ""),
                Objects.toString(detail.getName(), ""),
                Objects.toString(detail.getPlaceAddress(), ""),
                Objects.toString(detail.getAddress(), "")));
        if ("HOTEL".equalsIgnoreCase(category)) {
            return !(providerText.contains("hotel") || providerText.contains("khach san") || providerText.contains("resort")
                    || providerText.contains("homestay") || providerText.contains("villa") || providerText.contains("apartment")
                    || providerText.contains("can ho") || providerText.contains("ibis") || providerText.contains("imperial")
                    || providerText.contains("premier") || providerText.contains("pearl") || providerText.contains("vias"));
        }
        return false;
    }

    private boolean isHotelCategory(String category) {
        return "HOTEL".equalsIgnoreCase(category) || "HOMESTAY".equalsIgnoreCase(category) || "ACCOMMODATION".equalsIgnoreCase(category) || "HOTEL_AREA".equalsIgnoreCase(category);
    }

    private boolean insideDestination(String destination, Double lat, Double lng, String address) {
        if (lat == null || lng == null || lat == 0.0 || lng == 0.0) return false;
        String dest = normalize(destination);
        String addr = normalize(address);
        if (dest.contains("vung tau")) {
            boolean insideVungTauBox = lat >= 10.25 && lat <= 10.55 && lng >= 107.00 && lng <= 107.25;
            boolean unrelatedDistrict = addr.matches(".*\\b(quan 7|tan hung|phu my hung|binh thanh|thu duc|quan 1|quan 3|quan 4|quan 5|dong nai|long an|binh duong)\\b.*");
            if (unrelatedDistrict) return false;
            if (insideVungTauBox) return true;
            boolean hasVungTauContext = addr.contains("vung tau") || addr.contains("bai sau") || addr.contains("bai truoc")
                    || addr.contains("thuy van") || addr.contains("tran phu") || addr.contains("ha long") || addr.contains("hoang hoa tham")
                    || addr.contains("ba ria");
            return hasVungTauContext && !addr.isBlank();
        }
        return lat >= 8.0 && lat <= 24.0 && lng >= 102.0 && lng <= 110.0;
    }

    private boolean hasSerpMetadata(PlaceDetail detail) {
        return "SERPAPI".equalsIgnoreCase(detail.getImageSource())
                || "SERPAPI".equalsIgnoreCase(detail.getDataSource())
                || (detail.getProviderId() != null && (detail.getRating() != null || detail.getReviewCount() != null || detail.getEstimatedCost() != null));
    }

    private double tokenOverlapScore(String requestedName, String providerTitle) {
        Set<String> requested = meaningfulTokens(requestedName);
        Set<String> provider = meaningfulTokens(providerTitle);
        if (requested.isEmpty() || provider.isEmpty()) return 0.0;
        long matched = requested.stream().filter(provider::contains).count();
        return (double) matched / requested.size();
    }

    private Set<String> meaningfulTokens(String value) {
        Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : normalize(value).split("\\s+")) {
            if (token.length() >= 2 && !GENERIC_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean isGenericIdea(String normalized) {
        return normalized.contains("quan an")
                || normalized.contains("nha hang")
                || normalized.contains("cafe view")
                || normalized.contains("diem check")
                || normalized.contains("cho dem")
                || normalized.contains("khach san")
                || normalized.contains("an sang")
                || normalized.contains("an trua")
                || normalized.contains("hai san")
                || normalized.contains("bai bien");
    }

    private String providerKey(VerifiedCandidatePlace place) {
        if (place.getProviderId() != null && !place.getProviderId().isBlank()) {
            return "id:" + normalize(place.getProviderId());
        }
        if (place.getLat() != null && place.getLng() != null) {
            return String.format(Locale.US, "geo:%.5f,%.5f", place.getLat(), place.getLng());
        }
        return "name:" + normalize(firstNonBlank(place.getProviderTitle(), place.getName()));
    }

    private Map<String, Long> categoryCounts(List<VerifiedCandidatePlace> pool) {
        Map<String, Long> counts = new HashMap<>();
        for (VerifiedCandidatePlace place : pool) {
            counts.merge(place.getCategory(), 1L, Long::sum);
        }
        return counts;
    }

    private void logCandidateReject(ExtractedTravelCandidate candidate, String query, VerifiedCandidatePlace place, String reason) {
        log.info("Verified candidate rejected: candidateName='{}' category={} query='{}' providerTitle='{}' providerAddress='{}' lat={} lng={} rejectReason={}",
                candidate.getName(),
                candidate.getCategory(),
                query,
                place == null ? null : place.getProviderTitle(),
                place == null ? null : place.getAddress(),
                place == null ? null : place.getLat(),
                place == null ? null : place.getLng(),
                reason);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message
                .replaceAll("(?i)(api_key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(key=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(access_token=)[^&\\s\\\"]+", "$1***")
                .replaceAll("(?i)(token=)[^&\\s\\\"]+", "$1***");
    }

    public record VerificationOptions(
            int maxProviderCalls,
            int maxCandidatesToVerify,
            int targetVerifiedCandidates,
            boolean fallbackCategorySearchEnabled) {
        public static VerificationOptions defaults() {
            return new VerificationOptions(DEFAULT_MAX_PROVIDER_CALLS, DEFAULT_MAX_CANDIDATES_TO_VERIFY, DEFAULT_TARGET_VERIFIED, true);
        }

        public VerificationOptions sanitized() {
            return new VerificationOptions(
                    Math.max(1, maxProviderCalls),
                    Math.max(1, maxCandidatesToVerify),
                    Math.max(1, targetVerifiedCandidates),
                    fallbackCategorySearchEnabled
            );
        }
    }

    private static class VerificationContext {
        private final int maxProviderCalls;
        private int remainingProviderCalls;

        VerificationContext(VerificationOptions options) {
            this.maxProviderCalls = options.maxProviderCalls();
            this.remainingProviderCalls = options.maxProviderCalls();
        }

        int remainingProviderCalls() {
            return remainingProviderCalls;
        }

        int providerCallsUsed() {
            return maxProviderCalls - remainingProviderCalls;
        }

        void reserveProviderCalls(int calls) {
            remainingProviderCalls = Math.max(0, remainingProviderCalls - Math.max(0, calls));
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
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
}
