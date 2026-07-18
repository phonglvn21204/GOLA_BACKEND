package com.gola.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.GolaProperties;
import com.gola.dto.ai.ConfirmAiPlanRequest;
import com.gola.dto.ai.AiGeneratedStop;
import com.gola.dto.ai.GenerateTripRequest;
import com.gola.dto.ai.TravelResearchContext;
import com.gola.dto.ai.ExtractedTravelCandidate;
import com.gola.dto.ai.VerifiedCandidatePlace;
import com.gola.dto.trip.AddStopRequest;
import com.gola.dto.map.AutocompleteSuggestion;
import com.gola.dto.map.PlaceDetail;
import com.gola.entity.AiJob;
import com.gola.entity.enums.AiJobKind;
import com.gola.entity.enums.AiJobStatus;
import com.gola.repository.AiJobRepository;
import com.gola.repository.TripStopRepository;
import com.gola.entity.TripStop;
import com.gola.dto.ai.CandidatePlace;
import com.gola.dto.ai.AiCriticResult;
import com.gola.dto.ai.TripQualityReport;
import com.gola.exception.GolaException;
import com.gola.dto.trip.CreateTripRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiTripService {

    private static final java.util.regex.Pattern COORD_PATTERN =
            java.util.regex.Pattern.compile("Coordinates:\\s*([\\-0-9.]+),\\s*([\\-0-9.]+)");
    private static final int DEFAULT_STOP_DURATION_MINUTES = 60;
    private static final ZoneId TRIP_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter START_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private final Set<String> runningGenerateLocks = ConcurrentHashMap.newKeySet();
    private final AiJobRepository jobRepo;
    private final AiQuotaService  quotaService;
    private final TripService     tripService;
    private final GeminiClient    geminiClient;
    private final ObjectMapper    objectMapper;
    private final PlaceEnrichmentService placeEnrichmentService;
    private final PlaceService placeService;
    private final ScrapingDogTravelResearchService travelResearchService;
    private final Executor placeResolutionExecutor;
    private final CandidatePoolService candidatePoolService;
    private final TravelCandidateExtractionService travelCandidateExtractionService;
    private final VerifiedCandidatePoolService verifiedCandidatePoolService;
    private final MissingSlotFillerService missingSlotFillerService;
    private final AiCriticService aiCriticService;
    private final GapFillerService gapFillerService;
    private final TripQualityGate tripQualityGate;
    private final TripStopRepository stopRepo;
    private final FinalItinerarySanitizerService finalItinerarySanitizerService;
    private final GolaProperties properties;

    public AiTripService(
            AiJobRepository jobRepo,
            AiQuotaService quotaService,
            TripService tripService,
            GeminiClient geminiClient,
            ObjectMapper objectMapper,
            PlaceEnrichmentService placeEnrichmentService,
            PlaceService placeService,
            ScrapingDogTravelResearchService travelResearchService,
            @Qualifier("placeResolutionExecutor") Executor placeResolutionExecutor,
            CandidatePoolService candidatePoolService,
            TravelCandidateExtractionService travelCandidateExtractionService,
            VerifiedCandidatePoolService verifiedCandidatePoolService,
            MissingSlotFillerService missingSlotFillerService,
            AiCriticService aiCriticService,
            GapFillerService gapFillerService,
            TripQualityGate tripQualityGate,
            TripStopRepository stopRepo,
            FinalItinerarySanitizerService finalItinerarySanitizerService,
            GolaProperties properties) {
        this.jobRepo = jobRepo;
        this.quotaService = quotaService;
        this.tripService = tripService;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.placeEnrichmentService = placeEnrichmentService;
        this.placeService = placeService;
        this.travelResearchService = travelResearchService;
        this.placeResolutionExecutor = placeResolutionExecutor;
        this.candidatePoolService = candidatePoolService;
        this.travelCandidateExtractionService = travelCandidateExtractionService;
        this.verifiedCandidatePoolService = verifiedCandidatePoolService;
        this.missingSlotFillerService = missingSlotFillerService;
        this.aiCriticService = aiCriticService;
        this.gapFillerService = gapFillerService;
        this.tripQualityGate = tripQualityGate;
        this.stopRepo = stopRepo;
        this.finalItinerarySanitizerService = finalItinerarySanitizerService;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> generateTrip(UUID userId, GenerateTripRequest req, boolean isPremium) {
        String lockKey = userId + ":GENERATE_TRIP";
        if (!runningGenerateLocks.add(lockKey)) {
            throw GolaException.conflict("AI đang tạo lịch trình, vui lòng chờ hoàn tất.");
        }
        long totalStart = System.nanoTime();
        long researchMs = 0;
        long extractionMs = 0;
        long verificationMs = 0;
        long itineraryGeminiMs = 0;
        long fillerMs = 0;
        quotaService.checkAndIncrement(userId, AiJobKind.TRIP_GENERATE, isPremium);
        var job = AiJob.builder()
                .userId(userId).kind(AiJobKind.TRIP_GENERATE).status(AiJobStatus.RUNNING)
                .input(buildJobInput(req))
                .build();
        jobRepo.save(job);

        try {
            GolaProperties.Generate generateConfig = getGenerateConfig();
            boolean useScrapingDogContext = generateConfig.isUseScrapingdogContext();
            boolean useVerifiedCandidatePool = isVerifiedCandidatePoolEnabled();
            log.info(
                    "AI generate mode: mode={} useVerifiedCandidatePool={} scrapingDogContextEnabled={} enrichAfterGemini={} routeDuringPreview={}",
                    generateConfig.getMode(),
                    useVerifiedCandidatePool,
                    useScrapingDogContext,
                    generateConfig.isEnrichAfterGemini(),
                    generateConfig.isRouteDuringPreview()
            );

            long phaseStart = System.nanoTime();
            TravelResearchContext researchContext = useScrapingDogContext
                    ? travelResearchService.researchDestination(
                            req.getDestination(),
                            LocalDate.now(TRIP_TIME_ZONE),
                            req.getDays(),
                            req.getInterests(),
                            isPremium
                    )
                    : TravelResearchContext.empty(req.getDestination());
            researchMs = elapsedMs(phaseStart);

            List<ExtractedTravelCandidate> extractedCandidates = List.of();
            List<VerifiedCandidatePlace> verifiedPool = List.of();
            List<CandidatePlace> candidatePool = List.of();
            boolean fallbackPoolUsed = false;

            if (useVerifiedCandidatePool) {
                phaseStart = System.nanoTime();
                extractedCandidates = travelCandidateExtractionService.extractCandidates(researchContext, req);
                extractionMs = elapsedMs(phaseStart);

                phaseStart = System.nanoTime();
                verifiedPool = verifiedCandidatePoolService.buildVerifiedPool(
                        req,
                        extractedCandidates,
                        researchContext,
                        generateVerificationOptions()
                );
                verificationMs = elapsedMs(phaseStart);

                candidatePool = verifiedCandidatePoolService.toCandidatePlaces(verifiedPool);
                if (candidatePool.isEmpty()) {
                    candidatePool = candidatePoolService.buildPool(req.getDestination(), req.getInterests());
                    fallbackPoolUsed = true;
                }
                log.info("AI generate verified candidate context: extractedCandidateCount={} verifiedCandidateCount={} verifiedCounts={} fallbackPoolUsed={}",
                        extractedCandidates.size(),
                        verifiedPool.size(),
                        verifiedCategoryCounts(verifiedPool),
                        fallbackPoolUsed);
            } else {
                log.info("AI-first generate: skipping TravelCandidateExtractionService and VerifiedCandidatePoolService");
            }
            String prompt = buildPrompt(req, researchContext, candidatePool);
            phaseStart = System.nanoTime();
            String geminiResponse = geminiClient.generateContent(prompt);
            itineraryGeminiMs = elapsedMs(phaseStart);
            log.info("Gemini response received for user:{} chars={}", userId, geminiResponse.length());

            // Parse 3 plans từ Gemini
            List<Map<String, Object>> plans = parsePlans(geminiResponse);
            if (plans.isEmpty()) {
                throw new RuntimeException("Gemini did not return valid plans");
            }

            phaseStart = System.nanoTime();
            if (isGenerateCriticEnabled()) {
                List<AiGeneratedStop> previewStops = applyRealismPostProcessing(
                        extractStops(plans.get(0), req.getLanguage()),
                        req
                );
                AiCriticResult criticResult = aiCriticService.review(previewStops, req);
                log.info("AI generate preview critic enabled: score={} passed={}",
                        criticResult.getScore(), criticResult.isPassed());
            } else {
                log.info("AI generate preview critic disabled by config");
            }
            fillerMs = elapsedMs(phaseStart);

            for (int i = 0; i < plans.size(); i++) {
                Map<String, Object> plan = plans.get(i);
                plan.put("planIndex", i);
                plan.putIfAbsent("planId", "ai-plan-" + i + "-" + UUID.randomUUID());
            }

            // Update job
            job.setStatus(AiJobStatus.DONE);
            job.setCompletedAt(Instant.now());
            job.setOutput(Map.of("plan_count", plans.size()));
            jobRepo.save(job);

            log.info("AI trip generated for user:{} plans:{}", userId, plans.size());

            // Trả về plans array cho FE
            return Map.of("plans", plans);

        } catch (GolaException e) {
            failJob(job, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI trip generation failed for user:{} error:{}", userId, e.getMessage(), e);
            failJob(job, e.getMessage());
            throw GolaException.badRequest("AI generation failed: " + e.getMessage());
        } finally {
            runningGenerateLocks.remove(lockKey);
            log.info("AI generate timing totalMs={} researchMs={} extractionMs={} verificationMs={} itineraryGeminiMs={} fillerMs={}",
                    elapsedMs(totalStart),
                    researchMs,
                    extractionMs,
                    verificationMs,
                    itineraryGeminiMs,
                    fillerMs);
        }
    }

    @Transactional
    public Map<String, Object> confirmTripPlan(UUID userId, ConfirmAiPlanRequest req) {
        GenerateTripRequest tripRequest = req.getTripRequest();
        Map<String, Object> selectedPlan = req.getPlan();
        if (tripRequest == null || selectedPlan == null || selectedPlan.isEmpty()) {
            throw GolaException.badRequest("Selected AI plan is required");
        }

        int selectedPlanIndex = req.getSelectedPlanIndex() == null ? -1 : req.getSelectedPlanIndex();
        log.info(
                "Confirming AI plan for user:{} selectedPlanIndex={} title={} timelineDays={}",
                userId,
                selectedPlanIndex,
                selectedPlan.get("name"),
                selectedPlan.get("timeline") instanceof List<?> timeline ? timeline.size() : 0
        );

        var tripReq = new CreateTripRequest();
        tripReq.setTitle(buildTripTitle(tripRequest));
        tripReq.setOrigin(tripRequest.getOrigin());
        tripReq.setDestination(tripRequest.getDestination());
        LocalDate tripStartDate = LocalDate.now(TRIP_TIME_ZONE);
        tripReq.setStartDate(tripStartDate);
        tripReq.setEndDate(tripStartDate.plusDays(Math.max(1, tripRequest.getDays()) - 1L));
        var trip = tripService.createTrip(userId, tripReq);

        List<AiGeneratedStop> stops = applyRealismPostProcessing(
                extractStops(selectedPlan, tripRequest.getLanguage()),
                tripRequest
        );
        List<VerifiedCandidatePlace> confirmVerifiedPool = List.of();
        if (isVerifiedCandidatePoolEnabled()) {
            confirmVerifiedPool = verifiedCandidatePoolService.buildVerifiedPool(
                    tripRequest,
                    List.of(),
                    TravelResearchContext.empty(tripRequest.getDestination()),
                    generateVerificationOptions()
            );
            stops = missingSlotFillerService.fillMissingSlots(stops, tripRequest, confirmVerifiedPool);
        } else {
            log.info("AI-first confirm: skipping verified missing-slot filler before save");
        }

        if (isGenerateCriticEnabled()) {
            AiCriticResult criticResult = aiCriticService.review(stops, tripRequest);
            if (criticResult.getScore() < 80 && isDeepGapFillerEnabled()) {
                log.info("Itinerary critic score is low ({}/100). Running gap filler.", criticResult.getScore());
                List<CandidatePlace> pool = verifiedCandidatePoolService.toCandidatePlaces(confirmVerifiedPool);
                if (pool.isEmpty()) {
                    pool = candidatePoolService.buildPool(tripRequest.getDestination(), tripRequest.getInterests());
                }
                stops = gapFillerService.fill(stops, criticResult, tripRequest.getDestination(), pool);
            } else if (criticResult.getScore() < 80) {
                log.info("Itinerary critic score is low ({}/100), but deep gap filler is disabled by config.", criticResult.getScore());
            }
        } else {
            log.info("Initial confirm AI critic/gap filler disabled by config; use AI improve for deep repair");
        }

        logGenerationRealismSummary(selectedPlanIndex, stops, tripRequest);
        logBudgetConsistency(selectedPlan, stops);
        logRouteSanityWarnings(stops, tripRequest);
        logIntercityDurationSanityWarnings(stops);
        logPreferenceSanityWarnings(stops, tripRequest);
        logMealSanityWarnings(stops, tripRequest);
        // Sanitize draft stops before save
        stops = finalItinerarySanitizerService.sanitizeBeforeSave(stops, tripRequest);

        int savedStops = saveStops(trip.getId(), userId, stops, tripRequest.getDestination(), tripStartDate);

        // Sanitize stop entities after place enrichment
        List<TripStop> savedStopEntities = stopRepo.findByTrip_IdOrderByOrderIdxAsc(trip.getId());
        savedStopEntities = finalItinerarySanitizerService.sanitizeAfterEnrichment(savedStopEntities, tripRequest);

        TripQualityReport qualityReport = tripQualityGate.evaluate(savedStopEntities, tripStartDate, tripRequest.getDays());
        tripService.updateTripQuality(trip.getId(), qualityReport.getQualityScore(), qualityReport.getQualityWarning());

        log.info(
                "AI selected plan persisted for user:{} trip:{} selectedPlanIndex={} savedStops={} qualityScore={}",
                userId,
                trip.getId(),
                selectedPlanIndex,
                savedStops,
                qualityReport.getQualityScore()
        );

        return Map.of(
                "tripId", trip.getId().toString(),
                "selectedPlanIndex", selectedPlanIndex,
                "savedStops", savedStops
        );
    }

    @Transactional
    public void aiImproveTrip(UUID tripId, UUID userId) {
        // Load existing trip
        List<TripStop> existingStops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        if (existingStops.isEmpty()) {
            log.warn("aiImproveTrip: trip {} has no stops to improve", tripId);
            return;
        }
        // Discover destination from first stop's trip reference
        var tripRef = existingStops.get(0).getTrip();
        String destination = tripRef.getDestination();
        LocalDate startDate = tripRef.getStartDate() != null ? tripRef.getStartDate() : LocalDate.now(TRIP_TIME_ZONE);
        int totalDays = existingStops.stream().mapToInt(s -> {
            if (s.getArrivalAt() == null) return 1;
            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, s.getArrivalAt().atZone(TRIP_TIME_ZONE).toLocalDate()) + 1;
            return (int) Math.max(1, days);
        }).max().orElse(1);

        // Convert TripStop entities to AiGeneratedStop so critic/gap filler can work on them
        List<AiGeneratedStop> aiStops = existingStops.stream().map(ts -> {
            AiGeneratedStop s = new AiGeneratedStop();
            s.setPlaceName(ts.getName());
            s.setCategory(ts.getCategory());
            s.setLat(ts.getLat());
            s.setLng(ts.getLng());
            s.setImageUrl(ts.getImageUrl());
            s.setEstimatedCost(ts.getEstimatedCost());
            s.setSystemStop(Boolean.TRUE.equals(ts.getSystemStop()));
            if (ts.getArrivalAt() != null) {
                var dt = ts.getArrivalAt().atZone(TRIP_TIME_ZONE);
                long dayIdx = java.time.temporal.ChronoUnit.DAYS.between(startDate, dt.toLocalDate()) + 1;
                s.setDay((int) Math.max(1, dayIdx));
                s.setStartTime(String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
            } else {
                s.setDay(1);
            }
            if (ts.getDurationMin() != null) {
                s.setDurationMinutes(ts.getDurationMin());
            }
            return s;
        }).collect(java.util.stream.Collectors.toList());

        // Build a minimal request for critic
        GenerateTripRequest syntheticReq = new GenerateTripRequest();
        syntheticReq.setDestination(destination);
        syntheticReq.setDays(totalDays);
        syntheticReq.setLanguage("vi");

        AiCriticResult criticResult = isImproveCriticEnabled()
                ? aiCriticService.review(aiStops, syntheticReq)
                : AiCriticResult.builder().score(100).passed(true).build();
        log.info("aiImproveTrip: criticEnabled={} score={} passed={}",
                isImproveCriticEnabled(), criticResult.getScore(), criticResult.isPassed());
        List<VerifiedCandidatePlace> improveVerifiedPool = verifiedCandidatePoolService.buildVerifiedPool(
                syntheticReq,
                List.of(),
                TravelResearchContext.empty(destination),
                improveVerificationOptions()
        );
        List<AiGeneratedStop> originalAiStops = new ArrayList<>(aiStops);
        aiStops = missingSlotFillerService.fillMissingSlots(aiStops, syntheticReq, improveVerifiedPool);
        List<AiGeneratedStop> missingSlotNewStops = aiStops.stream()
                .filter(s -> originalAiStops.stream().noneMatch(existing ->
                        existing.getPlaceName() != null && existing.getPlaceName().equals(s.getPlaceName())))
                .toList();
        if (!missingSlotNewStops.isEmpty()) {
            log.info("aiImproveTrip: adding {} verified missing-slot stops to trip {}", missingSlotNewStops.size(), tripId);
            saveStops(tripId, userId, missingSlotNewStops, destination, startDate);
        }

        if (!criticResult.isPassed()) {
            // Build candidate pool and fill gaps
            List<CandidatePlace> pool = verifiedCandidatePoolService.toCandidatePlaces(improveVerifiedPool);
            if (pool.isEmpty()) {
                pool = candidatePoolService.buildPool(destination, List.of());
            }
            List<AiGeneratedStop> gapBaselineStops = new ArrayList<>(aiStops);
            List<AiGeneratedStop> filledStops = gapFillerService.fill(aiStops, criticResult, destination, pool);

            // Only save the new stops (ones not in existing)
            List<AiGeneratedStop> newStops = filledStops.stream()
                    .filter(s -> gapBaselineStops.stream().noneMatch(existing ->
                            existing.getPlaceName() != null && existing.getPlaceName().equals(s.getPlaceName())))
                    .toList();

            if (!newStops.isEmpty()) {
                log.info("aiImproveTrip: adding {} new gap-filled stops to trip {}", newStops.size(), tripId);
                saveStops(tripId, userId, newStops, destination, startDate);
            }
        }

        // Re-evaluate quality after enrichment sanitization
        List<TripStop> updatedStops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        updatedStops = finalItinerarySanitizerService.sanitizeAfterEnrichment(updatedStops, syntheticReq);

        TripQualityReport report = tripQualityGate.evaluate(updatedStops, startDate, totalDays);
        tripService.updateTripQuality(tripId, report.getQualityScore(), report.getQualityWarning());
        log.info("aiImproveTrip: trip {} improved, new qualityScore={}", tripId, report.getQualityScore());
    }

    private VerifiedCandidatePoolService.VerificationOptions generateVerificationOptions() {
        GolaProperties.Generate generate = properties.getAi() == null
                ? new GolaProperties.Generate()
                : properties.getAi().getGenerate();
        if (generate == null) generate = new GolaProperties.Generate();
        return new VerifiedCandidatePoolService.VerificationOptions(
                generate.getMaxProviderCalls(),
                generate.getMaxCandidatesToVerify(),
                generate.getTargetVerifiedCandidates(),
                generate.isFallbackCategorySearchEnabled()
        );
    }

    private VerifiedCandidatePoolService.VerificationOptions improveVerificationOptions() {
        GolaProperties.Improve improve = properties.getAi() == null
                ? new GolaProperties.Improve()
                : properties.getAi().getImprove();
        if (improve == null) improve = new GolaProperties.Improve();
        int maxProviderCalls = improve.getMaxProviderCalls();
        return new VerifiedCandidatePoolService.VerificationOptions(maxProviderCalls, maxProviderCalls, 10, true);
    }

    private boolean isGenerateCriticEnabled() {
        return properties.getAi() != null
                && properties.getAi().getGenerate() != null
                && properties.getAi().getGenerate().isCriticEnabled();
    }

    private GolaProperties.Generate getGenerateConfig() {
        if (properties.getAi() == null || properties.getAi().getGenerate() == null) {
            return new GolaProperties.Generate();
        }
        return properties.getAi().getGenerate();
    }

    private boolean isVerifiedCandidatePoolEnabled() {
        GolaProperties.Generate generate = getGenerateConfig();
        return generate.isUseVerifiedCandidatePool()
                || (generate.getMode() != null && !"ai_first".equalsIgnoreCase(generate.getMode().trim()));
    }

    private boolean isDeepGapFillerEnabled() {
        return getGenerateConfig().isDeepGapFillerEnabled();
    }

    private boolean isImproveCriticEnabled() {
        return properties.getAi() == null
                || properties.getAi().getImprove() == null
                || properties.getAi().getImprove().isCriticEnabled();
    }

    private Map<String, Long> verifiedCategoryCounts(List<VerifiedCandidatePlace> pool) {
        Map<String, Long> counts = new TreeMap<>();
        if (pool == null) return counts;
        for (VerifiedCandidatePlace candidate : pool) {
            counts.merge(String.valueOf(candidate.getCategory()), 1L, Long::sum);
        }
        return counts;
    }

    private long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
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
    private List<AiGeneratedStop> extractStops(Map<String, Object> plan, String language) {
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

                    String rawDescription = String.valueOf(item.getOrDefault("description", ""));
                    String description = cleanNaturalText(rawDescription);
                    stop.setDescription(description);

                    stop.setTimeOfDay(String.valueOf(
                            item.getOrDefault("time",
                                    item.getOrDefault("timeOfDay", "MORNING"))
                    ));

                    String startTime = stringValue(
                            item.getOrDefault("startTime", item.get("start_time"))
                    );
                    if (startTime == null && isValidStartTime(stop.getTimeOfDay())) {
                        startTime = stop.getTimeOfDay().trim();
                    }
                    stop.setStartTime(startTime);
                    stop.setDurationMinutes(parseInteger(
                            item.getOrDefault("durationMinutes",
                                    item.getOrDefault("durationMin", item.get("duration_minutes")))
                    ));
                    stop.setEstimatedCost(parseEstimatedCost(
                            item.getOrDefault("estimatedCost",
                                    item.getOrDefault("estimated_cost", item.get("cost")))
                    ));
                    stop.setCategory(normalizeStopCategory(stringValue(
                            item.getOrDefault("category", item.get("type"))
                    )));
                    stop.setSearchQuery(stringValue(
                            item.getOrDefault("searchQuery",
                                    item.getOrDefault("search_query", item.get("enrichmentQuery")))
                    ));
                    stop.setSystemStop(booleanValue(
                            item.getOrDefault("isSystemStop",
                                    item.getOrDefault("systemStop", item.get("system_stop")))
                    ));
                    stop.setMustHave(booleanValue(
                            item.getOrDefault("mustHave",
                                    item.getOrDefault("must_have", item.get("required")))
                    ));
                    stop.setFlexibility(stringValue(item.get("flexibility")));
                    stop.setCandidateProviderId(stringValue(
                            item.getOrDefault("candidateProviderId",
                                    item.getOrDefault("candidate_provider_id", item.get("providerId")))
                    ));
                    stop.setProviderTitle(stringValue(
                            item.getOrDefault("providerTitle",
                                    item.getOrDefault("provider_title", item.get("candidateTitle")))
                    ));
                    stop.setRationale(stringValue(item.getOrDefault("rationale", item.get("reason"))));
                    stop.setMealType(stringValue(item.get("mealType")));
                    stop.setExperienceType(stringValue(item.get("experienceType")));
                    Object travelFromPrevious = item.get("travelFromPrevious");
                    if (travelFromPrevious instanceof Map<?, ?> travelMap) {
                        stop.setTravelFromPrevious((Map<String, Object>) travelMap);
                    }

                    // Parse lat/lng from direct fields if provided by Gemini
                    Object latObj = item.getOrDefault("lat", item.get("latitude"));
                    Object lngObj = item.getOrDefault("lng", item.get("longitude"));
                    if (latObj instanceof Number latNum && lngObj instanceof Number lngNum) {
                        stop.setLat(latNum.doubleValue());
                        stop.setLng(lngNum.doubleValue());
                    }

                    // Fallback: try parsing from "Coordinates: lat, lng" embedded in description
                    if (!isValidCoordinate(stop.getLat(), stop.getLng()) && rawDescription != null) {
                        java.util.regex.Matcher m = COORD_PATTERN.matcher(rawDescription);
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

    private List<AiGeneratedStop> applyRealismPostProcessing(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }

        DestinationProfile profile = destinationProfile(req.getDestination());
        List<AiGeneratedStop> processed = new ArrayList<>(stops);
        Map<Integer, List<AiGeneratedStop>> byDay = groupStopsByDay(processed);
        int totalDays = Math.max(1, req.getDays());

        for (int day = 1; day <= totalDays; day++) {
            List<AiGeneratedStop> dayStops = byDay.computeIfAbsent(day, ignored -> new ArrayList<>());
            boolean finalDay = day == totalDays;
            boolean startsEarly = earliestStart(dayStops).map(time -> time.isBefore(LocalTime.of(10, 0))).orElse(true);
            boolean continuesAfterLunch = latestEnd(dayStops).map(time -> !time.isBefore(LocalTime.of(13, 30))).orElse(dayStops.size() >= 3);
            boolean continuesAfterDinner = latestEnd(dayStops).map(time -> !time.isBefore(LocalTime.of(18, 0))).orElse(dayStops.size() >= 4);

            if (startsEarly && !hasMealType(dayStops, "BREAKFAST")) {
                addGeneratedStop(processed, dayStops, recommendedMealStop(day, "BREAKFAST", profile, req));
            }

            if (needsLunch(finalDay, req, continuesAfterLunch) && !hasMealType(dayStops, "LUNCH")) {
                addGeneratedStop(processed, dayStops, recommendedMealStop(day, "LUNCH", profile, req));
            }

            if (!finalDay && continuesAfterDinner && !hasMealType(dayStops, "DINNER")) {
                addGeneratedStop(processed, dayStops, recommendedMealStop(day, "DINNER", profile, req));
            }

            if (finalDay && returnPreferenceAllowsDinner(req) && continuesAfterDinner && !hasMealType(dayStops, "DINNER")) {
                addGeneratedStop(processed, dayStops, recommendedMealStop(day, "DINNER", profile, req));
            }

            if (day == 1 && totalDays >= 2 && !hasMealType(dayStops, "DINNER") && dayOneNeedsEveningRhythm(dayStops)) {
                addGeneratedStop(processed, dayStops, recommendedMealStop(day, "DINNER", profile, req));
            }

            if (!hasChillStop(dayStops)) {
                addGeneratedStop(processed, dayStops, recommendedChillStop(day, profile, req, finalDay));
            }

            if (profile == DestinationProfile.BEACH && day == 1 && !hasExperienceType(dayStops, "SUNSET") && !isDayTooFull(dayStops)) {
                addGeneratedStop(processed, dayStops, recommendedExperienceStop(day, "SUNSET", profile, req));
            }
            if (profile == DestinationProfile.MOUNTAIN && day > 1 && !hasExperienceType(dayStops, "SUNRISE") && !isDayTooFull(dayStops)) {
                addGeneratedStop(processed, dayStops, recommendedExperienceStop(day, "SUNRISE", profile, req));
            }
        }

        processed.sort(Comparator
                .comparingInt((AiGeneratedStop stop) -> Math.max(1, stop.getDay()))
                .thenComparing(stop -> parseStartTime(stop.getStartTime()).orElse(fallbackStartTime(stop.getTimeOfDay()))));
        return processed;
    }

    private void logGenerationRealismSummary(int selectedPlanIndex, List<AiGeneratedStop> stops, GenerateTripRequest req) {
        DestinationProfile profile = destinationProfile(req.getDestination());
        Map<Integer, List<AiGeneratedStop>> byDay = groupStopsByDay(stops);
        for (Map.Entry<Integer, List<AiGeneratedStop>> entry : byDay.entrySet()) {
            List<AiGeneratedStop> dayStops = entry.getValue();
            long mealStops = dayStops.stream().filter(this::isMealOrCafeStop).count();
            long systemStops = dayStops.stream().filter(this::isSystemStop).count();
            boolean hasBreakfast = hasMealType(dayStops, "BREAKFAST");
            boolean hasLunch = hasMealType(dayStops, "LUNCH");
            boolean hasDinner = hasMealType(dayStops, "DINNER");
            boolean hasChill = hasChillStop(dayStops);
            boolean hasReturn = dayStops.stream().anyMatch(stop -> isTransportStop(stop) && looksLikeReturnLeg(stop, req));
            log.info(
                    "AI realism summary plan={} destinationProfile={} day={} stops={} systemStops={} mealsOrCafe={} breakfast={} lunch={} dinner={} chill={} returnLeg={}",
                    selectedPlanIndex,
                    profile,
                    entry.getKey(),
                    dayStops.size(),
                    systemStops,
                    mealStops,
                    hasBreakfast,
                    hasLunch,
                    hasDinner,
                    hasChill,
                    hasReturn
            );
        }
    }

    private DestinationProfile destinationProfile(String destination) {
        String normalized = normalizeAscii(destination);
        if (normalized.contains("vung tau")
                || normalized.contains("nha trang")
                || normalized.contains("da nang")
                || normalized.contains("phu quoc")
                || normalized.contains("quy nhon")
                || normalized.contains("mui ne")
                || normalized.contains("phan thiet")
                || normalized.contains("ha long")) {
            return DestinationProfile.BEACH;
        }
        if (normalized.contains("da lat")
                || normalized.contains("sapa")
                || normalized.contains("sa pa")
                || normalized.contains("ha giang")
                || normalized.contains("moc chau")
                || normalized.contains("tam dao")
                || normalized.contains("cao bang")) {
            return DestinationProfile.MOUNTAIN;
        }
        if (normalized.contains("hoi an")
                || normalized.contains("hue")
                || normalized.contains("ninh binh")
                || normalized.contains("ha noi")) {
            return DestinationProfile.CULTURAL_CITY;
        }
        return DestinationProfile.CITY;
    }

    private String safeDestination(GenerateTripRequest req) {
        if (req == null || req.getDestination() == null || req.getDestination().isBlank()) {
            return "điểm đến";
        }
        return req.getDestination().trim();
    }

    private Map<Integer, List<AiGeneratedStop>> groupStopsByDay(List<AiGeneratedStop> stops) {
        Map<Integer, List<AiGeneratedStop>> byDay = new TreeMap<>();
        for (AiGeneratedStop stop : stops) {
            byDay.computeIfAbsent(Math.max(1, stop.getDay()), ignored -> new ArrayList<>()).add(stop);
        }
        return byDay;
    }

    private void addGeneratedStop(List<AiGeneratedStop> allStops, List<AiGeneratedStop> dayStops, AiGeneratedStop stop) {
        if (stop == null) return;
        allStops.add(stop);
        dayStops.add(stop);
        log.info("AI realism post-process inserted stop day={} name='{}' category={} mealType={} experienceType={}",
                stop.getDay(), stop.getPlaceName(), stop.getCategory(), stop.getMealType(), stop.getExperienceType());
    }

    private AiGeneratedStop recommendedMealStop(int day, String mealType, DestinationProfile profile, GenerateTripRequest req) {
        String destination = safeDestination(req);
        AiGeneratedStop stop = baseRecommendedStop(day);
        stop.setCategory("FOOD");
        stop.setMealType(mealType);
        stop.setExperienceType("LOCAL_FOOD");
        stop.setMustHave(true);
        stop.setFlexibility("FLEXIBLE");
        switch (mealType) {
            case "BREAKFAST" -> {
                stop.setTimeOfDay("Morning");
                stop.setStartTime("08:00");
                stop.setDurationMinutes(45);
                stop.setEstimatedCost(BigDecimal.valueOf(60000));
                stop.setPlaceName(profile == DestinationProfile.MOUNTAIN
                        ? "Ăn sáng món nóng địa phương tại " + destination
                        : "Ăn sáng địa phương tại " + destination);
                stop.setSearchQuery("quán ăn sáng nổi tiếng " + destination);
                stop.setDescription("Bắt đầu ngày mới bằng bữa sáng địa phương, giữ lịch nhẹ trước khi tham quan.");
            }
            case "LUNCH" -> {
                stop.setTimeOfDay("Afternoon");
                stop.setStartTime("12:00");
                stop.setDurationMinutes(75);
                stop.setEstimatedCost(BigDecimal.valueOf(120000));
                stop.setPlaceName(profile == DestinationProfile.BEACH
                        ? "Ăn trưa hải sản địa phương tại " + destination
                        : "Ăn trưa món địa phương tại " + destination);
                stop.setSearchQuery(profile == DestinationProfile.BEACH
                        ? "nhà hàng hải sản nổi tiếng " + destination
                        : "nhà hàng địa phương nổi tiếng " + destination);
                stop.setDescription("Dừng ăn trưa đúng giờ để nghỉ sức và tránh nhồi quá nhiều hoạt động vào buổi trưa.");
            }
            default -> {
                stop.setTimeOfDay("Evening");
                stop.setStartTime("18:30");
                stop.setDurationMinutes(90);
                stop.setEstimatedCost(BigDecimal.valueOf(180000));
                stop.setPlaceName(profile == DestinationProfile.BEACH
                        ? "Ăn tối hải sản gần trung tâm " + destination
                        : "Ăn tối món địa phương tại " + destination);
                stop.setSearchQuery(profile == DestinationProfile.BEACH
                        ? "quán hải sản ngon " + destination
                        : "quán ăn tối địa phương " + destination);
                stop.setDescription("Kết thúc ngày bằng bữa tối thoải mái, ưu tiên khu gần lịch trình để hạn chế di chuyển xa.");
            }
        }
        return stop;
    }

    private AiGeneratedStop recommendedChillStop(int day, DestinationProfile profile, GenerateTripRequest req, boolean finalDay) {
        String destination = safeDestination(req);
        AiGeneratedStop stop = baseRecommendedStop(day);
        stop.setCategory(profile == DestinationProfile.BEACH ? "CAFE" : "CAFE");
        stop.setMealType("DRINK");
        stop.setExperienceType(profile == DestinationProfile.BEACH ? "CAFE_CHILL" : "CAFE_CHILL");
        stop.setFlexibility("FLEXIBLE");
        stop.setMustHave(false);
        stop.setTimeOfDay(finalDay ? "Morning" : "Afternoon");
        stop.setStartTime(finalDay ? "09:30" : "15:30");
        stop.setDurationMinutes(60);
        stop.setEstimatedCost(BigDecimal.valueOf(70000));
        if (profile == DestinationProfile.BEACH) {
            stop.setPlaceName("Cà phê nghỉ chân gần biển " + destination);
            stop.setSearchQuery("cafe view biển " + destination);
            stop.setDescription("Nghỉ chân uống nước gần biển, giữ nhịp chuyến đi thoải mái hơn.");
        } else if (profile == DestinationProfile.MOUNTAIN) {
            stop.setPlaceName("Cà phê view núi tại " + destination);
            stop.setSearchQuery("cafe view đẹp " + destination);
            stop.setDescription("Dừng uống cà phê và ngắm cảnh, tránh lịch trình quá dày.");
        } else {
            stop.setPlaceName("Cà phê địa phương tại " + destination);
            stop.setSearchQuery("quán cafe nổi tiếng " + destination);
            stop.setDescription("Nghỉ chân tại một quán cà phê địa phương trước khi tiếp tục lịch trình.");
        }
        return stop;
    }

    private AiGeneratedStop recommendedExperienceStop(int day, String experienceType, DestinationProfile profile, GenerateTripRequest req) {
        String destination = safeDestination(req);
        AiGeneratedStop stop = baseRecommendedStop(day);
        stop.setCategory("SIGHTSEEING");
        stop.setExperienceType(experienceType);
        stop.setFlexibility("FLEXIBLE");
        stop.setMustHave(false);
        stop.setTimeOfDay("Evening");
        stop.setStartTime("17:15");
        stop.setDurationMinutes(60);
        stop.setEstimatedCost(BigDecimal.ZERO);
        if ("SUNRISE".equals(experienceType)) {
            stop.setTimeOfDay("Morning");
            stop.setStartTime("05:30");
            stop.setPlaceName(profile == DestinationProfile.MOUNTAIN ? "Ngắm bình minh săn mây tại " + destination : "Ngắm bình minh tại " + destination);
            stop.setSearchQuery(profile == DestinationProfile.MOUNTAIN ? "điểm săn mây bình minh " + destination : "điểm ngắm bình minh " + destination);
            stop.setDescription("Hoạt động sáng sớm dành cho lịch trình nhẹ, chỉ nên đi nếu thời tiết phù hợp.");
        } else {
            stop.setPlaceName(profile == DestinationProfile.BEACH ? "Dạo biển ngắm hoàng hôn tại " + destination : "Dạo phố ngắm hoàng hôn tại " + destination);
            stop.setSearchQuery(profile == DestinationProfile.BEACH ? "điểm ngắm hoàng hôn biển " + destination : "điểm ngắm hoàng hôn " + destination);
            stop.setDescription("Chèn một khoảng thư giãn cuối ngày thay vì kết thúc lịch quá sớm.");
        }
        return stop;
    }

    private AiGeneratedStop baseRecommendedStop(int day) {
        AiGeneratedStop stop = new AiGeneratedStop();
        stop.setDay(day);
        stop.setSystemStop(false);
        return stop;
    }

    private boolean needsLunch(boolean finalDay, GenerateTripRequest req, boolean continuesAfterLunch) {
        if (!continuesAfterLunch) return false;
        if (!finalDay) return true;
        String returnPreference = normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON");
        return !"MORNING".equals(returnPreference);
    }

    private boolean returnPreferenceAllowsDinner(GenerateTripRequest req) {
        String returnPreference = normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON");
        return "EVENING".equals(returnPreference) || "NIGHT".equals(returnPreference);
    }

    private boolean hasMealType(List<AiGeneratedStop> dayStops, String mealType) {
        return dayStops.stream().anyMatch(stop -> mealType.equalsIgnoreCase(String.valueOf(stop.getMealType()))
                || switch (mealType) {
                    case "BREAKFAST" -> isBreakfastStop(stop);
                    case "LUNCH" -> isLunchStop(stop);
                    case "DINNER" -> isDinnerMealStop(stop);
                    default -> false;
                });
    }

    private boolean hasChillStop(List<AiGeneratedStop> dayStops) {
        return dayStops.stream().anyMatch(stop -> "DRINK".equalsIgnoreCase(String.valueOf(stop.getMealType()))
                || "CAFE_CHILL".equalsIgnoreCase(String.valueOf(stop.getExperienceType()))
                || isChillText(stop));
    }

    private boolean hasExperienceType(List<AiGeneratedStop> dayStops, String experienceType) {
        return dayStops.stream().anyMatch(stop -> experienceType.equalsIgnoreCase(String.valueOf(stop.getExperienceType()))
                || combinedStopText(stop).toLowerCase(Locale.ROOT).contains(experienceType.toLowerCase(Locale.ROOT)));
    }

    private Optional<LocalTime> earliestStart(List<AiGeneratedStop> dayStops) {
        return dayStops.stream().map(AiGeneratedStop::getStartTime).map(this::parseStartTime).flatMap(Optional::stream).min(LocalTime::compareTo);
    }

    private Optional<LocalTime> latestEnd(List<AiGeneratedStop> dayStops) {
        return dayStops.stream().map(this::stopEndTime).flatMap(Optional::stream).max(LocalTime::compareTo);
    }

    private int saveStops(UUID tripId, UUID userId, List<AiGeneratedStop> stops, String destination, LocalDate tripStartDate) {
        int saved = 0;
        Map<Integer, Instant> previousArrivalByDay = new HashMap<>();
        Map<Integer, Integer> previousDurationByDay = new HashMap<>();
        Map<Integer, GeoPoint> previousLocalCoordinateByDay = new HashMap<>();
        Set<String> usedProviderCandidateKeys = new HashSet<>();
        Set<String> reservedExactVenueKeys = reservedExactVenueKeys(stops);
        LocalClusterState localCluster = new LocalClusterState();
        GeoFence destinationFence = resolveDestinationFence(destination, stops);
        for (AiGeneratedStop aiStop : stops) {
            if (aiStop.getPlaceName() == null || aiStop.getPlaceName().isBlank()) continue;
            String resolutionFailureContext = "NOT_RESOLVED";
            try {
                var addReq = new AddStopRequest();
                addReq.setName(aiStop.getPlaceName().trim());
                addReq.setNotes(AiItineraryParser.buildNotes(aiStop));
                String normalizedCategory = normalizeStopCategory(aiStop.getCategory());
                boolean accommodationCheckinStop = isAccommodationCheckinStop(aiStop, normalizedCategory);
                if (accommodationCheckinStop) {
                    normalizedCategory = "HOTEL";
                }
                if ("FOOD".equals(normalizedCategory) && isMarketStyleStop(aiStop)) {
                    normalizedCategory = "MARKET";
                    addReq.setName(toMarketStopName(aiStop.getPlaceName()));
                    log.info("Recategorized market-style food stop '{}' as MARKET", aiStop.getPlaceName());
                }
                addReq.setCategory(normalizedCategory);
                boolean hasGeminiCoords = isValidCoordinate(aiStop.getLat(), aiStop.getLng());
                int day = Math.max(1, aiStop.getDay());
                int durationMinutes = resolveDurationMinutes(aiStop);
                Instant arrivalAt = calculateArrivalAt(
                        aiStop,
                        tripStartDate,
                        previousArrivalByDay.get(day),
                        previousDurationByDay.get(day)
                );
                addReq.setArrivalAt(arrivalAt);
                addReq.setDurationMin(durationMinutes);
                addReq.setEstimatedCost(aiStop.getEstimatedCost());
                addReq.setProviderId(aiStop.getCandidateProviderId());
                addReq.setProviderTitle(aiStop.getProviderTitle());
                if (aiStop.getEstimatedCost() == null) {
                    log.warn("Missing estimatedCost for AI stop '{}'; saving null cost", aiStop.getPlaceName());
                }
                addReq.setOrderIdx(orderIdxForArrival(day, arrivalAt));

                boolean isRouteStop = isTransportStop(aiStop);
                boolean isSystemStop = isRouteStop || (isSystemStop(aiStop) && !accommodationCheckinStop);
                boolean isRealPlaceStop = !isSystemStop && isRealPlaceStop(aiStop);
                addReq.setSystemStop(isSystemStop);
                String providerQuery = cleanProviderQueryForStop(aiStop, destination);
                GeoPoint previousLocalCoordinate = previousLocalCoordinateByDay.get(day);
                GeoPoint enrichmentAnchor = localCluster.anchor()
                        .orElse(previousLocalCoordinate != null ? previousLocalCoordinate : destinationFence.center());
                PlaceDetail enrichDetail = null;
                PlaceDetail goongDetail = null;
                final String categoryForEnrichment = normalizedCategory;

                if (isRealPlaceStop) {
                    CompletableFuture<PlaceDetail> enrichFuture = CompletableFuture.supplyAsync(
                            () -> placeEnrichmentService.enrichForStop(
                                    providerQuery,
                                    destination,
                                    categoryForEnrichment,
                                    enrichmentAnchor.lat(),
                                    enrichmentAnchor.lng(),
                                    destinationFence.center().lat(),
                                    destinationFence.center().lng(),
                                    accommodationCheckinStop ? PlaceEnrichmentService.SerpApiBudget.of(4) : PlaceEnrichmentService.SerpApiBudget.of(3)
                            ),
                            placeResolutionExecutor
                    );
                    CompletableFuture<PlaceDetail> goongFuture = CompletableFuture.supplyAsync(
                            () -> resolveWithGoong(providerQuery, destination),
                            placeResolutionExecutor
                    );

                    try {
                        CompletableFuture.allOf(enrichFuture, goongFuture).join();
                    } catch (CompletionException e) {
                        log.warn("Parallel place resolution failed for '{}': {}", aiStop.getPlaceName(), e.getMessage());
                    }
                    enrichDetail = getPlaceResolutionResult(enrichFuture, "enrichment", aiStop.getPlaceName());
                    goongDetail = getPlaceResolutionResult(goongFuture, "goong", aiStop.getPlaceName());
                    enrichDetail = rejectDuplicateProviderCandidate(
                            enrichDetail,
                            aiStop,
                            normalizedCategory,
                            usedProviderCandidateKeys,
                            reservedExactVenueKeys
                    );
                    if (shouldRetryGenericEnrichment(enrichDetail, aiStop, normalizedCategory)) {
                        PlaceDetail retryDetail = resolveAlternateGenericEnrichment(
                                aiStop,
                                normalizedCategory,
                                destination,
                                enrichmentAnchor,
                                destinationFence,
                                usedProviderCandidateKeys,
                                reservedExactVenueKeys
                        );
                        if (retryDetail != null) {
                            enrichDetail = retryDetail;
                        }
                    }
                    if (enrichDetail != null && enrichDetail.getRejectedReason() != null) {
                        addReq.setPlaceDataRejectReason(enrichDetail.getRejectedReason());
                    }
                    resolutionFailureContext = "enrichmentSource=" + (enrichDetail == null ? null : enrichDetail.getDataSource())
                            + ", enrichmentRejectedReason=" + (enrichDetail == null ? "NO_ENRICHMENT_RESULT" : nullToEmpty(enrichDetail.getRejectedReason()))
                            + ", goongSource=" + (goongDetail == null ? null : goongDetail.getDataSource())
                            + ", goongRejectedReason=" + (goongDetail == null ? "NO_GOONG_RESULT" : nullToEmpty(goongDetail.getRejectedReason()));
                }

                if (isRealPlaceStop && enrichDetail != null
                        && "SERPAPI".equalsIgnoreCase(String.valueOf(enrichDetail.getDataSource()))
                        && applyCandidateCoordinate(addReq, aiStop, normalizedCategory, destination, destinationFence, localCluster, previousLocalCoordinate, enrichDetail, "SERPAPI")) {
                    log.info("Coords for '{}' resolved via SERPAPI", aiStop.getPlaceName());
                    addReq.setDataSource("SERPAPI");
                } else if (isRealPlaceStop && applyCandidateCoordinate(addReq, aiStop, normalizedCategory, destination, destinationFence, localCluster, previousLocalCoordinate, goongDetail, "GOONG")) {
                    log.info("Coords for '{}' resolved via GOONG fallback", aiStop.getPlaceName());
                    addReq.setDataSource("GOONG");
                } else if (hasGeminiCoords && (isSystemStop || !isRealPlaceStop)) {
                    addReq.setLat(aiStop.getLat());
                    addReq.setLng(aiStop.getLng());
                    log.info("Coords for system/non-place stop '{}' kept from AI_SYSTEM only", aiStop.getPlaceName());
                    addReq.setDataSource(isSystemStop ? "AI_SYSTEM" : "GEMINI");
                } else if (isRealPlaceStop && applyCandidateCoordinate(addReq, aiStop, normalizedCategory, destination, destinationFence, localCluster, previousLocalCoordinate, enrichDetail, "ENRICHMENT")) {
                    log.info("Coords for '{}' resolved via ENRICHMENT", aiStop.getPlaceName());
                    if (enrichDetail != null && enrichDetail.getDataSource() != null) {
                        addReq.setDataSource(enrichDetail.getDataSource());
                    }
                } else if (hasGeminiCoords && isRealPlaceStop) {
                    log.warn("Ignored GEMINI_ONLY coords for real AI stop '{}' destination='{}'; waiting for trusted geocode",
                            aiStop.getPlaceName(), destination);
                }

                if (isRealPlaceStop && !isValidCoordinate(addReq.getLat(), addReq.getLng())) {
                    String enrichmentReason = enrichDetail == null ? "NO_ENRICHMENT_RESULT" : nullToEmpty(enrichDetail.getRejectedReason());
                    String goongReason = goongDetail == null ? "NO_GOONG_RESULT" : nullToEmpty(goongDetail.getRejectedReason());
                    log.warn("Missing reliable coordinates for AI stop '{}' category={} realPlace=true destination='{}' enrichmentSource={} enrichmentRejectedReason={} goongSource={} goongRejectedReason={}",
                            aiStop.getPlaceName(), normalizedCategory, destination,
                            enrichDetail == null ? null : enrichDetail.getDataSource(), enrichmentReason,
                            goongDetail == null ? null : goongDetail.getDataSource(), goongReason);
                    if (isMealOrCafeCategory(normalizedCategory)) {
                        GeoPoint approximate = localCluster.anchor().orElse(destinationFence.center());
                        addReq.setLat(approximate.lat());
                        addReq.setLng(approximate.lng());
                        addReq.setDataSource("APPROX");
                        addReq.setHasRealCoordinates(false);
                        addReq.setPlaceDataRejectReason("APPROXIMATE_COORDINATES:" + enrichmentReason + ";GOONG:" + goongReason);
                        log.warn("Saving required meal/cafe stop '{}' with APPROX coordinates [{}, {}]", aiStop.getPlaceName(), approximate.lat(), approximate.lng());
                    }
                }

                if (accommodationCheckinStop && hasRealHotelPlaceCandidate(enrichDetail)) {
                    addReq.setName("Nhận phòng tại " + hotelDisplayName(enrichDetail));
                    addReq.setSystemStop(false);
                    isSystemStop = false;
                    isRealPlaceStop = true;
                    if (!hasRealHotelCandidate(enrichDetail)) {
                        addReq.setPlaceDataRejectReason("HOTEL_MISSING_PHOTO_OR_RATING");
                    }
                }

                if (isRealPlaceStop && enrichDetail != null) {
                    if (accommodationCheckinStop && hasRealHotelCandidate(enrichDetail)) {
                        addReq.setName("Nhận phòng tại " + enrichDetail.getName());
                    }
                    addReq.setImageUrl(enrichDetail.getImageUrl());
                    addReq.setPhotoUrls(enrichDetail.getPhotoUrls());
                    addReq.setPhone(enrichDetail.getPhone());
                    addReq.setWebsite(enrichDetail.getWebsite());
                    addReq.setRating(enrichDetail.getRating());
                    addReq.setReviewCount(enrichDetail.getReviewCount());
                    if (addReq.getEstimatedCost() == null && enrichDetail.getEstimatedCost() != null) {
                        addReq.setEstimatedCost(enrichDetail.getEstimatedCost());
                    }
                    addReq.setImageSource(enrichDetail.getImageSource());
                    addReq.setPlaceAddress(enrichDetail.getPlaceAddress() != null
                            ? enrichDetail.getPlaceAddress()
                            : enrichDetail.getAddress());
                    addReq.setOpeningHoursText(enrichDetail.getOpeningHours());
                    addReq.setOpenNow(enrichDetail.getOpenNow());
                    addReq.setBusinessStatus(enrichDetail.getProviderSource() != null ? enrichDetail.getProviderSource() : enrichDetail.getBusinessStatus());
                    addReq.setNextOpenCloseText(enrichDetail.getNextOpenCloseText());
                    addReq.setHasOpeningHours(enrichDetail.getHasOpeningHours());
                    addReq.setProviderTitle(enrichDetail.getProviderTitle() != null ? enrichDetail.getProviderTitle() : enrichDetail.getName());
                    addReq.setProviderId(enrichDetail.getProviderId());
                    addReq.setProviderSource(enrichDetail.getProviderSource() != null ? enrichDetail.getProviderSource() : enrichDetail.getDataSource());
                    if (addReq.getDataSource() == null) {
                        addReq.setDataSource(enrichDetail.getDataSource());
                    }
                }
                if (accommodationCheckinStop && !hasRealHotelPlaceCandidate(enrichDetail)) {
                    addReq.setName("Nhận phòng khách sạn đã đặt");
                    addReq.setCategory("CHECKIN");
                    addReq.setSystemStop(true);
                    isSystemStop = true;
                    isRealPlaceStop = false;
                    addReq.setLat(null);
                    addReq.setLng(null);
                    addReq.setDataSource("SYSTEM");
                    addReq.setImageUrl(null);
                    addReq.setImageSource("CATEGORY_FALLBACK");
                    addReq.setRating(null);
                    addReq.setReviewCount(null);
                    addReq.setBusinessStatus(null);
                    addReq.setHasRealPhoto(false);
                    addReq.setHasRealCoordinates(false);
                    addReq.setPlaceAddress(null);
                    addReq.setProviderTitle(null);
                    addReq.setProviderId(null);
                    addReq.setProviderSource("SYSTEM");
                    addReq.setPlaceDataRejectReason("HOTEL_NOT_VERIFIED");
                }
                finalizeStopQuality(addReq, isSystemStop);
                if (addReq.getImageSource() == null) {
                    addReq.setImageSource("CATEGORY_FALLBACK");
                }

                log.info("Enriching stop: {} -> imageSource={} hasImage={} rating={} reviews={}",
                        aiStop.getPlaceName(),
                        addReq.getImageSource(),
                        addReq.getImageUrl() != null,
                        addReq.getRating(),
                        addReq.getReviewCount());

                tripService.addStop(tripId, userId, addReq);
                previousArrivalByDay.put(day, arrivalAt);
                previousDurationByDay.put(day, durationMinutes);
                if (isRealPlaceStop && Boolean.TRUE.equals(addReq.getHasRealCoordinates())) {
                    GeoPoint accepted = new GeoPoint(addReq.getLat(), addReq.getLng());
                    previousLocalCoordinateByDay.put(day, accepted);
                    localCluster.accept(aiStop, normalizedCategory, accepted);
                }
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save AI stop '{}' category={} realPlace={} {} exception={}",
                        aiStop.getPlaceName(), normalizeStopCategory(aiStop.getCategory()), isRealPlaceStop(aiStop),
                        resolutionFailureContext, e.toString(), e);
            }
        }
        return saved;
    }

    private PlaceDetail getPlaceResolutionResult(
            CompletableFuture<PlaceDetail> future,
            String source,
            String placeName) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Place {} resolution interrupted for '{}': {}", source, placeName, e.getMessage());
            return null;
        } catch (ExecutionException e) {
            log.warn("Place {} resolution failed for '{}': {}", source, placeName, e.getMessage());
            return null;
        }
    }

    private void finalizeStopQuality(AddStopRequest addReq, boolean systemStop) {
        boolean hasRealCoordinates = isValidCoordinate(addReq.getLat(), addReq.getLng())
                && isTrustedCoordinateSource(addReq.getDataSource());
        boolean hasRealPhoto = addReq.getImageUrl() != null
                && !addReq.getImageUrl().isBlank()
                && !addReq.getImageUrl().contains("picsum.photos")
                && ("SERPAPI".equalsIgnoreCase(String.valueOf(addReq.getImageSource()))
                || ("WIKIMEDIA".equalsIgnoreCase(String.valueOf(addReq.getImageSource()))
                && Set.of("SIGHTSEEING", "MARKET", "OTHER").contains(normalizedCategoryForPhoto(addReq.getCategory()))));
        boolean hasOpeningHours = addReq.getOpeningHoursText() != null && !addReq.getOpeningHoursText().isBlank();

        addReq.setSystemStop(systemStop);
        addReq.setHasRealCoordinates(hasRealCoordinates);
        addReq.setHasRealPhoto(hasRealPhoto);
        addReq.setHasOpeningHours(hasOpeningHours);

        if (systemStop) {
            addReq.setImageUrl(null);
            addReq.setRating(null);
            addReq.setReviewCount(null);
            addReq.setOpenNow(null);
            addReq.setOpeningHoursText(null);
            addReq.setBusinessStatus(null);
            addReq.setNextOpenCloseText(null);
            addReq.setImageSource("CATEGORY_FALLBACK");
            addReq.setDataSource("SYSTEM");
            addReq.setEnrichmentStatus("SYSTEM_STOP");
            addReq.setHasRealCoordinates(false);
            addReq.setHasRealPhoto(false);
            addReq.setHasOpeningHours(false);
            return;
        }

        boolean hasProviderData = hasRealPhoto
                || addReq.getRating() != null
                || addReq.getReviewCount() != null
                || hasOpeningHours
                || (addReq.getPlaceAddress() != null && !addReq.getPlaceAddress().isBlank());
        if (hasRealCoordinates && hasProviderData) {
            addReq.setEnrichmentStatus("ENRICHED");
        } else if (hasRealCoordinates || hasProviderData) {
            addReq.setEnrichmentStatus("PARTIAL");
        } else {
            addReq.setEnrichmentStatus("FAILED");
        }
        if (addReq.getDataSource() == null || addReq.getDataSource().isBlank()) {
            addReq.setDataSource(hasProviderData ? "ENRICHMENT" : "NONE");
        }
        if (addReq.getImageSource() == null || addReq.getImageSource().isBlank()) {
            addReq.setImageSource(hasRealPhoto ? addReq.getDataSource() : "CATEGORY_FALLBACK");
        }
    }

    private String normalizedCategoryForPhoto(String category) {
        return category == null ? "OTHER" : category.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean isTrustedCoordinateSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = source.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "GOONG", "SERPAPI", "MANUAL", "SAVED_PLACE" -> true;
            default -> false;
        };
    }

    private boolean applyCandidateCoordinate(
            AddStopRequest addReq,
            AiGeneratedStop stop,
            String category,
            String destination,
            GeoFence destinationFence,
            LocalClusterState localCluster,
            GeoPoint previousLocalCoordinate,
            PlaceDetail detail,
            String source) {
        if (detail == null) return false;
        if (!isUsableProviderCoordinateForStop(detail, stop, category, source)) {
            log.warn(
                    "Rejected {} coords for AI stop '{}' category={} because provider candidate '{}' does not match stop intent",
                    source,
                    stop.getPlaceName(),
                    category,
                    detail.getName()
            );
            return false;
        }
        return applyCandidateCoordinate(
                addReq,
                stop,
                category,
                destination,
                destinationFence,
                localCluster,
                previousLocalCoordinate,
                detail.getLat(),
                detail.getLng(),
                source
        );
    }

    private boolean applyCandidateCoordinate(
            AddStopRequest addReq,
            AiGeneratedStop stop,
            String category,
            String destination,
            GeoFence destinationFence,
            LocalClusterState localCluster,
            GeoPoint previousLocalCoordinate,
            Double lat,
            Double lng,
            String source) {
        if (!isValidCoordinate(lat, lng)) return false;

        GeoPoint candidate = new GeoPoint(lat, lng);
        double fromDestinationKm = haversineKm(
                destinationFence.center().lat(),
                destinationFence.center().lng(),
                candidate.lat(),
                candidate.lng()
        );
        if (fromDestinationKm > destinationFence.radiusKm()) {
            log.warn(
                    "Rejected {} coords for AI stop '{}' category={} destination='{}': {}km from destination center exceeds {}km",
                    source,
                    stop.getPlaceName(),
                    category,
                    destination,
                    roundDistance(fromDestinationKm),
                    destinationFence.radiusKm()
            );
            return false;
        }

        Optional<GeoPoint> clusterAnchor = localCluster.anchor();
        if (clusterAnchor.isPresent() && requiresLocalCluster(stop, category)) {
            GeoPoint anchor = clusterAnchor.get();
            double fromClusterKm = haversineKm(
                    anchor.lat(),
                    anchor.lng(),
                    candidate.lat(),
                    candidate.lng()
            );
            double clusterLimitKm = localClusterLimitKm(stop, category, destination);
            if (fromClusterKm > clusterLimitKm) {
                log.warn(
                        "Rejected {} coords for AI local stop '{}' category={} destination='{}': {}km from local cluster exceeds {}km",
                        source,
                        stop.getPlaceName(),
                        category,
                        destination,
                        roundDistance(fromClusterKm),
                        clusterLimitKm
                );
                return false;
            }
        }

        if (previousLocalCoordinate != null) {
            double fromPreviousKm = haversineKm(
                    previousLocalCoordinate.lat(),
                    previousLocalCoordinate.lng(),
                    candidate.lat(),
                    candidate.lng()
            );
            double sequenceLimitKm = requiresLocalCluster(stop, category) ? 12.0 : 40.0;
            if (fromPreviousKm > sequenceLimitKm) {
                log.warn(
                        "Rejected {} coords for AI stop '{}' category={}: {}km from previous local stop exceeds {}km",
                        source,
                        stop.getPlaceName(),
                        category,
                        roundDistance(fromPreviousKm),
                        sequenceLimitKm
                );
                return false;
            }
        }

        addReq.setLat(candidate.lat());
        addReq.setLng(candidate.lng());
        log.info(
                "Accepted {} coords for AI stop '{}' destination='{}': {}km from center",
                source,
                stop.getPlaceName(),
                destination,
                roundDistance(fromDestinationKm)
        );
        return true;
    }

    private PlaceDetail rejectDuplicateProviderCandidate(
            PlaceDetail detail,
            AiGeneratedStop stop,
            String category,
            Set<String> usedProviderCandidateKeys,
            Set<String> reservedExactVenueKeys) {
        if (detail == null || !tracksProviderReuse(detail)) {
            return detail;
        }
        String stopExactKey = exactVenueKeyForStop(stop);
        String candidateExactKey = exactVenueKey(normalizeAscii(String.join(" ",
                nullToEmpty(detail.getName()),
                nullToEmpty(detail.getAddress()),
                nullToEmpty(detail.getPlaceAddress())
        )));
        if (stopExactKey == null && candidateExactKey != null && reservedExactVenueKeys.contains(candidateExactKey)) {
            log.warn(
                    "Rejected reserved exact provider candidate for generic AI stop '{}' category={} providerName='{}' reservedExactKey={}",
                    stop.getPlaceName(),
                    category,
                    detail.getName(),
                    candidateExactKey
            );
            return PlaceDetail.builder()
                    .dataSource("NONE")
                    .imageSource("CATEGORY_FALLBACK")
                    .hasRealCoordinates(false)
                    .hasRealPhoto(false)
                    .enrichmentStatus("FAILED")
                    .rejectedReason("NAME_RESERVED_FOR_EXACT_STOP")
                    .build();
        }
        String key = providerCandidateKey(detail);
        if (key == null || key.isBlank()) {
            return detail;
        }
        if (usedProviderCandidateKeys.contains(key)) {
            log.warn(
                    "Rejected duplicate provider candidate for AI stop '{}' category={} providerKey={} providerName='{}'",
                    stop.getPlaceName(),
                    category,
                    key,
                    detail.getName()
            );
            PlaceDetail rejected = PlaceDetail.builder()
                    .dataSource("NONE")
                    .imageSource("CATEGORY_FALLBACK")
                    .hasRealCoordinates(false)
                    .hasRealPhoto(false)
                    .enrichmentStatus("FAILED")
                    .rejectedReason("DUPLICATE_PROVIDER_CANDIDATE")
                    .build();
            return rejected;
        }
        usedProviderCandidateKeys.add(key);
        return detail;
    }

    private boolean shouldRetryGenericEnrichment(PlaceDetail detail, AiGeneratedStop stop, String category) {
        if (!isMealOrCafeCategory(category)) {
            return false;
        }
        if (detail == null) {
            return true;
        }
        String reason = detail.getRejectedReason();
        if (reason != null && Set.of("NAME_RESERVED_FOR_EXACT_STOP", "DUPLICATE_PROVIDER_CANDIDATE").contains(reason)) {
            return true;
        }
        return !isValidCoordinate(detail.getLat(), detail.getLng());
    }

    private PlaceDetail resolveAlternateGenericEnrichment(
            AiGeneratedStop stop,
            String category,
            String destination,
            GeoPoint enrichmentAnchor,
            GeoFence destinationFence,
            Set<String> usedProviderCandidateKeys,
            Set<String> reservedExactVenueKeys) {
        for (String query : genericFallbackQueries(stop, category, destination)) {
            try {
                PlaceDetail detail = placeEnrichmentService.enrichForStop(
                        query,
                        destination,
                        category,
                        enrichmentAnchor.lat(),
                        enrichmentAnchor.lng(),
                        destinationFence.center().lat(),
                        destinationFence.center().lng(),
                        PlaceEnrichmentService.SerpApiBudget.of(2)
                );
                detail = rejectDuplicateProviderCandidate(detail, stop, category, usedProviderCandidateKeys, reservedExactVenueKeys);
                if (detail != null && detail.getRejectedReason() == null
                        && (isValidCoordinate(detail.getLat(), detail.getLng())
                        || detail.getImageUrl() != null
                        || detail.getRating() != null
                        || detail.getReviewCount() != null)) {
                    log.info("Generic fallback enrichment accepted for '{}' query='{}' provider='{}'",
                            stop.getPlaceName(), query, detail.getName());
                    return detail;
                }
            } catch (Exception e) {
                log.warn("Generic fallback enrichment failed for '{}' query='{}': {}", stop.getPlaceName(), query, e.getMessage());
            }
        }
        PlaceDetail rejected = PlaceDetail.builder()
                .dataSource("NONE")
                .imageSource("CATEGORY_FALLBACK")
                .hasRealCoordinates(false)
                .hasRealPhoto(false)
                .enrichmentStatus("FAILED")
                .rejectedReason("NO_UNUSED_GENERIC_CANDIDATE")
                .build();
        log.warn("No unused generic provider candidate for '{}' category={}", stop.getPlaceName(), category);
        return rejected;
    }

    private List<String> genericFallbackQueries(AiGeneratedStop stop, String category, String destination) {
        String text = normalizedStopText(stop);
        String dest = isVungTauDestination(destination) ? "Vung Tau" : nullToEmpty(destination);
        if ("CAFE".equals(normalizeStopCategory(category))) {
            return List.of(
                    "quan cafe view bien " + dest,
                    "quan cafe gan Bai Sau " + dest,
                    "quan cafe gan Bai Truoc " + dest
            );
        }
        if ("MARKET".equals(normalizeStopCategory(category))) {
            return List.of(
                    "cho dia phuong " + dest,
                    "cho am thuc " + dest,
                    "cho hai san " + dest
            );
        }
        if (text.contains("hai san") || text.contains("seafood")) {
            return List.of(
                    "nha hang hai san " + dest,
                    "quan hai san dia phuong " + dest,
                    "nha hang gan Bai Sau " + dest
            );
        }
        if (text.contains("an sang") || text.contains("breakfast")) {
            return List.of(
                    "quan an sang " + dest,
                    "quan an sang dia phuong " + dest,
                    "banh mi pho bun bo " + dest
            );
        }
        if (text.contains("bai truoc")) {
            return List.of(
                    "nha hang gan Bai Truoc " + dest,
                    "quan an trua gan Bai Truoc " + dest,
                    "quan an dia phuong " + dest
            );
        }
        return List.of(
                "quan an trua " + dest,
                "quan an dia phuong " + dest,
                "nha hang dia phuong " + dest
        );
    }

    private boolean tracksProviderReuse(PlaceDetail detail) {
        return "SERPAPI".equalsIgnoreCase(String.valueOf(detail.getDataSource()))
                || "SERPAPI".equalsIgnoreCase(String.valueOf(detail.getImageSource()));
    }

    private String providerCandidateKey(PlaceDetail detail) {
        String providerId = stringValue(detail.getProviderId());
        if (providerId != null) {
            return "id:" + normalizeAscii(providerId);
        }
        String name = stringValue(detail.getName());
        if (name == null) {
            return null;
        }
        if (isValidCoordinate(detail.getLat(), detail.getLng())) {
            return "geo:" + normalizeAscii(name)
                    + "|" + Math.round(detail.getLat() * 10000.0)
                    + "|" + Math.round(detail.getLng() * 10000.0);
        }
        return "name:" + normalizeAscii(name);
    }

    private boolean isUsableProviderCoordinateForStop(
            PlaceDetail detail,
            AiGeneratedStop stop,
            String category,
            String source) {
        String providerText = normalizeAscii(String.join(" ",
                nullToEmpty(detail.getName()),
                nullToEmpty(detail.getAddress()),
                nullToEmpty(detail.getPlaceAddress())
        ));
        if (providerText.contains("ban do") || providerText.contains("chu quyen") || providerText.contains("so do") || providerText.contains("bien dao")) {
            return false;
        }
        if (!"GOONG".equalsIgnoreCase(String.valueOf(source))) {
            return true;
        }
        String normalizedCategory = normalizeStopCategory(category);
        String stopText = normalizeAscii(String.join(" ",
                nullToEmpty(stop.getPlaceName()),
                nullToEmpty(stop.getSearchQuery()),
                nullToEmpty(stop.getDescription())
        ));

        String exactKey = exactVenueKey(stopText);
        if (exactKey != null && !exactVenueTitleMatches(exactKey, providerText)) {
            return false;
        }

        if ("FOOD".equals(normalizedCategory)) {
            return providerText.matches(".*\\b(nha hang|quan|banh|bun|pho|com|hai san|restaurant|food|eatery|bistro|dining)\\b.*");
        }
        if ("CAFE".equals(normalizedCategory)) {
            return providerText.matches(".*\\b(cafe|coffee|ca phe|espresso|roastery|tea|tra sua)\\b.*");
        }
        if (stopText.contains("bai sau") || stopText.contains("back beach")) {
            return providerText.contains("bai sau") || providerText.contains("back beach");
        }
        if (stopText.contains("bai truoc") || stopText.contains("front beach")) {
            return providerText.contains("bai truoc") || providerText.contains("front beach");
        }
        return true;
    }

    private Set<String> reservedExactVenueKeys(List<AiGeneratedStop> stops) {
        Set<String> keys = new HashSet<>();
        for (AiGeneratedStop stop : stops) {
            String key = exactVenueKeyForStop(stop);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String exactVenueKeyForStop(AiGeneratedStop stop) {
        if (stop == null) return null;
        return exactVenueKey(normalizeAscii(String.join(" ",
                nullToEmpty(stop.getPlaceName()),
                nullToEmpty(stop.getSearchQuery()),
                nullToEmpty(stop.getDescription())
        )));
    }

    private String exactVenueKey(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return null;
        if (normalizedText.contains("banh khot") && (normalizedText.contains("goc") || normalizedText.contains("vu sua"))) {
            return "BANH_KHOT_GOC_VU_SUA";
        }
        if (normalizedText.contains("son dang")) return "SON_DANG_COFFEE";
        if (normalizedText.contains("ganh hao")) return "GANH_HAO";
        if (normalizedText.contains("marina club")) return "MARINA_CLUB";
        if (normalizedText.contains("annata beach")) return "ANNATA_BEACH_HOTEL";
        if (normalizedText.contains("premier pearl")) return "PREMIER_PEARL";
        if (normalizedText.contains("imperial hotel") || normalizedText.contains("the imperial")) return "IMPERIAL_HOTEL";
        if (normalizedText.contains("malibu hotel")) return "MALIBU_HOTEL";
        return null;
    }

    private boolean exactVenueTitleMatches(String exactKey, String normalizedProviderText) {
        if (normalizedProviderText == null || normalizedProviderText.isBlank()) return false;
        return switch (exactKey) {
            case "BANH_KHOT_GOC_VU_SUA" -> normalizedProviderText.contains("banh khot")
                    && normalizedProviderText.contains("goc")
                    && normalizedProviderText.contains("vu sua");
            case "SON_DANG_COFFEE" -> normalizedProviderText.contains("son dang");
            case "GANH_HAO" -> normalizedProviderText.contains("ganh hao");
            case "MARINA_CLUB" -> normalizedProviderText.contains("marina club");
            case "ANNATA_BEACH_HOTEL" -> normalizedProviderText.contains("annata") && normalizedProviderText.contains("beach");
            case "PREMIER_PEARL" -> normalizedProviderText.contains("premier pearl");
            case "IMPERIAL_HOTEL" -> normalizedProviderText.contains("imperial");
            case "MALIBU_HOTEL" -> normalizedProviderText.contains("malibu");
            default -> true;
        };
    }

    private GeoPoint resolveSafeLocalFallback(
            LocalClusterState localCluster,
            GeoPoint previousLocalCoordinate,
            GeoFence destinationFence) {
        return previousLocalCoordinate != null
                ? previousLocalCoordinate
                : localCluster.anchor().orElse(destinationFence.center());
    }

    private GeoFence resolveDestinationFence(String destination, List<AiGeneratedStop> stops) {
        GeoPoint center = getKnownCityCoordinate(destination)
                .orElseGet(() -> inferDestinationCenterFromStops(stops)
                        .orElseGet(() -> {
                            double[] coords = getDefaultCityCoordinates(destination);
                            return new GeoPoint(coords[0], coords[1]);
                        }));
        double radiusKm = destinationRadiusKm(destination);
        log.info("AI trip destination center resolved for '{}': lat={} lng={} radiusKm={}",
                destination, center.lat(), center.lng(), radiusKm);
        return new GeoFence(center, radiusKm);
    }

    private Optional<GeoPoint> inferDestinationCenterFromStops(List<AiGeneratedStop> stops) {
        List<GeoPoint> localCoordinates = stops.stream()
                .filter(stop -> !isTransportStop(stop))
                .filter(stop -> isValidCoordinate(stop.getLat(), stop.getLng()))
                .map(stop -> new GeoPoint(stop.getLat(), stop.getLng()))
                .toList();
        if (localCoordinates.size() < 2) return Optional.empty();

        double lat = localCoordinates.stream().mapToDouble(GeoPoint::lat).average().orElse(Double.NaN);
        double lng = localCoordinates.stream().mapToDouble(GeoPoint::lng).average().orElse(Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) return Optional.empty();
        return Optional.of(new GeoPoint(lat, lng));
    }

    private PlaceDetail resolveWithGoong(String placeName, String destination) {
        String base = placeName == null ? "" : placeName.trim();
        String destinationValue = destination == null ? "" : destination.trim();
        String query = normalizeAscii(base).contains(normalizeAscii(destinationValue))
                ? base
                : (base + ", " + destinationValue).trim();
        try {
            List<AutocompleteSuggestion> suggestions = placeService.searchAutocomplete(query);
            if (suggestions.isEmpty()) {
                log.info("Goong autocomplete returned no results for '{}'", query);
                return null;
            }

            String placeId = suggestions.get(0).getPlaceId();
            PlaceDetail detail = placeService.getPlaceDetail(placeId);
            if (detail != null && isValidCoordinate(detail.getLat(), detail.getLng())) {
                log.info("Goong resolved '{}' -> lat={} lng={}", query, detail.getLat(), detail.getLng());
                return detail;
            }

            log.info("Goong detail returned invalid coords for '{}'", query);
            return null;
        } catch (Exception e) {
            log.warn("Goong coordinate resolution failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    private String cleanProviderQueryForStop(AiGeneratedStop stop, String destination) {
        String explicit = stringValue(stop.getSearchQuery());
        if (explicit != null) {
            return explicit;
        }
        String name = stringValue(stop.getPlaceName());
        String category = normalizeStopCategory(stop.getCategory());
        String destinationValue = destination == null ? "" : destination.trim();
        boolean generic = isGenericLocalStop(stop);
        return switch (category) {
            case "FOOD" -> generic
                    ? "nhà hàng địa phương nổi tiếng " + destinationValue
                    : name + " " + destinationValue;
            case "CAFE" -> generic
                    ? "quán cafe đẹp " + destinationValue
                    : "quán cafe " + name + " " + destinationValue;
            case "MARKET" -> generic
                    ? "chợ đêm ẩm thực " + destinationValue
                    : name + " " + destinationValue;
            case "HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING" -> generic
                    ? "khách sạn homestay trung tâm " + destinationValue
                    : name + " " + destinationValue;
            case "SIGHTSEEING" -> generic
                    ? "địa điểm tham quan nổi tiếng " + destinationValue
                    : name + " " + destinationValue;
            default -> (name == null || name.isBlank() ? destinationValue : name + " " + destinationValue).trim();
        };
    }

    private String providerQueryForStop(AiGeneratedStop stop, String destination) {
        String explicit = stringValue(stop.getSearchQuery());
        if (explicit != null) {
            return explicit;
        }
        String name = stringValue(stop.getPlaceName());
        String category = normalizeStopCategory(stop.getCategory());
        String destinationValue = destination == null ? "" : destination.trim();
        boolean generic = isGenericLocalStop(stop);
        return switch (category) {
            case "FOOD" -> generic
                    ? "nhà hàng địa phương nổi tiếng " + destinationValue
                    : name + " " + destinationValue;
            case "CAFE" -> generic
                    ? "quán cafe đẹp " + destinationValue
                    : "quán cafe " + name + " " + destinationValue;
            case "MARKET" -> generic
                    ? "chợ đêm ẩm thực " + destinationValue
                    : name + " " + destinationValue;
            case "HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING" -> generic
                    ? "khách sạn homestay trung tâm " + destinationValue
                    : name + " " + destinationValue;
            case "SIGHTSEEING" -> generic
                    ? "địa điểm tham quan nổi tiếng " + destinationValue
                    : name + " " + destinationValue;
            default -> (name == null || name.isBlank() ? destinationValue : name + " " + destinationValue).trim();
        };
    }

    private Instant calculateArrivalAt(
            AiGeneratedStop stop,
            LocalDate tripStartDate,
            Instant previousArrivalAt,
            Integer previousDurationMinutes) {
        int day = Math.max(1, stop.getDay());
        LocalDate stopDate = tripStartDate.plusDays(day - 1L);
        Optional<LocalTime> explicitStart = parseStartTime(stop.getStartTime());

        if (explicitStart.isPresent()) {
            return stopDate.atTime(explicitStart.get()).atZone(TRIP_TIME_ZONE).toInstant();
        }

        if (previousArrivalAt != null) {
            int duration = previousDurationMinutes != null ? previousDurationMinutes : DEFAULT_STOP_DURATION_MINUTES;
            log.warn(
                    "Missing startTime for '{}' on day {}; chaining from previous stop + {} minutes",
                    stop.getPlaceName(),
                    day,
                    duration
            );
            return previousArrivalAt.plusSeconds(duration * 60L);
        }

        LocalTime fallback = fallbackStartTime(stop.getTimeOfDay());
        log.warn(
                "Missing startTime for first stop '{}' on day {}; using last-resort {} fallback {}",
                stop.getPlaceName(),
                day,
                stop.getTimeOfDay(),
                fallback
        );
        return stopDate.atTime(fallback).atZone(TRIP_TIME_ZONE).toInstant();
    }

    private int resolveDurationMinutes(AiGeneratedStop stop) {
        Integer duration = stop.getDurationMinutes();
        if (duration != null && duration > 0) {
            return duration;
        }
        log.warn(
                "Missing durationMinutes for '{}'; using default {} minutes",
                stop.getPlaceName(),
                DEFAULT_STOP_DURATION_MINUTES
        );
        return DEFAULT_STOP_DURATION_MINUTES;
    }

    private Optional<LocalTime> parseStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(startTime.trim(), START_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            log.warn("Invalid AI startTime '{}'; expected HH:mm", startTime);
            return Optional.empty();
        }
    }

    private boolean isValidStartTime(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            LocalTime.parse(value.trim(), START_TIME_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private LocalTime fallbackStartTime(String timeOfDay) {
        return switch (timeOfDay == null ? "" : timeOfDay.trim().toLowerCase(Locale.ROOT)) {
            case "morning" -> LocalTime.of(8, 0);
            case "afternoon" -> LocalTime.of(13, 0);
            case "evening", "night" -> LocalTime.of(18, 0);
            default -> LocalTime.of(9, 0);
        };
    }

    private double orderIdxForArrival(int day, Instant arrivalAt) {
        LocalTime localTime = arrivalAt.atZone(TRIP_TIME_ZONE).toLocalTime();
        int minutesFromMidnight = localTime.getHour() * 60 + localTime.getMinute();
        return Math.max(1, day) * 100000.0 + minutesFromMidnight;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value from AI itinerary '{}'", text);
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private BigDecimal parseEstimatedCost(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        String text = stringValue(value);
        if (text == null) {
            return null;
        }

        String numeric = text.replaceAll("[^0-9.,-]", "").replace(",", "");
        if (numeric.isBlank() || "-".equals(numeric)) {
            log.warn("Invalid estimatedCost value from AI itinerary '{}'", text);
            return null;
        }

        try {
            return new BigDecimal(numeric);
        } catch (NumberFormatException e) {
            log.warn("Invalid estimatedCost value from AI itinerary '{}'", text);
            return null;
        }
    }

    private Double parseDoubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            log.warn("Invalid decimal value from AI itinerary '{}'", text);
            return null;
        }
    }

    private String normalizeStopCategory(String value) {
        if (value == null || value.isBlank()) {
            return "SIGHTSEEING";
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "TRAVEL", "TRANSFER", "TRANSIT", "TRANSPORT" -> "TRANSPORT";
            case "FLIGHT", "PLANE", "AIRPORT" -> "FLIGHT";
            case "BUS", "LIMOUSINE", "COACH" -> "BUS";
            case "TRAIN", "RAIL" -> "TRAIN";
            case "TAXI", "GRAB", "CAR" -> "TAXI";
            case "MOTORBIKE", "MOTORCYCLE", "BIKE", "SCOOTER" -> "MOTORBIKE";
            case "HOTEL", "STAY" -> "HOTEL";
            case "HOMESTAY" -> "HOMESTAY";
            case "ACCOMMODATION", "LODGING" -> "LODGING";
            case "CHECKIN", "CHECK_IN", "DROP_LUGGAGE", "BAG_DROP" -> "CHECKIN";
            case "CHECKOUT", "CHECK_OUT" -> "CHECKOUT";
            case "REST", "REST_STOP" -> "REST";
            case "NOTE" -> "NOTE";
            case "FOOD", "RESTAURANT", "DINING", "LUNCH", "DINNER", "BREAKFAST" -> "FOOD";
            case "CAFE", "COFFEE" -> "CAFE";
            case "MARKET", "SHOPPING" -> "MARKET";
            case "EMERGENCY" -> "EMERGENCY";
            case "SIGHT", "SIGHTSEEING", "ATTRACTION", "PLACE", "VISIT" -> "SIGHTSEEING";
            default -> "OTHER";
        };
    }

    private boolean isTransportStop(AiGeneratedStop stop) {
        String category = normalizeStopCategory(stop.getCategory());
        if (isTransportCategory(category)) {
            return true;
        }
        String name = stop.getPlaceName() == null ? "" : stop.getPlaceName();
        return name.matches("(?i).*(travel to|travel from|depart|departure|return to|back to|transfer|transit|airport|bus station|train station).*");
    }

    private boolean isTransportCategory(String category) {
        String normalized = normalizeStopCategory(category);
        return Set.of("TRANSPORT", "FLIGHT", "BUS", "TRAIN", "TAXI", "MOTORBIKE").contains(normalized);
    }

    private boolean isSystemStop(AiGeneratedStop stop) {
        String category = normalizeStopCategory(stop.getCategory());
        return isTransportCategory(category)
                || Set.of("CHECKIN", "CHECKOUT", "REST", "NOTE", "EMERGENCY").contains(category);
    }

    private boolean isAccommodationCheckinStop(AiGeneratedStop stop, String category) {
        String normalizedCategory = category == null ? normalizeStopCategory(stop.getCategory()) : category;
        String placeName = stringValue(stop.getPlaceName());
        String searchQuery = stringValue(stop.getSearchQuery());
        String text = normalizeAscii(String.join(" ",
                placeName == null ? "" : placeName,
                searchQuery == null ? "" : searchQuery
        ));
        boolean accommodationCategory = Set.of("HOTEL", "HOMESTAY", "ACCOMMODATION", "LODGING", "CHECKIN").contains(normalizedCategory);
        boolean checkinText = text.matches(".*\\b(nhan phong|check in|khach san|hotel|homestay|resort|bai sau|back beach)\\b.*");
        boolean pureNonPlace = text.matches(".*\\b(tra phong|checkout|di chuyen den khach san|nghi ngoi tai khach san)\\b.*");
        return accommodationCategory && checkinText && !pureNonPlace;
    }

    private boolean isMarketStyleStop(AiGeneratedStop stop) {
        String text = normalizeAscii(String.join(" ",
                nullToEmpty(stop.getPlaceName()),
                nullToEmpty(stop.getSearchQuery()),
                nullToEmpty(stop.getDescription())
        ));
        return text.matches(".*\\b(cho|market|xom luoi|night market|fish market)\\b.*");
    }

    private String toMarketStopName(String originalName) {
        String name = stringValue(originalName);
        if (name == null) {
            return "Khám phá chợ địa phương";
        }
        String normalized = normalizeAscii(name);
        if (normalized.contains("cho xom luoi")) {
            return "Khám phá Chợ Xóm Lưới";
        }
        if (normalized.contains("cho dem")) {
            return "Khám phá chợ đêm";
        }
        if (normalized.startsWith("kham pha")) {
            return name;
        }
        return "Khám phá " + name
                .replaceFirst("(?i)^ăn\\s+(trưa|tối|sáng)\\s+(tại|ở|gần)\\s+", "")
                .trim();
    }

    private boolean hasRealHotelCandidate(PlaceDetail detail) {
        return detail != null
                && detail.getName() != null
                && !detail.getName().isBlank()
                && "SERPAPI".equalsIgnoreCase(String.valueOf(detail.getImageSource()))
                && detail.getImageUrl() != null
                && !detail.getImageUrl().isBlank();
    }

    private boolean hasRealHotelPlaceCandidate(PlaceDetail detail) {
        return hotelDisplayName(detail) != null;
    }

    private String hotelDisplayName(PlaceDetail detail) {
        if (detail == null) return null;
        for (String value : List.of(detail.getName(), firstAddressSegment(detail.getPlaceAddress()), firstAddressSegment(detail.getAddress()))) {
            String text = stringValue(value);
            if (isSpecificHotelName(text)) {
                return text;
            }
        }
        return null;
    }

    private String firstAddressSegment(String address) {
        String text = stringValue(address);
        if (text == null) return null;
        String[] parts = text.split(",");
        return parts.length == 0 ? text : parts[0].trim();
    }

    private boolean isSpecificHotelName(String value) {
        if (value == null) return false;
        String text = normalizeAscii(value);
        if (text.isBlank()) return false;
        if (text.contains("ban do") || text.contains("chu quyen") || text.contains("bien dao") || text.contains("so do")) {
            return false;
        }
        if (!text.matches(".*\\b(hotel|resort|homestay|villa|khach san|hostel|inn|motel|apartment|apartments)\\b.*")) {
            return false;
        }
        return !text.matches(".*\\b(nhan phong|goi y|da chon|khu bai sau|gan trung tam|khach san vung tau|hotel vung tau|khach san bai sau|hotel back beach)\\b.*");
    }

    private boolean isRealPlaceStop(AiGeneratedStop stop) {
        String category = normalizeStopCategory(stop.getCategory());
        return Set.of("PLACE", "FOOD", "CAFE", "MARKET", "SIGHTSEEING", "HOTEL", "HOMESTAY", "LODGING").contains(category)
                || (!isSystemStop(stop) && !"OTHER".equals(category));
    }

    private String cleanNaturalText(String text) {
        return AiItineraryParser.cleanNaturalNotes(text);
    }

    private boolean isVietnameseLanguage(String language) {
        if (language == null || language.isBlank()) {
            return false;
        }
        String value = language.trim().toLowerCase(Locale.ROOT);
        return value.equals("vi") || value.contains("vietnamese") || value.contains("tiếng việt") || value.contains("tieng viet");
    }

    private boolean looksEnglish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : List.of(
                "start the", "begin the", "journey", "check in", "your hotel", "enjoy",
                "fresh seafood", "explore", "visit", "discover", "take a", "relax",
                "head to", "return to", "travel to", "arrive", "depart", "local culture",
                "traditional", "scenic", "beach", "lunch", "dinner", "breakfast",
                "the ", "and ", "with ", "near the "
        )) {
            if (lower.contains(token)) {
                hits++;
            }
        }
        long asciiLetters = lower.chars()
                .filter(ch -> (ch >= 'a' && ch <= 'z'))
                .count();
        long vietnameseMarks = lower.chars()
                .filter(ch -> "ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ".indexOf(ch) >= 0)
                .count();
        return hits >= 2 || (hits >= 1 && asciiLetters > 35 && vietnameseMarks == 0);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String buildTripTitle(GenerateTripRequest req) {
        if (isVietnameseLanguage(req.getLanguage())) {
            return req.getDays() + " ngày khám phá " + req.getDestination();
        }
        return req.getDays() + " Days in " + req.getDestination();
    }

    private void logBudgetConsistency(Map<String, Object> plan, List<AiGeneratedStop> stops) {
        BigDecimal planBudget = parseEstimatedCost(plan.get("budget"));
        if (planBudget == null || planBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal stopTotal = stops.stream()
                .map(AiGeneratedStop::getEstimatedCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stopTotal.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("AI plan budget '{}' exists but stop estimatedCost total is missing/zero", plan.get("budget"));
            return;
        }

        BigDecimal difference = stopTotal.subtract(planBudget).abs();
        BigDecimal tolerance = planBudget.multiply(BigDecimal.valueOf(0.15));
        if (difference.compareTo(tolerance) > 0) {
            log.warn(
                    "AI plan budget mismatch: plan budget={} stop estimatedCost sum={} diff={} tolerance={}",
                    planBudget,
                    stopTotal,
                    difference,
                    tolerance
            );
        }
    }

    private void logRouteSanityWarnings(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        Map<Integer, List<AiGeneratedStop>> byDay = new TreeMap<>();
        for (AiGeneratedStop stop : stops) {
            byDay.computeIfAbsent(Math.max(1, stop.getDay()), ignored -> new ArrayList<>()).add(stop);
        }

        double localThresholdKm = isMountainOrRuralTrip(req) ? 60.0 : 25.0;
        for (Map.Entry<Integer, List<AiGeneratedStop>> entry : byDay.entrySet()) {
            List<AiGeneratedStop> dayStops = entry.getValue();
            for (int i = 1; i < dayStops.size(); i++) {
                AiGeneratedStop prev = dayStops.get(i - 1);
                AiGeneratedStop curr = dayStops.get(i);
                if (!isValidCoordinate(prev.getLat(), prev.getLng()) || !isValidCoordinate(curr.getLat(), curr.getLng())) {
                    continue;
                }
                double km = haversineKm(prev.getLat(), prev.getLng(), curr.getLat(), curr.getLng());
                boolean transportSegment = isTransportStop(prev) || isTransportStop(curr);
                if (!transportSegment && (km > localThresholdKm || km > 100.0)) {
                    log.warn(
                            "AI route sanity warning day {}: '{}' -> '{}' is {} km; prevCategory={} currCategory={} transportSegment={}",
                            entry.getKey(),
                            prev.getPlaceName(),
                            curr.getPlaceName(),
                            String.format(Locale.ROOT, "%.1f", km),
                            normalizeStopCategory(prev.getCategory()),
                            normalizeStopCategory(curr.getCategory()),
                            transportSegment
                    );
                }
            }
        }
    }

    private void logIntercityDurationSanityWarnings(List<AiGeneratedStop> stops) {
        Map<Integer, List<AiGeneratedStop>> byDay = new TreeMap<>();
        for (AiGeneratedStop stop : stops) {
            byDay.computeIfAbsent(Math.max(1, stop.getDay()), ignored -> new ArrayList<>()).add(stop);
        }

        for (Map.Entry<Integer, List<AiGeneratedStop>> entry : byDay.entrySet()) {
            List<AiGeneratedStop> dayStops = entry.getValue();
            for (int i = 1; i < dayStops.size(); i++) {
                AiGeneratedStop prev = dayStops.get(i - 1);
                AiGeneratedStop curr = dayStops.get(i);
                boolean transportSegment = isTransportStop(prev) || isTransportStop(curr);
                Optional<Double> distanceKm = segmentDistanceKm(prev, curr);
                Integer durationMinutes = segmentDurationMinutes(curr);

                if (!transportSegment || distanceKm.isEmpty() || durationMinutes == null || durationMinutes <= 0) {
                    continue;
                }

                double km = distanceKm.get();
                if ((km > 150.0 && durationMinutes < 150) || (km > 80.0 && durationMinutes < 90)) {
                    log.warn(
                            "AI intercity duration warning day {}: '{}' -> '{}' distance={} km duration={} min seems too short",
                            entry.getKey(),
                            prev.getPlaceName(),
                            curr.getPlaceName(),
                            String.format(Locale.ROOT, "%.1f", km),
                            durationMinutes
                    );
                }
            }

            for (AiGeneratedStop stop : dayStops) {
                if (!isTransportStop(stop)) {
                    continue;
                }
                Integer duration = stop.getDurationMinutes();
                if (duration == null || duration <= 0) {
                    continue;
                }
                Optional<Double> statedDistance = travelMapDistanceKm(stop);
                if (statedDistance.isPresent()
                        && ((statedDistance.get() > 150.0 && duration < 150)
                        || (statedDistance.get() > 80.0 && duration < 90))) {
                    log.warn(
                            "AI intercity duration warning: transport stop '{}' states distance={} km but duration={} min",
                            stop.getPlaceName(),
                            String.format(Locale.ROOT, "%.1f", statedDistance.get()),
                            duration
                    );
                }
            }
        }
    }

    private Optional<Double> segmentDistanceKm(AiGeneratedStop prev, AiGeneratedStop curr) {
        Optional<Double> statedDistance = travelMapDistanceKm(curr);
        if (statedDistance.isPresent()) {
            return statedDistance;
        }
        if (isValidCoordinate(prev.getLat(), prev.getLng()) && isValidCoordinate(curr.getLat(), curr.getLng())) {
            return Optional.of(haversineKm(prev.getLat(), prev.getLng(), curr.getLat(), curr.getLng()));
        }
        return Optional.empty();
    }

    private Optional<Double> travelMapDistanceKm(AiGeneratedStop stop) {
        Map<String, Object> travel = stop.getTravelFromPrevious();
        if (travel == null) {
            return Optional.empty();
        }
        Double distance = parseDoubleValue(travel.get("distanceKm"));
        return distance != null && distance > 0 ? Optional.of(distance) : Optional.empty();
    }

    private Integer segmentDurationMinutes(AiGeneratedStop curr) {
        Map<String, Object> travel = curr.getTravelFromPrevious();
        Integer travelDuration = travel == null ? null : parseInteger(travel.get("durationMinutes"));
        if (travelDuration != null && travelDuration > 0) {
            return travelDuration;
        }
        return isTransportStop(curr) ? curr.getDurationMinutes() : null;
    }

    private boolean isMountainOrRuralTrip(GenerateTripRequest req) {
        String text = ((req.getDestination() == null ? "" : req.getDestination()) + " " + String.join(" ", req.getInterests() == null ? List.of() : req.getInterests()))
                .toLowerCase(Locale.ROOT);
        return text.contains("ha giang")
                || text.contains("hà giang")
                || text.contains("sapa")
                || text.contains("sa pa")
                || text.contains("mountain")
                || text.contains("rural")
                || text.contains("cao bằng")
                || text.contains("cao bang")
                || text.contains("mộc châu")
                || text.contains("moc chau");
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Map<String, Object> buildJobInput(GenerateTripRequest req) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("origin", req.getOrigin());
        input.put("destination", req.getDestination());
        input.put("days", req.getDays());
        input.put("interests", req.getInterests() != null ? req.getInterests() : List.of());
        input.put("budget", req.getBudget());
        input.put("language", req.getLanguage());
        input.put("departureTimePreference", normalizeTimePreference(req.getDepartureTimePreference(), "MORNING"));
        input.put("departureTime", req.getDepartureTime());
        input.put("returnTimePreference", normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON"));
        input.put("returnTime", req.getReturnTime());
        input.put("needAccommodation", resolveNeedAccommodation(req));
        input.put("accommodationPreference", normalizeAccommodationPreference(req.getAccommodationPreference()));
        input.put("transportPreference", normalizeTransportPreference(req.getTransportPreference()));
        return input;
    }

    private void logPreferenceSanityWarnings(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        if (stops.isEmpty()) {
            return;
        }

        stops.stream()
                .filter(stop -> Math.max(1, stop.getDay()) == 1)
                .findFirst()
                .ifPresent(stop -> parseStartTime(stop.getStartTime()).ifPresent(start -> {
                    LocalTime latestExpected = preferenceEndTime(
                            normalizeTimePreference(req.getDepartureTimePreference(), "MORNING"),
                            req.getDepartureTime()
                    ).plusMinutes(90);
                    if (start.isAfter(latestExpected)) {
                        log.warn(
                                "AI preference warning: day 1 starts at {} for '{}', later than departure preference {} ({})",
                                start,
                                stop.getPlaceName(),
                                normalizeTimePreference(req.getDepartureTimePreference(), "MORNING"),
                                req.getDepartureTime()
                        );
                    }
                }));

        if (req.getDays() >= 2 && resolveNeedAccommodation(req)) {
            boolean hasAccommodation = stops.stream().anyMatch(this::isAccommodationStop);
            if (!hasAccommodation) {
                log.warn(
                        "AI preference warning: {}-day trip requires accommodation preference {}, but no HOTEL/HOMESTAY/check-in stop was parsed",
                        req.getDays(),
                        normalizeAccommodationPreference(req.getAccommodationPreference())
                );
            }

            boolean hasDayOneAccommodation = stops.stream()
                    .filter(stop -> Math.max(1, stop.getDay()) == 1)
                    .anyMatch(this::isCheckInOrAccommodationStop);
            if (!hasDayOneAccommodation) {
                log.warn(
                        "AI accommodation warning: day 1 of a {}-day trip has no destination accommodation/check-in/drop-luggage stop",
                        req.getDays()
                );
            }

            int finalDay = Math.max(1, req.getDays());
            boolean hasCheckout = stops.stream()
                    .filter(stop -> Math.max(1, stop.getDay()) == finalDay)
                    .anyMatch(this::isCheckoutStop);
            if (!hasCheckout) {
                log.warn("AI accommodation warning: final day {} has no checkout/trả phòng stop", finalDay);
            }
        }

        int finalDay = Math.max(1, req.getDays());
        boolean hasReturnLeg = stops.stream()
                .filter(stop -> Math.max(1, stop.getDay()) == finalDay)
                .anyMatch(stop -> isTransportStop(stop) && looksLikeReturnLeg(stop, req));
        if (!hasReturnLeg) {
            log.warn(
                    "AI preference warning: final day {} has no clear return transport/access leg to origin '{}'",
                    finalDay,
                    req.getOrigin()
            );
        }
    }

    private void logMealSanityWarnings(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        if (stops.isEmpty()) {
            return;
        }

        Map<Integer, List<AiGeneratedStop>> stopsByDay = new TreeMap<>();
        for (AiGeneratedStop stop : stops) {
            stopsByDay.computeIfAbsent(Math.max(1, stop.getDay()), ignored -> new ArrayList<>()).add(stop);
        }

        for (Map.Entry<Integer, List<AiGeneratedStop>> entry : stopsByDay.entrySet()) {
            List<AiGeneratedStop> dayStops = entry.getValue();
            if (isFullDayItinerary(dayStops) && dayStops.stream().noneMatch(this::isMealOrCafeStop)) {
                log.warn(
                        "AI meal sanity warning: day {} looks like a full-day itinerary but has no FOOD/CAFE/MARKET stop",
                        entry.getKey()
                );
            }
        }

        if (Math.max(1, req.getDays()) >= 2) {
            List<AiGeneratedStop> dayOneStops = stopsByDay.getOrDefault(1, List.of());
            boolean hasDayOneDinner = dayOneStops.stream().anyMatch(this::isDinnerMealStop);
            if (!hasDayOneDinner) {
                log.warn("AI meal sanity warning: day 1 of a multi-day trip has no dinner/food-market/cafe stop");
            }
            if (dayOneNeedsEveningRhythm(dayOneStops)) {
                log.warn("AI day rhythm warning: day 1 ends before 20:30 without dinner/cafe/market/light evening activity");
            }
        }

        int finalDay = Math.max(1, req.getDays());
        if (returnPreferenceNeedsFinalMeal(req)) {
            List<AiGeneratedStop> finalDayStops = stopsByDay.getOrDefault(finalDay, List.of());
            LocalTime cutoff = finalMealCutoff(finalDayStops, req);
            boolean hasMealBeforeReturn = finalDayStops.stream()
                    .filter(this::isMealOrCafeStop)
                    .anyMatch(stop -> parseStartTime(stop.getStartTime())
                            .map(start -> !start.isAfter(cutoff))
                            .orElse(false));
            if (!hasMealBeforeReturn) {
                log.warn(
                        "AI meal sanity warning: final day {} has no FOOD/CAFE/MARKET stop before return cutoff {}",
                        finalDay,
                        cutoff
                );
            }
        }
    }

    private boolean dayOneNeedsEveningRhythm(List<AiGeneratedStop> dayOneStops) {
        if (dayOneStops.isEmpty() || isDayTooFull(dayOneStops) || !hasDestinationLocalStop(dayOneStops)) {
            return false;
        }
        if (dayOneStops.stream().anyMatch(this::isEveningRhythmStop)) {
            return false;
        }
        Optional<LocalTime> latestEnd = dayOneStops.stream()
                .map(this::stopEndTime)
                .flatMap(Optional::stream)
                .max(LocalTime::compareTo);
        return latestEnd.isPresent() && latestEnd.get().isBefore(LocalTime.of(20, 30));
    }

    private boolean hasDestinationLocalStop(List<AiGeneratedStop> dayStops) {
        return dayStops.stream().anyMatch(stop -> !isTransportStop(stop) && isValidCoordinate(stop.getLat(), stop.getLng()));
    }

    private boolean isDayTooFull(List<AiGeneratedStop> dayStops) {
        if (dayStops.size() >= 7) {
            return true;
        }
        Optional<LocalTime> firstStart = dayStops.stream()
                .map(AiGeneratedStop::getStartTime)
                .map(this::parseStartTime)
                .flatMap(Optional::stream)
                .min(LocalTime::compareTo);
        Optional<LocalTime> latestEnd = dayStops.stream()
                .map(this::stopEndTime)
                .flatMap(Optional::stream)
                .max(LocalTime::compareTo);
        return firstStart.isPresent()
                && latestEnd.isPresent()
                && Duration.between(firstStart.get(), latestEnd.get()).toMinutes() >= 720;
    }

    private boolean isEveningRhythmStop(AiGeneratedStop stop) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        boolean eveningText = text.contains("ăn tối")
                || text.contains("an toi")
                || text.contains("bữa tối")
                || text.contains("bua toi")
                || text.contains("dinner")
                || text.contains("cà phê")
                || text.contains("ca phe")
                || text.contains("cafe")
                || text.contains("chợ đêm")
                || text.contains("cho dem")
                || text.contains("hải sản")
                || text.contains("hai san")
                || text.contains("dạo biển")
                || text.contains("dao bien")
                || text.contains("bãi trước")
                || text.contains("bai truoc")
                || text.contains("night")
                || text.contains("evening");
        if (eveningText) {
            return true;
        }
        Optional<LocalTime> start = parseStartTime(stop.getStartTime());
        return start.isPresent()
                && !start.get().isBefore(LocalTime.of(17, 0))
                && (isMealOrCafeStop(stop) || "SIGHTSEEING".equals(normalizeStopCategory(stop.getCategory())));
    }

    private void logVietnameseNoteWarnings(List<AiGeneratedStop> stops, GenerateTripRequest req) {
        if (!isVietnameseLanguage(req.getLanguage())) {
            return;
        }
        for (AiGeneratedStop stop : stops) {
            String note = cleanNaturalText(stop.getDescription());
            if (looksEnglish(note)) {
                log.warn(
                        "AI Vietnamese note warning: stop '{}' returned English-looking notes: {}",
                        stop.getPlaceName(),
                        abbreviate(note, 140)
                );
            }
        }
    }

    private boolean isFullDayItinerary(List<AiGeneratedStop> dayStops) {
        if (dayStops.size() >= 4) {
            return true;
        }

        Optional<LocalTime> firstStart = dayStops.stream()
                .map(AiGeneratedStop::getStartTime)
                .map(this::parseStartTime)
                .flatMap(Optional::stream)
                .min(LocalTime::compareTo);
        Optional<LocalTime> lastEnd = dayStops.stream()
                .map(this::stopEndTime)
                .flatMap(Optional::stream)
                .max(LocalTime::compareTo);

        if (firstStart.isPresent() && lastEnd.isPresent()) {
            long spanMinutes = Duration.between(firstStart.get(), lastEnd.get()).toMinutes();
            return (!firstStart.get().isAfter(LocalTime.of(10, 0))
                    && !lastEnd.get().isBefore(LocalTime.of(14, 0)))
                    || spanMinutes >= 300;
        }

        return dayStops.size() >= 3;
    }

    private Optional<LocalTime> stopEndTime(AiGeneratedStop stop) {
        return parseStartTime(stop.getStartTime())
                .map(start -> start.plusMinutes(resolveDurationForSanity(stop)));
    }

    private int resolveDurationForSanity(AiGeneratedStop stop) {
        Integer duration = stop.getDurationMinutes();
        return duration != null && duration > 0 ? duration : DEFAULT_STOP_DURATION_MINUTES;
    }

    private boolean isMealOrCafeStop(AiGeneratedStop stop) {
        String category = normalizeStopCategory(stop.getCategory());
        if (Set.of("FOOD", "CAFE", "MARKET").contains(category)) {
            return true;
        }

        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        return text.contains("breakfast")
                || text.contains("lunch")
                || text.contains("dinner")
                || text.contains("food")
                || text.contains("restaurant")
                || text.contains("cafe")
                || text.contains("coffee")
                || text.contains("seafood")
                || text.contains("an sang")
                || text.contains("an trua")
                || text.contains("an toi")
                || text.contains("bua sang")
                || text.contains("bua trua")
                || text.contains("bua toi")
                || text.contains("ăn sáng")
                || text.contains("ăn trưa")
                || text.contains("ăn tối")
                || text.contains("bữa sáng")
                || text.contains("bữa trưa")
                || text.contains("bữa tối")
                || text.contains("cà phê")
                || text.contains("ca phe")
                || text.contains("nhà hàng")
                || text.contains("nha hang")
                || text.contains("hải sản")
                || text.contains("hai san")
                || text.contains("bánh khọt")
                || text.contains("banh khot")
                || text.contains("chợ đêm")
                || text.contains("cho dem");
    }

    private boolean isBreakfastStop(AiGeneratedStop stop) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        Optional<LocalTime> start = parseStartTime(stop.getStartTime());
        return "BREAKFAST".equalsIgnoreCase(String.valueOf(stop.getMealType()))
                || text.contains("breakfast")
                || text.contains("an sang")
                || text.contains("bua sang")
                || text.contains("ăn sáng")
                || text.contains("bữa sáng")
                || (isMealOrCafeStop(stop) && start.isPresent()
                && !start.get().isBefore(LocalTime.of(6, 30))
                && start.get().isBefore(LocalTime.of(10, 0)));
    }

    private boolean isLunchStop(AiGeneratedStop stop) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        Optional<LocalTime> start = parseStartTime(stop.getStartTime());
        return "LUNCH".equalsIgnoreCase(String.valueOf(stop.getMealType()))
                || text.contains("lunch")
                || text.contains("an trua")
                || text.contains("bua trua")
                || text.contains("ăn trưa")
                || text.contains("bữa trưa")
                || (isMealOrCafeStop(stop) && start.isPresent()
                && !start.get().isBefore(LocalTime.of(10, 45))
                && start.get().isBefore(LocalTime.of(14, 0)));
    }

    private boolean isChillText(AiGeneratedStop stop) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        return text.contains("cafe")
                || text.contains("coffee")
                || text.contains("ca phe")
                || text.contains("cà phê")
                || text.contains("nghỉ chân")
                || text.contains("nghi chan")
                || text.contains("dạo biển")
                || text.contains("dao bien")
                || text.contains("hoàng hôn")
                || text.contains("hoang hon")
                || text.contains("bình minh")
                || text.contains("binh minh")
                || text.contains("chợ đêm")
                || text.contains("cho dem");
    }

    private boolean isDinnerMealStop(AiGeneratedStop stop) {
        if (!isMealOrCafeStop(stop)) {
            return false;
        }

        Optional<LocalTime> startTime = parseStartTime(stop.getStartTime());
        if (startTime.isPresent()
                && !startTime.get().isBefore(LocalTime.of(17, 0))
                && !startTime.get().isAfter(LocalTime.of(20, 30))) {
            return true;
        }

        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        return text.contains("dinner")
                || text.contains("night market")
                || text.contains("evening food")
                || text.contains("ăn tối")
                || text.contains("an toi")
                || text.contains("bữa tối")
                || text.contains("bua toi")
                || text.contains("chợ đêm")
                || text.contains("cho dem")
                || text.contains("hải sản")
                || text.contains("hai san");
    }

    private boolean returnPreferenceNeedsFinalMeal(GenerateTripRequest req) {
        String preference = normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON");
        if ("AFTERNOON".equals(preference) || "EVENING".equals(preference)) {
            return true;
        }
        if ("CUSTOM".equals(preference) && isValidStartTime(req.getReturnTime())) {
            LocalTime returnTime = LocalTime.parse(req.getReturnTime().trim(), START_TIME_FORMATTER);
            return !returnTime.isBefore(LocalTime.NOON);
        }
        return false;
    }

    private LocalTime finalMealCutoff(List<AiGeneratedStop> finalDayStops, GenerateTripRequest req) {
        return finalDayStops.stream()
                .filter(stop -> isTransportStop(stop) && looksLikeReturnLeg(stop, req))
                .map(AiGeneratedStop::getStartTime)
                .map(this::parseStartTime)
                .flatMap(Optional::stream)
                .min(LocalTime::compareTo)
                .orElseGet(() -> preferenceEndTime(
                        normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON"),
                        req.getReturnTime()
                ));
    }

    private boolean isAccommodationStop(AiGeneratedStop stop) {
        String category = normalizeStopCategory(stop.getCategory());
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        return Set.of("HOTEL", "HOMESTAY", "LODGING", "CHECKIN").contains(category)
                || text.contains("homestay")
                || text.contains("lodging")
                || text.contains("accommodation")
                || text.contains("khách sạn")
                || text.contains("khach san")
                || text.contains("lưu trú")
                || text.contains("luu tru")
                || text.contains("check-in")
                || text.contains("checkin")
                || text.contains("check in")
                || text.contains("nhận phòng")
                || text.contains("nhan phong")
                || text.contains("trả phòng")
                || text.contains("tra phong")
                || text.contains("gửi hành lý")
                || text.contains("gui hanh ly");
    }

    private boolean isCheckInOrAccommodationStop(AiGeneratedStop stop) {
        if (!isAccommodationStop(stop)) {
            return false;
        }
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        return !isCheckoutStop(stop)
                || text.contains("check-in")
                || text.contains("checkin")
                || text.contains("check in")
                || text.contains("nhận phòng")
                || text.contains("nhan phong")
                || text.contains("gửi hành lý")
                || text.contains("gui hanh ly")
                || text.contains("drop luggage");
    }

    private boolean isCheckoutStop(AiGeneratedStop stop) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        String category = normalizeStopCategory(stop.getCategory());
        return "CHECKOUT".equals(category)
                || text.contains("checkout")
                || text.contains("check-out")
                || text.contains("check out")
                || text.contains("trả phòng")
                || text.contains("tra phong");
    }

    private boolean looksLikeReturnLeg(AiGeneratedStop stop, GenerateTripRequest req) {
        String text = combinedStopText(stop).toLowerCase(Locale.ROOT);
        String origin = req.getOrigin() == null ? "" : req.getOrigin().toLowerCase(Locale.ROOT);
        return text.contains("return")
                || text.contains("back")
                || text.contains("quay về")
                || text.contains("trở về")
                || text.contains("về ")
                || (!origin.isBlank() && text.contains(origin));
    }

    private String combinedStopText(AiGeneratedStop stop) {
        return String.join(" ",
                stop.getPlaceName() == null ? "" : stop.getPlaceName(),
                stop.getDescription() == null ? "" : stop.getDescription(),
                stop.getCategory() == null ? "" : stop.getCategory()
        );
    }

    private boolean resolveNeedAccommodation(GenerateTripRequest req) {
        if (req.getNeedAccommodation() != null) {
            return req.getNeedAccommodation();
        }
        return req.getDays() >= 2;
    }

    private String normalizeTimePreference(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MORNING", "NOON", "AFTERNOON", "EVENING", "CUSTOM" -> normalized;
            default -> fallback;
        };
    }

    private String normalizeAccommodationPreference(String value) {
        if (value == null || value.isBlank()) {
            return "ANY";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BUDGET", "HOMESTAY", "HOTEL", "RESORT", "NEAR_CENTER", "NEAR_BEACH", "ANY" -> normalized;
            default -> "ANY";
        };
    }

    private String normalizeTransportPreference(String value) {
        if (value == null || value.isBlank()) {
            return "AUTO";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AUTO", "MOTORBIKE", "BUS", "CAR", "FLIGHT", "TRAIN" -> normalized;
            default -> "AUTO";
        };
    }

    private String timePreferenceWindow(String preference, String customTime) {
        return switch (preference) {
            case "MORNING" -> "06:00-09:00";
            case "NOON" -> "10:00-13:00";
            case "AFTERNOON" -> "14:00-17:00";
            case "EVENING" -> "18:00-21:00";
            case "CUSTOM" -> isValidStartTime(customTime) ? customTime : "09:00";
            default -> "06:00-09:00";
        };
    }

    private LocalTime preferenceEndTime(String preference, String customTime) {
        if ("CUSTOM".equals(preference) && isValidStartTime(customTime)) {
            return LocalTime.parse(customTime.trim(), START_TIME_FORMATTER);
        }
        return switch (preference) {
            case "MORNING" -> LocalTime.of(9, 0);
            case "NOON" -> LocalTime.of(13, 0);
            case "AFTERNOON" -> LocalTime.of(17, 0);
            case "EVENING" -> LocalTime.of(21, 0);
            default -> LocalTime.of(9, 0);
        };
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

    private Optional<GeoPoint> getKnownCityCoordinate(String city) {
        double[] coords = getDefaultCityCoordinates(city);
        if (coords[0] == 16.0 && coords[1] == 106.0) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(coords[0], coords[1]));
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null
                && Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0.0 && lng != 0.0;
    }

    private double destinationRadiusKm(String destination) {
        String normalized = destination == null ? "" : destination.toLowerCase(Locale.ROOT);
        if (normalized.contains("vung tau") || normalized.contains("vũng tàu")) return 35.0;
        if (normalized.contains("ha giang") || normalized.contains("phu quoc")
                || normalized.contains("sapa") || normalized.contains("sa pa")) {
            return 60.0;
        }
        return 30.0;
    }

    private boolean isMealOrCafeCategory(String category) {
        String normalized = normalizeStopCategory(category);
        return Set.of("FOOD", "CAFE", "MARKET").contains(normalized);
    }

    private boolean requiresLocalCluster(AiGeneratedStop stop, String category) {
        if (explicitlyOnReturnRoute(stop)) {
            return false;
        }
        String normalized = normalizeStopCategory(category);
        return Set.of("FOOD", "CAFE", "MARKET").contains(normalized)
                || isCheckoutStop(stop)
                || ("OTHER".equals(normalized) && isGenericLocalStop(stop))
                || isGenericLocalStop(stop);
    }

    private double localClusterLimitKm(AiGeneratedStop stop, String category, String destination) {
        if (isGenericLocalStop(stop) && isMealOrCafeCategory(category)) {
            return 8.0;
        }
        if (isVungTauDestination(destination)) {
            return 12.0;
        }
        return 12.0;
    }

    private boolean isGenericLocalStop(AiGeneratedStop stop) {
        String text = normalizedStopText(stop);
        if (text.isBlank()) return true;
        return text.matches(".*\\b(ca phe|cafe|an trua|an toi|an sang|mua dac san|dac san|tra phong|checkout|check out|dao bien|cho dia phuong|cho dem|hai san|nghi chan|view bien)\\b.*");
    }

    private boolean explicitlyOnReturnRoute(AiGeneratedStop stop) {
        String text = normalizedStopText(stop);
        return text.contains("tren duong ve")
                || text.contains("doc duong ve")
                || text.contains("on the way back");
    }

    private boolean isVungTauDestination(String destination) {
        String normalized = normalizeAscii(destination);
        return normalized.contains("vung tau");
    }

    private String normalizedStopText(AiGeneratedStop stop) {
        return normalizeAscii(combinedStopText(stop));
    }

    private String normalizeAscii(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double roundDistance(double km) {
        return Math.round(km * 10.0) / 10.0;
    }

    private record GeoPoint(double lat, double lng) {}

    private record GeoFence(GeoPoint center, double radiusKm) {}

    private enum DestinationProfile {
        BEACH,
        MOUNTAIN,
        CULTURAL_CITY,
        CITY
    }

    private class LocalClusterState {
        private GeoPoint hotelAnchor;
        private final List<GeoPoint> acceptedLocalPoints = new ArrayList<>();

        Optional<GeoPoint> anchor() {
            if (hotelAnchor != null) {
                return Optional.of(hotelAnchor);
            }
            if (acceptedLocalPoints.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(averagePoint(acceptedLocalPoints));
        }

        void accept(AiGeneratedStop stop, String category, GeoPoint point) {
            if (isCheckInOrAccommodationStop(stop) && hotelAnchor == null) {
                hotelAnchor = point;
                log.info("AI trip local cluster hotel/check-in anchor chosen from '{}' at [{}, {}]",
                        stop.getPlaceName(), point.lat(), point.lng());
            }

            acceptedLocalPoints.add(point);
            GeoPoint currentAnchor = anchor().orElse(point);
            log.info("AI trip local cluster updated by '{}' category={} acceptedPoints={} anchor=[{}, {}]",
                    stop.getPlaceName(), category, acceptedLocalPoints.size(), currentAnchor.lat(), currentAnchor.lng());
        }

        private GeoPoint averagePoint(List<GeoPoint> points) {
            double lat = points.stream().mapToDouble(GeoPoint::lat).average().orElse(0.0);
            double lng = points.stream().mapToDouble(GeoPoint::lng).average().orElse(0.0);
            return new GeoPoint(lat, lng);
        }
    }

    private String buildResearchPromptSection(TravelResearchContext context) {
        if (context == null || !context.hasUsefulContext()) {
            return "- No external web research context available. Use built-in Vietnam travel knowledge and keep all final places provider-verifiable.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("- destination: " + nullToEmpty(context.getDestination()));
        if (context.getMonthText() != null && !context.getMonthText().isBlank()) {
            lines.add("- month: " + context.getMonthText());
        }
        appendResearchList(lines, "seasonalNotes", context.getSeasonalNotes(), 5);
        appendResearchList(lines, "recommendedAttractions", context.getRecommendedAttractions(), 8);
        appendResearchList(lines, "foodSuggestions", context.getFoodSuggestions(), 6);
        appendResearchList(lines, "hotelAreaSuggestions", context.getHotelAreaSuggestions(), 5);
        appendResearchList(lines, "warningsOrTips", context.getWarningsOrTips(), 5);
        if (context.getSourceSummaries() != null && !context.getSourceSummaries().isEmpty()) {
            lines.add("- sourceSummaries:");
            context.getSourceSummaries().stream().limit(5).forEach(source -> lines.add(
                    "  - " + nullToEmpty(source.getSourceDomain()) + ": "
                            + abbreviate(nullToEmpty(source.getTitle()) + " " + nullToEmpty(source.getSnippet()), 180)
            ));
        }
        return String.join("\n", lines);
    }

    private void appendResearchList(List<String> lines, String label, List<String> values, int limit) {
        if (values == null || values.isEmpty()) return;
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(limit)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        if (!joined.isBlank()) {
            lines.add("- " + label + ": " + joined);
        }
    }

    private String formatCandidatePool(List<CandidatePlace> candidatePool) {
        if (candidatePool == null || candidatePool.isEmpty()) {
            return "No verified candidates available.";
        }
        StringBuilder sb = new StringBuilder();
        for (CandidatePlace cp : candidatePool) {
            sb.append("- ").append(cp.getTitle())
                    .append(" (").append(cp.getCategory()).append(")");
            if (cp.getRating() != null) {
                sb.append(", Rating: ").append(cp.getRating());
            }
            if (cp.getReviewCount() != null) {
                sb.append(", Reviews: ").append(cp.getReviewCount());
            }
            if (cp.getAddress() != null && !cp.getAddress().isBlank()) {
                sb.append(", Address: ").append(cp.getAddress());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildCandidatePoolPromptSection(List<CandidatePlace> candidatePool) {
        if (candidatePool == null || candidatePool.isEmpty()) {
            return """
                AI-FIRST PLACE SELECTION:
                - Use the web research context for seasonal relevance, local food names, hotel areas, travel warnings and popular attraction inspiration.
                - You may suggest well-known real places and venues that are appropriate for the destination.
                - Exact provider metadata will be verified after Gemini by SerpApi/Goong; do not fake ratings, review counts, addresses, opening hours, provider IDs or hotel prices.
                - Avoid ugly generic real-stop names. If a slot cannot name a confident venue, use an honest natural fallback with a strong `searchQuery`, for example "Ăn sáng tự do gần khu trung tâm", "Cafe tự chọn gần biển", or "Tự do check-in quanh Bãi Sau".
                - For hotels, output a searchable hotel-area suggestion instead of a fake hotel name. Backend will choose a real hotel if provider data is available.
                """;
        }
        return """
            CANDIDATE POOL (verified real places - prefer these when they fit naturally):
            %s
            - Use the web research context for seasonal relevance and inspiration only.
            - Prefer verified candidates for exact venues, but keep the itinerary complete and human-paced.
            - If a required meal/cafe/hotel slot is missing from the pool, output a natural generic activity with a strong `searchQuery`; backend will verify or convert it to an honest system fallback.
            - Do not invent hotel metadata. Exact place/hotel details will be verified later by SerpApi/Goong.
            """.formatted(formatCandidatePool(candidatePool));
    }

    private String buildPrompt(GenerateTripRequest req, TravelResearchContext researchContext, List<CandidatePlace> candidatePool) {
        String interests = req.getInterests() == null || req.getInterests().isEmpty()
                ? "general sightseeing"
                : String.join(", ", req.getInterests());
        String language = req.getLanguage() == null || req.getLanguage().isBlank()
                ? "vi"
                : req.getLanguage();
        String departurePreference = normalizeTimePreference(req.getDepartureTimePreference(), "MORNING");
        String returnPreference = normalizeTimePreference(req.getReturnTimePreference(), "AFTERNOON");
        boolean needAccommodation = resolveNeedAccommodation(req);
        String accommodationPreference = normalizeAccommodationPreference(req.getAccommodationPreference());
        String transportPreference = normalizeTransportPreference(req.getTransportPreference());
        DestinationProfile destinationProfile = destinationProfile(req.getDestination());
        String researchSummary = buildResearchPromptSection(researchContext);

        return """
            IMPORTANT: Generate a REALISTIC travel plan for %s, NOT the example below.
            You are a Vietnam travel planner. Generate exactly 3 different trip plans from %s to %s for %d days.
            Traveler interests: %s. Requested language: %s. Budget preference: %s.
            Trip preferences:
            - destinationProfile: %s
            - departureTimePreference: %s, departureTime/window: %s
            - returnTimePreference: %s, returnTime/window: %s
            - transportPreference: %s
            - needAccommodation: %s
            - accommodationPreference: %s

            WEB RESEARCH CONTEXT:
            %s

            %s

            Return ONLY a valid JSON array of 3 plan objects (no markdown, no code fences, no extra text).
            Each plan must have exactly these fields:
            - name (string, creative plan title)
            - badge (string, one of: "AI Recommended", "Budget Friendly", "Premium Experience")
            - budget (string, estimated total in the local trip currency)
            - duration (string, e.g. "3 days")
            - accommodation (string)
            - foodStyle (string, e.g. "Street food + local")
            - timeline (array of day objects)

            LANGUAGE RULES:
            - If requested language is "vi" or Vietnamese, ALL user-facing fields must be Vietnamese:
              name, badge, budget text, accommodation, foodStyle, activity/placeName, description, notes, travel tips, transport labels, transport reasons and food descriptions.
            - In Vietnamese mode, never output English sentences such as "Start the journey...", "Check in to your hotel...", "Enjoy fresh seafood...", or "Exploring traditional Hmong culture".
            - Descriptions/notes in Vietnamese mode must be short, natural Vietnamese sentences. Do not mix English and Vietnamese.
            - Keep real place names as their official/common local Vietnamese names with proper diacritics.

            USER PREFERENCE RULES:
            - Day 1 must respect departureTimePreference:
              MORNING means start around 06:00-09:00; NOON means 10:00-13:00; AFTERNOON means 14:00-17:00; EVENING means 18:00-21:00 and fewer sightseeing stops; CUSTOM means use the provided departureTime.
            - Do not start Day 1 at 15:00 unless departureTimePreference is AFTERNOON/EVENING or CUSTOM near that time.
            - Final day must include a return transport/access leg according to returnTimePreference. MORNING means return in the morning, AFTERNOON means return in the afternoon, EVENING means return in the evening, CUSTOM means use returnTime.
            - Do not add sightseeing after the planned return leg unless it is clearly before the return time.
            - If days >= 2 and needAccommodation is true, include HOTEL/HOMESTAY/check-in or drop-luggage on Day 1 and checkout on the final day. Do not leave the user wandering all day without a place to stay.
            - If needAccommodation is false, do not force hotel stops; only mention accommodation if needed as context.
            - Accommodation preference is %s. Use it when selecting hotel/homestay/resort/check-in stops.
            - Transport preference is %s. AUTO chooses based on distance and practicality; MOTORBIKE is only for safe near/local trips; BUS prefers bus/limousine; CAR prefers private car/taxi; FLIGHT uses airports for far trips; TRAIN uses train where practical.
            - For TP.HCM -> Vũng Tàu, do not use flight. Prefer bus/car/motorbike.
            - For TP.HCM -> Hà Giang, use flight/bus combination or bus/limousine via Hà Nội/Nội Bài, not a direct road route to a sightseeing stop.

            ACCOMMODATION RULES:
            - Do NOT invent hotel names. For accommodation/check-in stops, prefer category "HOTEL" or "ACCOMMODATION", activity "Gợi ý khách sạn khu Bãi Sau" or "Gợi ý khách sạn gần trung tâm", and searchQuery such as "khách sạn Bãi Sau Vũng Tàu" or "hotel Back Beach Vung Tau". Backend will choose the real hotel from provider data.
            - Pure actions like "Trả phòng khách sạn", "Di chuyển đến khách sạn", or "Nghỉ ngơi tại khách sạn" may remain system/checkpoint stops, but check-in/accommodation suggestions should be searchable hotel stops.
            - If days >= 2 and needAccommodation is true, Day 1 MUST include one visible accommodation/check-in/drop-luggage stop. Use category "HOTEL", "HOMESTAY", "ACCOMMODATION", "CHECKIN" or "LODGING".
            - The final day MUST include a visible checkout stop. Use category "CHECKOUT" or a Vietnamese activity such as "Trả phòng khách sạn".
            - Accommodation must be inside the destination/local sightseeing area, not on the intercity route.
            - For Vũng Tàu, prefer Bãi Sau, Bãi Trước, trung tâm Vũng Tàu, or near local stops. Do NOT place accommodation on QL51, Long Thành, Phú Mỹ, or the TP.HCM-Vũng Tàu highway route.
            - If you are unsure about a specific hotel name, use generic but useful stop names such as "Nhận phòng khách sạn khu Bãi Sau", "Gửi hành lý tại homestay gần trung tâm", or "Trả phòng khách sạn".
            - Do not make users carry luggage through heavy sightseeing before check-in unless the stop explicitly says they can gửi hành lý/drop luggage first.

            MEAL AND CAFE RULES:
            - Every realistic itinerary must include FOOD/CAFE/MARKET breaks when the day crosses normal meal times, without overloading the schedule.
            - For 1-day trips: if the trip starts in the morning and continues after lunch, include a FOOD lunch stop; if the trip ends in the evening, include dinner or a cafe/food stop. Do not force accommodation.
            - For 2+ day trips: Day 1 should usually include lunch after arrival or before/after check-in, plus dinner, a food market, or cafe in the evening.
            - For trips >= 2 days, if Day 1 has activities ending before 20:30 and the user is already at the destination, add one evening FOOD/CAFE/MARKET or light local activity unless the schedule is already too full.
            - Day 1 should not usually end around 17:00 with no dinner, cafe, market, beach walk, or evening rest activity.
            - Good Day 1 evening examples for Vũng Tàu: "Ăn tối hải sản gần Bãi Sau", "Dạo biển Bãi Trước", "Cà phê view biển", "Chợ đêm Vũng Tàu".
            - For middle days: include breakfast/cafe if the day starts early, lunch, and dinner if the schedule continues into the evening.
            - For the final day: include breakfast/cafe or lunch before checkout/return depending on returnTimePreference; do not add food after return transport unless it is clearly realistic.
            - Every day should have at least one meal/rest/chill anchor when the schedule is longer than half a day. This can be FOOD, CAFE, MARKET, a beach walk, a sunset stop, a viewpoint rest, or a local dessert stop.
            - Keep the pace human: usually 4-6 user-facing stops per day excluding pure transport/check-in/check-out anchors. Do not create a checklist marathon.
            - Use category FOOD for meals, local restaurants and normal food stops; CAFE for coffee, dessert or rest breaks; MARKET for night markets or food markets.
            - Food/cafe stops must be near the current area/route: near hotel/check-in, near a sightseeing cluster, near the beach/center for beach trips. Do not send the user 20 km just to eat unless it is a famous food stop and fits the route.
            - Meal time windows: breakfast/cafe 07:00-09:30; lunch 11:00-13:30; cafe/rest 14:00-16:30; dinner/night market 18:00-20:30.
            - Do not schedule heavy sightseeing during normal meal breaks on a long day.
            - In Vietnamese mode, food/cafe descriptions must be Vietnamese and use local suggestions when possible, e.g. "Ăn trưa với bánh khọt gần Bãi Sau", "Cà phê nghỉ chân gần biển", "Ăn tối hải sản tại Chợ đêm Vũng Tàu".

            DESTINATION PROFILE RULES:
            - destinationProfile BEACH: include beach/seafood/cafe/sunset rhythm where realistic; prefer coastal clusters, center, beach roads and night markets.
            - destinationProfile MOUNTAIN: include viewpoints, cafe/rest stops, slower transfer times, and sunrise/misty morning options only when the day can start early.
            - destinationProfile CULTURAL_CITY: include old town/heritage clusters, local food markets and walking-friendly pacing.
            - destinationProfile CITY: include neighborhoods, food streets, cafes, museums/landmarks and avoid zig-zagging across the city.
            - Do not invent coordinates for generic experience stops. Use a specific local place when confident; otherwise give a clear searchQuery so enrichment can resolve it.

            PLACE NAME AND SEARCH QUERY QUALITY:
            - Avoid generic non-system stop names such as "Ăn sáng địa phương tại Vũng Tàu", "Ăn trưa hải sản Bãi Sau", "Cà phê view biển Bãi Trước", or "Cà phê nghỉ chân gần biển".
            - Prefer specific real venue/place names when widely known, then put an exact provider-friendly searchQuery.
            - For Vũng Tàu examples: "Bánh Khọt Gốc Vú Sữa", "Gành Hào", "Sơn Đăng Coffee", "Marina Club Vũng Tàu", "Chợ đêm Vũng Tàu", "Tượng Chúa Kitô Vua", "Hải đăng Vũng Tàu", "Mũi Nghinh Phong", "Bãi Sau", "Bãi Trước".
            - If you are not confident about a venue, keep the activity natural but make searchQuery specific, e.g. "quán cafe view biển Vũng Tàu" or "nhà hàng hải sản Bãi Sau Vũng Tàu".
            - For non-system stops, always provide a useful `searchQuery` with destination/area and no duplicated destination words.

            Each day object:
            - day (integer, 1-based)
            - items (array of stop objects)

            Each stop object MUST include ALL of these fields (no exceptions):
            - "time" (REQUIRED: "Morning", "Afternoon", or "Evening")
            - "startTime" (REQUIRED: concrete local start time in "HH:mm", e.g. "08:30")
            - "endTime" (REQUIRED: concrete local end time in "HH:mm")
            - "durationMinutes" (REQUIRED: integer estimated duration in minutes, e.g. 90)
            - "estimatedCost" (REQUIRED: numeric cost in the trip local currency, direct units not cents; use 0 for free activities)
            - "activity" (REQUIRED: place name string)
            - "description" (REQUIRED: natural travel description only)
            - "reason" (REQUIRED: why this stop belongs at this time/place, natural language)
            - "category" (REQUIRED, one of: "TRANSPORT", "FLIGHT", "BUS", "TRAIN", "TAXI", "MOTORBIKE", "HOTEL", "HOMESTAY", "ACCOMMODATION", "CHECKIN", "CHECKOUT", "LODGING", "SIGHTSEEING", "FOOD", "CAFE", "MARKET", "EMERGENCY", "OTHER")
            - "searchQuery" (REQUIRED: provider-friendly Vietnamese search query; include destination/area, no coordinates)
            - "candidateProviderId" (optional: providerId from candidate pool if used)
            - "providerTitle" (optional: providerTitle from candidate pool if used)
            - "isSystemStop" (REQUIRED boolean: true only for transport, check-in/check-out, rest/admin anchors; false for real places)
            - "mustHave" (REQUIRED boolean: true for essential transport, meals, accommodation/check-out, or signature attractions)
            - "flexibility" (REQUIRED: "FIXED" or "FLEXIBLE")
            - "rationale" (REQUIRED: one short sentence explaining why this stop fits the route)
            - "mealType" (REQUIRED: "BREAKFAST", "LUNCH", "DINNER", "DRINK", or null)
            - "experienceType" (REQUIRED: "LOCAL_FOOD", "CAFE_CHILL", "SUNRISE", "SUNSET", "VIEWPOINT", "HERITAGE", "BEACH", "MARKET", "TRANSPORT", "ACCOMMODATION", or null)
            - "lat" (REQUIRED: decimal degrees latitude, e.g. 11.9404)
            - "lng" (REQUIRED: decimal degrees longitude, e.g. 108.4385)
            - "travelFromPrevious" (REQUIRED for every item except the first item of the day; null for the first item)

            travelFromPrevious must be an object:
            {
              "distanceKm": number,
              "durationMinutes": integer,
              "suggestedModes": [
                { "mode": "MOTORBIKE" | "TAXI" | "BUS" | "WALK" | "FLIGHT" | "TRAIN", "durationMinutes": integer, "reason": string }
              ]
            }

            CLEAN NOTES RULES:
            - description must be short natural travel guidance only.
            - Good Vietnamese notes examples:
              "Bắt đầu di chuyển từ TP.HCM đến Vũng Tàu theo tuyến QL51."
              "Nhận phòng khách sạn khu Bãi Sau và gửi hành lý trước khi tham quan."
              "Thưởng thức hải sản gần Bãi Sau, phù hợp để nghỉ trưa sau khi di chuyển."
              "Leo lên tượng Chúa để ngắm toàn cảnh biển Vũng Tàu."
            - Do not put Coordinates, Time, Start time, Duration, Estimated cost or Cost inside description/notes.
            - Those values must stay only in structured fields.

            GEOGRAPHIC REALISM RULES:
            - Group stops by nearby area. Route convenience is more important than listing famous places randomly.
            - Stops in the same half-day should be geographically close.
            - Consecutive local sightseeing/food/cafe/market stops should normally be under 10-20 km in cities, or 20-50 km in mountain/rural trips.
            - If a segment is longer, represent it as a TRANSPORT/FLIGHT/BUS/TRAIN/TAXI/MOTORBIKE leg or move the stop to another day.
            - Never mix far-away attractions in the same day without a clear transport reason.

            ACCESS AND RETURN PLANNING:
            - If origin and destination are far apart, include access/transport legs before sightseeing:
              origin/local area -> departure hub -> long-distance transport -> arrival hub -> hotel/check-in or first local stop -> local itinerary.
            - If return is realistic within the trip length, include final local stop -> return hub -> return transport -> origin/local end point.
            - Near trip under about 150 km: suggest car, bus, taxi or motorbike if realistic.
            - Medium trip 150-400 km: suggest bus, limousine, train or car depending on the destination.
            - Far trip over 400 km: consider flight/train/bus combinations. If the destination has no airport, use the nearest practical airport or bus station, then local transfer.
            - Example: TP.HCM to Hà Giang must not start directly at Cổng trời Quản Bạ. It should include TP.HCM -> Nội Bài/Hà Nội -> Hà Giang -> local stops.

            REALISTIC INTERCITY DURATION RULES:
            - Intercity transport durationMinutes must be conservative and realistic, including traffic, pickup/drop-off and city access time.
            - TP.HCM -> Vũng Tàu by bus/car/motorbike usually takes 150-210 minutes (2.5-3.5 hours). Vũng Tàu -> TP.HCM also usually takes 150-210 minutes. Do NOT output 60 minutes for this route.
            - For any intercity route over 80 km, durationMinutes should not be below 90 minutes.
            - For any intercity route over 150 km, durationMinutes should not be below 150 minutes.
            - If unsure, prefer a conservative duration rather than an unrealistically short one.

            DAY PLANNING:
            - Stop-count target: 2D1N trips should contain 10-14 total stops; 3D2N trips should contain 14-20 total stops. Do not return sparse plans with only 1-2 stops per day.
            - Required 2D1N rhythm: Day 1 must include origin-to-destination transport, lunch/local food, check-in/hotel/drop luggage when accommodation is needed, one attraction/beach stop, cafe/chill if requested, dinner/seafood, and an optional night walk/market. Day 2 must include breakfast, 1-2 attractions/check-in stops, lunch, optional cafe/souvenir stop, checkout, and return transport.
            - Required 3D2N rhythm: every day must have at least 3-5 meaningful stops; include breakfast/lunch/dinner rhythm where realistic, check-in/check-out, outbound/return travel, and cafe/chill when requested.
            - For 2+ day trips, use this natural flow: Day 1 intercity transport -> arrival/drop luggage/check-in -> lunch/food -> light sightseeing -> dinner/night activity.
            - Middle days should include local sightseeing + food/cafe.
            - Final day should be breakfast/cafe or light sightseeing -> checkout/trả phòng -> lunch or mua đặc sản if time allows -> return transport.
            - Do not overload a day with long-distance transport plus too many attractions.
            - If day 1 includes long-distance transport, reduce sightseeing stops that day.
            - If final day includes return transport, reduce sightseeing stops that day.

            IMPORTANT: Use realistic non-uniform startTime values based on the itinerary flow (for example "08:30", "10:15", "13:45"), not fixed 08:00/13:00/18:00 slots.
            IMPORTANT: durationMinutes must reflect the actual visit/travel duration for each item. Use 45-180 minutes depending on the activity.
            IMPORTANT: estimatedCost must be a number, not a formatted string. Missing estimatedCost is not allowed; free activities must use 0.
            IMPORTANT: The 'activity' field (place name) MUST be in Vietnamese with proper diacritics, using the official Vietnamese name (e.g. 'Hồ Gươm' not 'Hoan Kiem Lake', 'Văn Miếu' not 'Temple of Literature'). The 'description' field language follows the requested language (%s), but 'activity' is ALWAYS Vietnamese.
            IMPORTANT: Every single item MUST have lat and lng fields with real GPS coordinates. Items without lat/lng will be rejected.

            The 3 plans should differ in style:
            Plan 1 (Balanced / First-time traveler): signature places, realistic pace, clear meals and one chill stop per full day, budget %s
            Plan 2 (Food & Chill / Local experience): local food, cafes, markets, relaxed neighborhood clusters, fewer heavy attractions
            Plan 3 (Adventure / Scenic / Photo spots): sunrise/sunset/viewpoints/outdoor stops where realistic, but still includes meals and recovery breaks

            Example output (follow this exact structure):
            [
              {
                "name": "Khám phá Hà Nội theo tuyến thuận tiện",
                "badge": "AI Recommended",
                "budget": "1200000 VND",
                "duration": "%d days",
                "accommodation": "Khách sạn khu phố cổ",
                "foodStyle": "Ẩm thực đường phố và quán địa phương",
                "timeline": [
                  {
                    "day": 1,
                    "items": [
                      { "time": "Morning", "startTime": "08:30", "durationMinutes": 60, "estimatedCost": 120000, "activity": "Di chuyển từ sân bay Nội Bài vào phố cổ", "description": "Đi taxi hoặc xe công nghệ về khách sạn, gửi hành lý trước khi bắt đầu tham quan.", "category": "TAXI", "lat": 21.2142, "lng": 105.8028, "travelFromPrevious": null },
                      { "time": "Morning", "startTime": "09:45", "durationMinutes": 30, "estimatedCost": 0, "activity": "Gửi hành lý tại khách sạn khu phố cổ", "description": "Gửi hành lý hoặc nhận phòng sớm nếu có phòng trống để di chuyển nhẹ nhàng hơn.", "category": "CHECKIN", "lat": 21.0338, "lng": 105.8495, "travelFromPrevious": { "distanceKm": 3.2, "durationMinutes": 15, "suggestedModes": [{ "mode": "TAXI", "durationMinutes": 15, "reason": "Di chuyển nhanh tới khu lưu trú" }] } },
                      { "time": "Morning", "startTime": "10:30", "durationMinutes": 90, "estimatedCost": 0, "activity": "Hồ Gươm", "description": "Đi bộ nhẹ quanh hồ, ghé đền Ngọc Sơn nếu còn thời gian.", "category": "SIGHTSEEING", "lat": 21.0285, "lng": 105.8542, "travelFromPrevious": { "distanceKm": 1.0, "durationMinutes": 12, "suggestedModes": [{ "mode": "WALK", "durationMinutes": 12, "reason": "Khách sạn và hồ nằm gần nhau trong khu trung tâm" }] } },
                      { "time": "Afternoon", "startTime": "13:30", "durationMinutes": 90, "estimatedCost": 80000, "activity": "Phố Cổ Hà Nội", "description": "Dạo các tuyến phố gần nhau, thử vài món ăn vặt địa phương.", "category": "MARKET", "lat": 21.0340, "lng": 105.8500, "travelFromPrevious": { "distanceKm": 1.2, "durationMinutes": 15, "suggestedModes": [{ "mode": "WALK", "durationMinutes": 15, "reason": "Hai điểm rất gần, đi bộ thuận tiện" }] } },
                      { "time": "Evening", "startTime": "18:00", "durationMinutes": 90, "estimatedCost": 180000, "activity": "Phố Bia Tạ Hiện", "description": "Ăn tối và cảm nhận không khí phố đêm, giữ lịch nhẹ để nghỉ sớm.", "category": "FOOD", "lat": 21.0345, "lng": 105.8519, "travelFromPrevious": { "distanceKm": 0.5, "durationMinutes": 8, "suggestedModes": [{ "mode": "WALK", "durationMinutes": 8, "reason": "Nằm trong cùng khu phố cổ" }] } }
                    ]
                  },
                  {
                    "day": 2,
                    "items": [
                      { "time": "Morning", "startTime": "08:30", "durationMinutes": 30, "estimatedCost": 0, "activity": "Trả phòng khách sạn", "description": "Trả phòng và gửi hành lý lại quầy lễ tân nếu còn tham quan buổi sáng.", "category": "CHECKOUT", "lat": 21.0338, "lng": 105.8495, "travelFromPrevious": null },
                      { "time": "Morning", "startTime": "09:15", "durationMinutes": 90, "estimatedCost": 70000, "activity": "Văn Miếu - Quốc Tử Giám", "description": "Tham quan khu di tích vào buổi sáng để tránh nắng và đông khách.", "category": "SIGHTSEEING", "lat": 21.0236, "lng": 105.8357, "travelFromPrevious": { "distanceKm": 2.2, "durationMinutes": 15, "suggestedModes": [{ "mode": "TAXI", "durationMinutes": 15, "reason": "Di chuyển ngắn từ khách sạn sau khi trả phòng" }] } },
                      { "time": "Morning", "startTime": "11:15", "durationMinutes": 75, "estimatedCost": 0, "activity": "Lăng Chủ tịch Hồ Chí Minh", "description": "Ghé khu Ba Đình và quảng trường, kiểm tra giờ mở cửa trước khi đi.", "category": "SIGHTSEEING", "lat": 21.0366, "lng": 105.8344, "travelFromPrevious": { "distanceKm": 2.0, "durationMinutes": 12, "suggestedModes": [{ "mode": "TAXI", "durationMinutes": 12, "reason": "Tiết kiệm sức cho lịch trình trong ngày" }] } },
                      { "time": "Afternoon", "startTime": "15:30", "durationMinutes": 120, "estimatedCost": 220000, "activity": "Hồ Tây", "description": "Ngắm hoàng hôn và ăn tối quanh hồ, kết thúc ngày ở khu vực ít phải di chuyển xa.", "category": "FOOD", "lat": 21.0560, "lng": 105.8239, "travelFromPrevious": { "distanceKm": 3.5, "durationMinutes": 18, "suggestedModes": [{ "mode": "TAXI", "durationMinutes": 18, "reason": "Di chuyển ngắn trong nội đô" }] } }
                    ]
                  }
                ]
              }
            ]
            """.formatted(
                req.getDestination(),
                req.getOrigin(), req.getDestination(), req.getDays(),
                interests, language, req.getBudget(),
                destinationProfile,
                departurePreference, timePreferenceWindow(departurePreference, req.getDepartureTime()),
                returnPreference, timePreferenceWindow(returnPreference, req.getReturnTime()),
                transportPreference,
                needAccommodation,
                accommodationPreference,
                researchSummary,
                buildCandidatePoolPromptSection(candidatePool),
                accommodationPreference,
                transportPreference,
                language,
                req.getBudget(),
                req.getDays()
        ).trim();
    }

}
