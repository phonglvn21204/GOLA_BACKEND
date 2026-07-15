package com.gola.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.GolaProperties;
import com.gola.dto.community.AlbumResponse;
import com.gola.dto.trip.ReelStoryboardResponse;
import com.gola.dto.trip.TravelBookResponse;
import com.gola.entity.Album;
import com.gola.entity.AlbumMedia;
import com.gola.entity.Expense;
import com.gola.entity.Quest;
import com.gola.entity.QuestProgress;
import com.gola.entity.Trip;
import com.gola.entity.TripMemory;
import com.gola.entity.TripMemoryPhoto;
import com.gola.entity.TripNote;
import com.gola.entity.TripStop;
import com.gola.entity.enums.QuestProgressStatus;
import com.gola.entity.enums.TripStatus;
import com.gola.exception.GolaException;
import com.gola.repository.AlbumMediaRepository;
import com.gola.repository.AlbumRepository;
import com.gola.repository.ExpenseRepository;
import com.gola.repository.QuestProgressRepository;
import com.gola.repository.QuestRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripMemoryPhotoRepository;
import com.gola.repository.TripMemoryRepository;
import com.gola.repository.TripNoteRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripMemoryArtifactService {
    private static final String NOT_GENERATED = "NOT_GENERATED";
    private static final String GENERATING = "GENERATING";
    private static final String READY = "READY";
    private static final String FAILED = "FAILED";
    private static final String SOURCE_PERSONAL_PHOTOS = "PERSONAL_PHOTOS";
    private static final String SOURCE_ITINERARY = "ITINERARY";
    private static final String UPLOAD_PHOTOS_REQUIRED = "UPLOAD_PHOTOS_REQUIRED: Add at least one memory photo before generating a personal album.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TripRepository tripRepo;
    private final TripMemberRepository memberRepo;
    private final TripMemoryRepository memoryRepo;
    private final TripMemoryPhotoRepository photoRepo;
    private final TripStopRepository stopRepo;
    private final TripNoteRepository noteRepo;
    private final ExpenseRepository expenseRepo;
    private final QuestProgressRepository questProgressRepo;
    private final QuestRepository questRepo;
    private final AlbumRepository albumRepo;
    private final AlbumMediaRepository albumMediaRepo;
    private final AlbumService albumService;
    private final GeminiClient geminiClient;
    private final GolaProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public AlbumResponse generateAlbum(UUID tripId, UUID userId, boolean regenerate) {
        return generateAlbum(tripId, userId, regenerate, SOURCE_PERSONAL_PHOTOS, "Cinematic");
    }

    @Transactional
    public AlbumResponse generateAlbum(UUID tripId, UUID userId, boolean regenerate, String requestedSource) {
        return generateAlbum(tripId, userId, regenerate, requestedSource, "Cinematic");
    }

    @Transactional
    public AlbumResponse generateAlbum(UUID tripId, UUID userId, boolean regenerate, String requestedSource, String requestedStyle) {
        String albumSource = normalizeAlbumSource(requestedSource);
        String albumStyle = normalizeAlbumStyle(requestedStyle);
        TripContext context = loadContext(tripId, userId, true);
        TripMemory memory = context.memory();

        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource) && context.photos().isEmpty()) {
            log.info("Personal trip memory album requires uploaded photos tripId={} userId={}", tripId, userId);
            throw GolaException.badRequest(UPLOAD_PHOTOS_REQUIRED);
        }

        Album existingAlbum = memory.getAlbumId() != null ? albumRepo.findById(memory.getAlbumId()).orElse(null) : null;
        if (!regenerate
            && READY.equals(memory.getAlbumStatus())
            && existingAlbum != null
            && albumSource.equals(existingAlbum.getAlbumSource())) {
            return getAlbum(tripId, userId);
        }

        replaceExistingAlbum(memory);
        memory.setAlbumStatus(GENERATING);
        memory.setAlbumError(null);
        memoryRepo.save(memory);

        Album album = albumRepo.save(Album.builder()
            .ownerId(userId)
            .tripId(tripId)
            .title(context.trip().getTitle())
            .coverUrl(firstImage(context, albumSource))
            .isPublic(false)
            .isAiCurated(true)
            .albumSource(albumSource)
            .status(GENERATING)
            .hashtags(new String[0])
            .build());

        try {
            AlbumGenerationResult generation = buildAlbumGenerationResult(context, albumSource, albumStyle);
            AlbumAiResult result = generation.result();
            log.info("Generating trip memory album tripId={} albumSource={} style={} generationMode={} stops={} uploadedPhotos={} imagesFound={}",
                tripId, albumSource, albumStyle, generation.mode(), context.stops().size(), context.photos().size(), countImages(context, albumSource));

            applyAlbumResult(album, result, context, albumSource);
            album = albumRepo.save(album);
            saveAlbumItems(album, result, context, albumSource, generation.mode(), albumStyle);

            memory.setAlbumId(album.getId());
            memory.setAlbumStatus(READY);
            memory.setAlbumError(null);
            memory.setAlbumGeneratedAt(Instant.now());
            memory.setStatus("GENERATED");
            memory.setGeneratedAt(Instant.now());
            memory.setSummary(firstText(result.getIntroStory(), result.getSummary(), memory.getSummary()));
            memoryRepo.save(memory);

            return albumService.mapToResponse(album);
        } catch (Exception e) {
            log.warn("Album generation failed for trip {}: {}", tripId, safeError(e));
            album.setStatus(FAILED);
            album.setErrorMessage("Album generation failed. Please try again.");
            albumRepo.save(album);
            memory.setAlbumId(album.getId());
            memory.setAlbumStatus(FAILED);
            memory.setAlbumError("Album generation failed. Please try again.");
            memoryRepo.save(memory);
            return albumService.mapToResponse(album);
        }
    }

    public AlbumResponse getAlbum(UUID tripId, UUID userId) {
        TripContext context = loadContext(tripId, userId, false);
        UUID albumId = context.memory().getAlbumId();
        Album album = albumId != null
            ? albumRepo.findById(albumId).orElse(null)
            : albumRepo.findFirstByTripIdAndOwnerIdAndIsAiCuratedTrueOrderByCreatedAtDesc(tripId, userId).orElse(null);
        if (album == null) {
            throw GolaException.notFound("Album");
        }
        return albumService.mapToResponse(album);
    }

    @Transactional
    public TravelBookResponse generateBook(UUID tripId, UUID userId) {
        TripContext context = loadContext(tripId, userId, true);
        TripMemory memory = context.memory();
        memory.setBookStatus(GENERATING);
        memory.setBookError(null);
        memory.setBookUrl(null);
        memory.setBookFilePath(null);
        memoryRepo.save(memory);

        try {
            Album album = selectAlbumForArtifacts(context);
            List<AlbumMedia> media = album != null
                ? albumMediaRepo.findByAlbumIdOrderByDayIndexAscOrderIdxAsc(album.getId())
                : List.of();
            log.info("Generating travel book tripId={} source={} mediaItems={}",
                tripId, album != null ? album.getAlbumSource() : "SUMMARY", media.size());
            Path pdfPath = writeTravelBookPdf(context, album, media);
            String downloadUrl = "/api/trips/" + tripId + "/memory/book/download";

            memory.setBookStatus(READY);
            memory.setBookUrl(downloadUrl);
            memory.setBookFilePath(pdfPath.toString());
            memory.setBookError(null);
            memory.setBookGeneratedAt(Instant.now());
            memoryRepo.save(memory);
            return bookResponse(memory);
        } catch (Exception e) {
            log.warn("Travel book generation failed for trip {}: {}", tripId, safeError(e));
            memory.setBookStatus(FAILED);
            memory.setBookError("Không thể tạo Travel Book PDF. Vui lòng thử lại.");
            memoryRepo.save(memory);
            return bookResponse(memory);
        }
    }

    public TravelBookResponse getBook(UUID tripId, UUID userId) {
        return bookResponse(loadContext(tripId, userId, false).memory());
    }

    public Path getBookPath(UUID tripId, UUID userId) {
        TripMemory memory = loadContext(tripId, userId, false).memory();
        if (!READY.equals(memory.getBookStatus()) || memory.getBookFilePath() == null) {
            throw GolaException.notFound("Travel book");
        }
        Path path;
        try {
            path = Path.of(memory.getBookFilePath());
        } catch (Exception e) {
            throw GolaException.notFound("Travel book file");
        }
        if (!Files.isRegularFile(path)) {
            throw GolaException.notFound("Travel book file");
        }
        return path;
    }

    @Transactional
    public ReelStoryboardResponse generateReel(UUID tripId, UUID userId) {
        TripContext context = loadContext(tripId, userId, true);
        TripMemory memory = context.memory();
        memory.setReelStatus(GENERATING);
        memory.setReelError(null);
        memoryRepo.save(memory);

        try {
            ReelStoryboardResponse storyboard = buildStoryboard(context);
            memory.setReelStatus(READY);
            memory.setReelStoryboard(storyboardToMap(storyboard));
            memory.setReelGeneratedAt(Instant.now());
            memory.setReelError(null);
            memoryRepo.save(memory);
            return storyboard;
        } catch (Exception e) {
            log.warn("Reel storyboard generation failed for trip {}: {}", tripId, e.getMessage());
            memory.setReelStatus(FAILED);
            memory.setReelError("Storyboard generation failed. Please try again.");
            memoryRepo.save(memory);
            return getReel(tripId, userId);
        }
    }

    public ReelStoryboardResponse getReel(UUID tripId, UUID userId) {
        TripMemory memory = loadContext(tripId, userId, false).memory();
        if (memory.getReelStoryboard() == null) {
            return ReelStoryboardResponse.builder()
                .status(defaultStatus(memory.getReelStatus()))
                .durationSeconds(0)
                .slides(List.of())
                .error(memory.getReelError())
                .generatedAt(memory.getReelGeneratedAt())
                .build();
        }
        return mapToStoryboard(memory);
    }

    private TripContext loadContext(UUID tripId, UUID userId, boolean requireCompletedOrMemory) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        Optional<TripMemory> existingMemory = memoryRepo.findByTripIdAndUserId(tripId, userId);
        if (requireCompletedOrMemory && trip.getStatus() != TripStatus.COMPLETED && existingMemory.isEmpty()) {
            throw GolaException.badRequest("Complete the trip before generating post-trip artifacts");
        }
        TripMemory memory = existingMemory.orElseGet(() -> memoryRepo.save(TripMemory.builder()
            .tripId(tripId)
            .userId(userId)
            .title(memoryTitle(trip))
            .summary(memorySummary(trip))
            .status(NOT_GENERATED)
            .shareStatus("PRIVATE")
            .build()));

        List<TripStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(tripId);
        List<TripMemoryPhoto> photos = photoRepo.findByTripIdAndUserIdOrderBySortOrderAscCreatedAtAsc(tripId, userId);
        List<TripNote> notes = noteRepo.findByTripIdOrderByCreatedAtDesc(tripId);
        List<Expense> expenses = expenseRepo.findByTripIdOrderByCreatedAtDesc(tripId);
        List<QuestProgress> completedQuestProgress = questProgressRepo.findByTripIdAndUserIdOrderByCreatedAtAsc(tripId, userId).stream()
            .filter(progress -> progress.getStatus() == QuestProgressStatus.COMPLETED)
            .toList();
        Map<UUID, Quest> questsById = questRepo.findAllById(completedQuestProgress.stream()
                .map(QuestProgress::getQuestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Quest::getId, quest -> quest));

        return new TripContext(trip, memory, stops, photos, notes, expenses, completedQuestProgress, questsById);
    }

    private void replaceExistingAlbum(TripMemory memory) {
        if (memory.getAlbumId() == null) {
            return;
        }
        UUID oldAlbumId = memory.getAlbumId();
        memory.setAlbumId(null);
        memoryRepo.save(memory);
        albumRepo.findById(oldAlbumId).ifPresent(albumRepo::delete);
    }

    private Album selectAlbumForArtifacts(TripContext context) {
        List<Album> readyAlbums = albumRepo.findByTripId(context.trip().getId()).stream()
            .filter(album -> context.memory().getUserId().equals(album.getOwnerId()))
            .filter(Album::isAiCurated)
            .filter(album -> READY.equals(album.getStatus()))
            .sorted(Comparator
                .comparing((Album album) -> firstInstant(album.getGeneratedAt(), album.getCreatedAt()), Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
            .toList();

        return readyAlbums.stream()
            .filter(album -> SOURCE_PERSONAL_PHOTOS.equals(album.getAlbumSource()))
            .findFirst()
            .or(() -> readyAlbums.stream().filter(album -> SOURCE_ITINERARY.equals(album.getAlbumSource())).findFirst())
            .or(() -> readyAlbums.stream().findFirst())
            .orElse(null);
    }

    private AlbumAiResult parseAlbumResult(String raw) throws IOException {
        String json = extractJsonObject(raw);
        return objectMapper.readValue(json, AlbumAiResult.class);
    }

    private AlbumGenerationResult buildAlbumGenerationResult(TripContext context, String albumSource, String albumStyle) {
        if (!hasGeminiKey()) {
            log.info("Trip memory album using fallback because Gemini is not configured tripId={} albumSource={} style={} stops={} uploadedPhotos={} imagesFound={}",
                context.trip().getId(), albumSource, albumStyle, context.stops().size(), context.photos().size(), countImages(context, albumSource));
            return new AlbumGenerationResult(
                fallbackAlbum(context, "GOLA đã tạo album từ dữ liệu chuyến đi vì Gemini chưa được cấu hình.", albumSource, albumStyle),
                "FALLBACK"
            );
        }
        try {
            if (shouldAnalyzePhotos(albumSource)) {
                List<TripMemoryPhoto> analyzablePhotos = analyzablePhotos(context.photos());
                List<GeminiClient.InlineImagePart> imageParts = inlineImageParts(analyzablePhotos);
                if (!imageParts.isEmpty()) {
                    log.info("Gemini photo analysis enabled for album tripId={} imageParts={} uploadedPhotos={}",
                        context.trip().getId(), imageParts.size(), context.photos().size());
                    return new AlbumGenerationResult(
                        parseAlbumResult(geminiClient.generateContentWithImages(buildPhotoAwareAlbumPrompt(context, albumStyle, analyzablePhotos), imageParts)),
                        "GEMINI_PHOTO_ANALYSIS"
                    );
                }
                log.info("Gemini photo analysis enabled but no local image bytes could be resolved tripId={}", context.trip().getId());
            }
            return new AlbumGenerationResult(parseAlbumResult(geminiClient.generateContent(buildAlbumPrompt(context, albumSource, albumStyle))), "GEMINI");
        } catch (Exception e) {
            log.warn("Gemini album generation failed; using deterministic fallback tripId={} albumSource={} style={} reason={}",
                context.trip().getId(), albumSource, albumStyle, safeError(e));
            return new AlbumGenerationResult(
                fallbackAlbum(context, "GOLA đã tạo album từ dữ liệu đã lưu vì AI tạm thời không khả dụng.", albumSource, albumStyle),
                "FALLBACK"
            );
        }
    }

    private String buildAlbumPrompt(TripContext context, String albumSource, String albumStyle) {
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            return buildPersonalAlbumPrompt(context, albumStyle);
        }
        return buildItineraryAlbumPrompt(context, albumStyle);
    }

    private boolean shouldAnalyzePhotos(String albumSource) {
        return SOURCE_PERSONAL_PHOTOS.equals(albumSource)
            && properties.getGemini() != null
            && properties.getGemini().isAnalyzePhotos();
    }

    private List<TripMemoryPhoto> analyzablePhotos(List<TripMemoryPhoto> photos) {
        return photos.stream()
            .filter(photo -> resolveLocalImagePath(photo.getImageUrl()).isPresent())
            .limit(8)
            .toList();
    }

    private List<GeminiClient.InlineImagePart> inlineImageParts(List<TripMemoryPhoto> photos) {
        List<GeminiClient.InlineImagePart> parts = new ArrayList<>();
        for (TripMemoryPhoto photo : photos) {
            resolveLocalImagePath(photo.getImageUrl()).ifPresent(path -> {
                try {
                    String mimeType = Optional.ofNullable(Files.probeContentType(path))
                        .filter(value -> value.startsWith("image/"))
                        .orElse("image/jpeg");
                    String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
                    parts.add(new GeminiClient.InlineImagePart(mimeType, base64));
                } catch (Exception e) {
                    log.debug("Skipping photo bytes for Gemini analysis photoId={} reason={}", photo.getId(), e.getMessage());
                }
            });
        }
        return parts;
    }

    private String buildPhotoAwareAlbumPrompt(TripContext context, String albumStyle, List<TripMemoryPhoto> analyzedPhotos) {
        Trip trip = context.trip();
        Map<UUID, TripStop> stopsById = context.stops().stream()
            .collect(Collectors.toMap(TripStop::getId, stop -> stop, (a, b) -> a));
        String photos = analyzedPhotos.stream()
            .map(photo -> {
                TripStop stop = photo.getTripStopId() != null ? stopsById.get(photo.getTripStopId()) : null;
                return Map.of(
                    "photoSlot", analyzedPhotos.indexOf(photo) + 1,
                    "memoryPhotoId", photo.getId().toString(),
                    "dayIndex", photo.getDayIndex() != null ? photo.getDayIndex() : 1,
                    "sortOrder", photo.getSortOrder(),
                    "linkedStopName", stop != null ? safe(stop.getName()) : "",
                    "captionNote", safe(photo.getCaptionNote()),
                    "originalFileName", safe(photo.getOriginalFileName()))
                    .toString();
            })
            .collect(Collectors.joining("\n"));
        String notes = context.notes().stream().limit(8).map(TripNote::getContent).collect(Collectors.joining("\n"));

        return """
            You are creating a photo-aware PERSONAL_PHOTOS AI Travel Album for GOLA.
            The uploaded images are attached as inline image parts in the same order as photoSlot below.
            Analyze the photos visually, but follow these safety rules:
            - Do not identify real people or infer sensitive traits.
            - Use generic descriptions such as "nhóm bạn", "cảnh hoàng hôn", "biển", "đường phố", "bữa ăn".
            - User-facing copy must be Vietnamese UTF-8.
            - Do not invent photos. Keep every memoryPhotoId exactly as provided.

            Selected style: %s.
            Style behavior:
            - Cinematic: dramatic title, film-still layout, cinematic captions, dark gradient overlays.
            - Vintage Film: warm analog tone, date-stamp feeling, nostalgic captions.
            - Modern Clean: magazine grid, clean typography, concise captions.
            - Adventure: scrapbook energy, route labels, energetic captions.
            - Travel Postcard: postcard warmth, stamp/card feel, travel-note captions.

            Return JSON only. No markdown fences.
            Schema:
            {
              "albumTitle": "...",
              "subtitle": "...",
              "introStory": "...",
              "coverPhotoId": "memoryPhotoId chosen as cover",
              "mood": "CHILL|ADVENTURE|FOODIE|FRIENDS|FAMILY|ROMANTIC|DISCOVERY",
              "colorPalette": ["teal", "sand", "sunset orange"],
              "designDirection": "...",
              "coverCaption": "...",
              "highlightMoments": ["..."],
              "travelQuotes": ["..."],
              "shareCaption": "...",
              "hashtags": ["#GOLA", "#Travel"],
              "moments": [{
                "dayIndex": 1,
                "title": "...",
                "summary": "...",
                "layoutType": "cover-spread|collage|film-strip|postcard|scrapbook",
                "photoCaptions": [{
                  "memoryPhotoId": "...",
                  "title": "...",
                  "caption": "...",
                  "shortCaption": "...",
                  "visualSummary": "...",
                  "microStory": "...",
                  "stickerText": "...",
                  "cropFocus": "center|top|bottom|left|right",
                  "filterSuggestion": "cinematic|warm-film|clean|adventure|postcard",
                  "sceneType": "beach|city|food|hotel|road|nature|group|detail|other",
                  "mood": "...",
                  "mainColors": ["..."],
                  "qualityScore": 0.0,
                  "isCoverCandidate": true,
                  "tags": ["..."],
                  "emotionalTone": "...",
                  "locationHint": "..."
                }]
              }]
            }
            Trip: %s
            Route: %s to %s
            Date range: %s
            Memory summary: %s
            Trip notes:
            %s
            Uploaded photo slots:
            %s
            """.formatted(
            albumStyle,
            safe(trip.getTitle()),
            safe(trip.getOrigin()),
            safe(trip.getDestination()),
            dateRange(trip),
            safe(context.memory().getSummary()),
            notes,
            photos
        );
    }

    private String buildPersonalAlbumPrompt(TripContext context, String albumStyle) {
        Trip trip = context.trip();
        Map<UUID, TripStop> stopsById = context.stops().stream()
            .collect(Collectors.toMap(TripStop::getId, stop -> stop, (a, b) -> a));
        String photos = context.photos().stream()
            .map(photo -> {
                TripStop stop = photo.getTripStopId() != null ? stopsById.get(photo.getTripStopId()) : null;
                return Map.of(
                    "memoryPhotoId", photo.getId().toString(),
                    "dayIndex", photo.getDayIndex() != null ? photo.getDayIndex() : 1,
                    "sortOrder", photo.getSortOrder(),
                    "linkedStopName", stop != null ? safe(stop.getName()) : "",
                    "captionNote", safe(photo.getCaptionNote()),
                    "originalFileName", safe(photo.getOriginalFileName()))
                    .toString();
            })
            .collect(Collectors.joining("\n"));
        String quests = questTitles(context).stream().collect(Collectors.joining(", "));
        String notes = context.notes().stream().limit(8).map(TripNote::getContent).collect(Collectors.joining("\n"));

        return """
            You are creating a PERSONAL_PHOTOS post-trip AI Travel Album for GOLA.
            Use uploaded photo metadata as the main source. Do not invent extra photos or replace them with place images.
            Selected style: %s.
            Style rules:
            - Cinematic: movie-like, dramatic, each photo feels like a scene.
            - Vintage Film: nostalgic, warm, analog-memory tone.
            - Modern Clean: minimal, elegant, concise.
            - Adventure: energetic, bold, exploring tone.
            - Travel Postcard: warm destination-letter tone.
            Write all user-facing copy in clean Vietnamese UTF-8. Do not use repeated generic captions.
            Do not send or assume image bytes. Use metadata, trip title, destination, dates, linked stops, photo notes, quests, and trip notes only.
            Return JSON only. Do not include markdown fences.
            Schema:
            {
              "albumTitle": "...",
              "subtitle": "...",
              "coverPhotoId": "memoryPhotoId selected as cover",
              "mood": "CHILL|ADVENTURE|FOODIE|FRIENDS|FAMILY|ROMANTIC|DISCOVERY",
              "colorPalette": ["teal", "sunset orange"],
              "designDirection": "...",
              "coverCaption": "...",
              "introStory": "...",
              "highlightMoments": ["..."],
              "travelQuotes": ["..."],
              "shareCaption": "...",
              "hashtags": ["#GOLA", "#Travel"],
              "daySections": [{
                "dayIndex": 1,
                "title": "...",
                "summary": "...",
                "photoCaptions": [{
                  "memoryPhotoId": "...",
                  "tripStopId": "...",
                  "stopName": "...",
                  "title": "...",
                  "caption": "...",
                  "shortCaption": "...",
                  "visualSummary": "...",
                  "microStory": "...",
                  "stickerText": "...",
                  "cropFocus": "center|top|bottom|left|right",
                  "filterSuggestion": "cinematic|warm-film|clean|adventure|postcard",
                  "sceneType": "beach|city|food|hotel|road|nature|group|detail|other",
                  "mainColors": ["..."],
                  "qualityScore": 0.0,
                  "isCoverCandidate": true,
                  "tags": ["..."],
                  "emotionalTone": "...",
                  "locationHint": "..."
                }]
              }]
            }
            Keep every memoryPhotoId exactly as provided. Each caption must be specific to the photo metadata or linked stop.
            If no image bytes are available, infer visualSummary/cropFocus from filename, captionNote, linked stop, and day context; do not pretend to identify people.
            If a photo has captionNote, weave it naturally into that photo's caption.
            Trip: %s
            Route: %s to %s
            Date range: %s
            Completed quests: %d %s
            Expense summary: %s
            Memory summary: %s
            Trip notes:
            %s
            Uploaded photos:
            %s
            """.formatted(
            albumStyle,
            safe(trip.getTitle()),
            safe(trip.getOrigin()),
            safe(trip.getDestination()),
            dateRange(trip),
            context.completedQuestProgress().size(),
            quests,
            expenseSummary(context.expenses()),
            safe(context.memory().getSummary()),
            notes,
            photos
        );
    }

    private String buildItineraryAlbumPrompt(TripContext context, String albumStyle) {
        Trip trip = context.trip();
        String stops = context.stops().stream()
            .map(stop -> Map.of(
                "tripStopId", stop.getId().toString(),
                "name", safe(stop.getName()),
                "category", safe(stop.getCategory()),
                "address", safe(stop.getPlaceAddress()),
                "imageUrl", safe(stop.getImageUrl()),
                "orderIdx", stop.getOrderIdx()))
            .map(Object::toString)
            .collect(Collectors.joining("\n"));
        String quests = questTitles(context).stream().collect(Collectors.joining(", "));
        String notes = context.notes().stream().limit(8).map(TripNote::getContent).collect(Collectors.joining("\n"));

        return """
            You are creating a post-trip AI Travel Album for GOLA.
            Selected style: %s.
            Style rules:
            - Cinematic: movie-like, dramatic, each stop feels like a scene.
            - Vintage Film: nostalgic, warm, analog-memory tone.
            - Modern Clean: minimal, elegant, concise.
            - Adventure: energetic, bold, exploring tone.
            - Travel Postcard: warm destination-letter tone.
            Use trip stops as the source. Write all user-facing copy in clean Vietnamese UTF-8.
            Do not use repeated generic captions. Use stop name, category, destination, date range, notes, quests, and expenses to create variety.
            Return JSON only. Do not include markdown fences.
            Schema:
            {
              "albumTitle": "...",
              "subtitle": "...",
              "mood": "CHILL|ADVENTURE|FOODIE|FRIENDS|FAMILY|ROMANTIC|DISCOVERY",
              "colorPalette": ["teal", "sand"],
              "designDirection": "...",
              "coverCaption": "...",
              "introStory": "...",
              "highlightMoments": ["..."],
              "travelQuotes": ["..."],
              "shareCaption": "...",
              "hashtags": ["#GOLA", "#Travel"],
              "daySections": [{
                "dayIndex": 1,
                "title": "...",
                "summary": "...",
                "photoCaptions": [{
                  "tripStopId": "...",
                  "stopName": "...",
                  "title": "...",
                  "caption": "...",
                  "shortCaption": "...",
                  "visualSummary": "...",
                  "microStory": "...",
                  "stickerText": "...",
                  "cropFocus": "center|top|bottom|left|right",
                  "filterSuggestion": "cinematic|warm-film|clean|adventure|postcard",
                  "sceneType": "beach|city|food|hotel|road|nature|group|detail|other",
                  "mainColors": ["..."],
                  "tags": ["..."],
                  "emotionalTone": "...",
                  "locationHint": "...",
                  "imageUrl": "..."
                }]
              }]
            }
            Keep tripStopId exactly as provided. Captions should be short, specific, and style-aware.
            Trip: %s
            Route: %s to %s
            Date range: %s
            Completed quests: %d %s
            Expense summary: %s
            Memory summary: %s
            Trip notes:
            %s
            Ordered stops:
            %s
            """.formatted(
            albumStyle,
            safe(trip.getTitle()),
            safe(trip.getOrigin()),
            safe(trip.getDestination()),
            dateRange(trip),
            context.completedQuestProgress().size(),
            quests,
            expenseSummary(context.expenses()),
            safe(context.memory().getSummary()),
            notes,
            stops
        );
    }

    private void applyAlbumResult(Album album, AlbumAiResult result, TripContext context, String albumSource) {
        album.setTitle(firstText(result.getTitle(), result.getAlbumTitle(), album.getTitle(), "AI Travel Album"));
        album.setSummary(firstText(result.getIntroStory(), result.getSummary(), result.getSubtitle(), "Album kỷ niệm được tạo từ dữ liệu chuyến đi."));
        album.setMood(firstText(result.getMood(), "DISCOVERY"));
        album.setCoverCaption(result.getCoverCaption());
        String selectedCover = selectedCoverUrl(context, albumSource, result);
        if (hasText(selectedCover)) {
            album.setCoverUrl(selectedCover);
        }
        album.setShareCaption(result.getShareCaption());
        album.setHashtags(cleanHashtags(result.getHashtags()));
        album.setStatus(READY);
        album.setErrorMessage(null);
        album.setGeneratedAt(Instant.now());
    }

    private void saveAlbumItems(Album album, AlbumAiResult result, TripContext context, String albumSource, String generationMode, String albumStyle) {
        albumMediaRepo.deleteByAlbumId(album.getId());
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            savePersonalAlbumItems(album, result, context, generationMode, albumStyle);
            return;
        }
        saveItineraryAlbumItems(album, result, context, generationMode, albumStyle);
    }

    private String selectedCoverUrl(TripContext context, String albumSource, AlbumAiResult result) {
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            String coverPhotoId = result != null ? result.getCoverPhotoId() : null;
            if (hasText(coverPhotoId)) {
                for (TripMemoryPhoto photo : context.photos()) {
                    if (photo.getId().toString().equals(coverPhotoId)) {
                        return photo.getImageUrl();
                    }
                }
            }
            if (result != null && result.getDays() != null) {
                for (AlbumAiDay day : result.getDays()) {
                    if (day.getItems() == null) continue;
                    for (AlbumAiItem item : day.getItems()) {
                        if (Boolean.TRUE.equals(item.getCoverCandidate())) {
                            String photoId = firstText(item.getMemoryPhotoId(), item.getPhotoId());
                            for (TripMemoryPhoto photo : context.photos()) {
                                if (photo.getId().toString().equals(photoId)) {
                                    return photo.getImageUrl();
                                }
                            }
                        }
                    }
                }
            }
        }
        return firstImage(context, albumSource);
    }

    private void savePersonalAlbumItems(Album album, AlbumAiResult result, TripContext context, String generationMode, String albumStyle) {
        Map<String, TripStop> stopsById = context.stops().stream()
            .collect(Collectors.toMap(stop -> stop.getId().toString(), stop -> stop, (a, b) -> a));
        Map<String, AlbumAiItem> aiItemsByPhotoId = new LinkedHashMap<>();
        Map<String, AlbumAiDay> aiDaysByPhotoId = new LinkedHashMap<>();
        if (result.getDays() != null) {
            for (AlbumAiDay day : result.getDays()) {
                if (day.getItems() == null) continue;
                for (AlbumAiItem item : day.getItems()) {
                    String photoId = firstText(item.getMemoryPhotoId(), item.getPhotoId());
                    if (hasText(photoId)) {
                        aiItemsByPhotoId.putIfAbsent(photoId, item);
                        aiDaysByPhotoId.putIfAbsent(photoId, day);
                    }
                }
            }
        }

        int order = 0;
        for (TripMemoryPhoto photo : context.photos()) {
            String photoId = photo.getId().toString();
            AlbumAiItem item = aiItemsByPhotoId.get(photoId);
            AlbumAiDay day = aiDaysByPhotoId.get(photoId);
            TripStop stop = photo.getTripStopId() != null ? stopsById.get(photo.getTripStopId().toString()) : null;
            int dayIndex = photo.getDayIndex() != null && photo.getDayIndex() > 0
                ? photo.getDayIndex()
                : (day != null && day.getDayIndex() > 0 ? day.getDayIndex() : 1);
            String stopName = firstText(
                item != null ? item.getStopName() : null,
                stop != null ? stop.getName() : null,
                photo.getOriginalFileName(),
                "Memory photo"
            );
            String caption = firstText(
                item != null ? item.getCaption() : null,
                item != null ? item.getShortCaption() : null,
                photo.getCaptionNote(),
                fallbackPhotoCaption(context, photo, stop, order, albumStyle)
            );
            String highlight = firstText(
                item != null ? item.getEmotionalTone() : null,
                item != null ? item.getHighlight() : null,
                photo.getCaptionNote(),
                stop != null ? stop.getCategory() : null,
                styleTone(albumStyle)
            );
            photo.setAiCaption(caption);
            photoRepo.save(photo);

            albumMediaRepo.save(AlbumMedia.builder()
                .albumId(album.getId())
                .tripStopId(stop != null ? stop.getId() : null)
                .memoryPhotoId(photo.getId())
                .mediaUrl(photo.getImageUrl())
                .caption(caption)
                .highlight(highlight)
                .stopName(stopName)
                .dayIndex(dayIndex)
                .dayTitle(firstText(day != null ? day.getTitle() : null, "Ngày " + dayIndex))
                .daySummary(firstText(day != null ? day.getSummary() : null, "Những khoảnh khắc cá nhân trong ngày " + dayIndex + "."))
                .orderIdx(order++)
                .metadata(albumItemMetadata(
                    SOURCE_PERSONAL_PHOTOS,
                    generationMode,
                    albumStyle,
                    result,
                    day,
                    item,
                    firstText(item != null ? item.getTitle() : null, stopName),
                    firstText(item != null ? item.getShortCaption() : null, caption),
                    highlight,
                    firstText(item != null ? item.getLocationHint() : null, stopName),
                    true,
                    false
                ))
                .build());
        }
    }

    private void saveItineraryAlbumItems(Album album, AlbumAiResult result, TripContext context, String generationMode, String albumStyle) {
        Map<String, TripStop> stopsById = context.stops().stream()
            .collect(Collectors.toMap(stop -> stop.getId().toString(), stop -> stop, (a, b) -> a));

        int order = 0;
        List<AlbumAiDay> days = result.getDays() != null && !result.getDays().isEmpty()
            ? result.getDays()
            : fallbackAlbum(context, null, SOURCE_ITINERARY, albumStyle).getDays();
        for (AlbumAiDay day : days) {
            int dayIndex = day.getDayIndex() > 0 ? day.getDayIndex() : 1;
            List<AlbumAiItem> items = day.getItems() != null ? day.getItems() : List.of();
            for (AlbumAiItem item : items) {
                TripStop stop = item.getTripStopId() != null ? stopsById.get(item.getTripStopId()) : null;
                String imageUrl = firstText(item.getImageUrl(), stop != null ? stop.getImageUrl() : null, placeholderImageFor(stop));
                albumMediaRepo.save(AlbumMedia.builder()
                    .albumId(album.getId())
                    .tripStopId(stop != null ? stop.getId() : null)
                    .mediaUrl(imageUrl)
                    .caption(firstText(item.getCaption(), item.getShortCaption(), stop != null ? fallbackStopCaption(context, stop, order, albumStyle) : "Một điểm nhấn đáng nhớ của chuyến đi."))
                    .highlight(firstText(item.getEmotionalTone(), item.getHighlight(), stop != null ? stop.getName() : "Điểm nhấn"))
                    .stopName(firstText(item.getStopName(), stop != null ? stop.getName() : null))
                    .dayIndex(dayIndex)
                    .dayTitle(firstText(day.getTitle(), "Ngày " + dayIndex))
                    .daySummary(firstText(day.getSummary(), "Một ngày đáng nhớ trong chuyến đi."))
                    .orderIdx(order++)
                    .metadata(albumItemMetadata(
                        SOURCE_ITINERARY,
                        generationMode,
                        albumStyle,
                        result,
                        day,
                        item,
                        firstText(item.getTitle(), item.getStopName(), stop != null ? stop.getName() : null, "Khoảnh khắc"),
                        firstText(item.getShortCaption(), item.getCaption(), stop != null ? stop.getName() : null, "Điểm nhấn"),
                        firstText(item.getEmotionalTone(), item.getHighlight(), styleTone(albumStyle)),
                        firstText(item.getLocationHint(), item.getStopName(), stop != null ? stop.getPlaceAddress() : null, "Chuyến đi"),
                        false,
                        true
                    ))
                    .build());
            }
        }
    }

    private Map<String, Object> albumItemMetadata(
            String source,
            String generationMode,
            String albumStyle,
            AlbumAiResult result,
            AlbumAiDay day,
            AlbumAiItem item,
            String title,
            String shortCaption,
            String emotionalTone,
            String locationHint,
            boolean uploadedPhoto,
            boolean imageOptional) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "source", source);
        put(metadata, "generationMode", generationMode);
        put(metadata, "style", albumStyle);
        put(metadata, "subtitle", result != null ? result.getSubtitle() : null);
        put(metadata, "designDirection", result != null ? result.getDesignDirection() : null);
        put(metadata, "travelQuote", result != null && result.getTravelQuotes() != null && !result.getTravelQuotes().isEmpty() ? result.getTravelQuotes().get(0) : null);
        put(metadata, "layoutType", firstText(day != null ? day.getLayoutType() : null, layoutTypeForStyle(albumStyle)));
        put(metadata, "title", title);
        put(metadata, "shortCaption", shortCaption);
        put(metadata, "emotionalTone", emotionalTone);
        put(metadata, "locationHint", locationHint);
        put(metadata, "visualSummary", item != null ? item.getVisualSummary() : null);
        put(metadata, "microStory", item != null ? item.getMicroStory() : null);
        put(metadata, "stickerText", item != null ? item.getStickerText() : null);
        put(metadata, "cropFocus", normalizeCropFocus(item != null ? item.getCropFocus() : null));
        put(metadata, "filterSuggestion", firstText(item != null ? item.getFilterSuggestion() : null, filterSuggestionForStyle(albumStyle)));
        put(metadata, "sceneType", item != null ? item.getSceneType() : null);
        put(metadata, "mood", item != null ? item.getMood() : null);
        put(metadata, "mainColors", firstList(item != null ? item.getMainColors() : null, result != null ? result.getColorPalette() : null));
        put(metadata, "qualityScore", item != null ? item.getQualityScore() : null);
        put(metadata, "coverCandidate", item != null ? item.getCoverCandidate() : null);
        put(metadata, "tags", item != null ? item.getTags() : null);
        metadata.put("uploadedPhoto", uploadedPhoto);
        metadata.put("imageOptional", imageOptional);
        return metadata;
    }

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        if (value instanceof List<?> list && list.isEmpty()) return;
        metadata.put(key, value);
    }

    private List<String> firstList(List<String> primary, List<String> fallback) {
        if (primary != null && !primary.isEmpty()) return primary;
        if (fallback != null && !fallback.isEmpty()) return fallback;
        return List.of();
    }

    private String normalizeCropFocus(String value) {
        if (!hasText(value)) return "center";
        String normalized = value.toLowerCase(Locale.ROOT);
        if (List.of("top", "bottom", "left", "right", "center").contains(normalized)) {
            return normalized;
        }
        return "center";
    }

    private String filterSuggestionForStyle(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "warm-film";
            case "Modern Clean" -> "clean";
            case "Adventure" -> "adventure";
            case "Travel Postcard" -> "postcard";
            default -> "cinematic";
        };
    }

    private String layoutTypeForStyle(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "film-strip";
            case "Modern Clean" -> "magazine-grid";
            case "Adventure" -> "scrapbook";
            case "Travel Postcard" -> "postcard";
            default -> "cover-spread";
        };
    }

    private Path writeTravelBookPdf(TripContext context, Album album, List<AlbumMedia> media) throws IOException {
        Path outputDir = Path.of("uploads", "travel-books").toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        Path pdfPath = outputDir.resolve(context.trip().getId() + "-travel-book.pdf");

        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = loadPdfFonts(document);
            PdfWriter writer = new PdfWriter(document, fonts.titleFont(), fonts.bodyFont(), fonts.unicode());

            String bookTitle = album != null && SOURCE_PERSONAL_PHOTOS.equals(album.getAlbumSource())
                ? "GOLA Personal Photo Travel Book"
                : album != null && SOURCE_ITINERARY.equals(album.getAlbumSource())
                    ? "GOLA Itinerary Travel Book"
                    : "GOLA Trip Summary";
            writer.page(bookTitle);
            writer.heading(context.trip().getTitle());
            writer.line(firstText(context.trip().getDestination(), context.trip().getOrigin(), "Trip"));
            writer.line(dateRange(context.trip()));
            writer.spacer();
            resolveLocalImagePath(firstText(album != null ? album.getCoverUrl() : null, firstImage(context, SOURCE_PERSONAL_PHOTOS), firstImage(context, SOURCE_ITINERARY)))
                .ifPresent(writer::image);
            writer.spacer();
            writer.paragraph(firstText(album != null ? album.getSummary() : null, context.memory().getSummary()));
            writer.line("Made with GOLA");
            writer.footer();

            writer.page("Trip Overview");
            writer.line("Stops: " + context.stops().size());
            writer.line("Completed quests: " + context.completedQuestProgress().size());
            writer.line("Expenses: " + expenseSummary(context.expenses()));
            writer.spacer();
            writer.paragraph(firstText(context.memory().getSummary(), "Completed trip saved to GOLA memories."));
            writer.footer();

            Map<Integer, List<AlbumMedia>> mediaByDay = media.stream()
                .collect(Collectors.groupingBy(AlbumMedia::getDayIndex, LinkedHashMap::new, Collectors.toList()));
            if (!mediaByDay.isEmpty()) {
                for (Map.Entry<Integer, List<AlbumMedia>> day : mediaByDay.entrySet()) {
                    writer.page(firstText(day.getValue().get(0).getDayTitle(), "Day " + day.getKey()));
                    writer.paragraph(firstText(day.getValue().get(0).getDaySummary(), "Trip highlights"));
                    for (AlbumMedia item : day.getValue()) {
                        writer.bullet(firstText(item.getStopName(), "Stop") + ": " + firstText(item.getCaption(), item.getHighlight(), ""));
                        resolveLocalImagePath(item.getMediaUrl()).ifPresent(writer::image);
                    }
                    writer.footer();
                }
            } else {
                writer.page("Day-by-day Stops");
                for (TripStop stop : context.stops()) {
                    writer.bullet(firstText(stop.getName(), "Stop") + optionalSuffix(stop.getCategory()));
                    if (hasText(stop.getNotes())) writer.small(stop.getNotes());
                }
                writer.footer();
            }

            writer.page("Quest Highlights");
            List<String> questTitles = questTitles(context);
            if (questTitles.isEmpty()) {
                writer.line("No completed quests recorded for this trip.");
            } else {
                questTitles.forEach(writer::bullet);
            }
            writer.footer();

            writer.page("Share");
            writer.paragraph(firstText(album != null ? album.getShareCaption() : null, context.memory().getSummary()));
            if (album != null && album.getHashtags() != null && album.getHashtags().length > 0) {
                writer.line(String.join(" ", album.getHashtags()));
            }
            writer.footer();

            writer.close();
            document.save(pdfPath.toFile());
        }
        if (!Files.isRegularFile(pdfPath) || Files.size(pdfPath) == 0) {
            throw new IOException("Travel book PDF was not written: " + pdfPath);
        }
        return pdfPath;
    }

    private PdfFonts loadPdfFonts(PDDocument document) {
        PDFont titleFont = loadUnicodeFont(document, true).orElseGet(() -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        PDFont bodyFont = loadUnicodeFont(document, false).orElseGet(() -> new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        boolean unicode = titleFont instanceof PDType0Font && bodyFont instanceof PDType0Font;
        if (!unicode) {
            log.warn("Travel book PDF using Standard14 fallback fonts; Vietnamese accents may be normalized.");
        }
        return new PdfFonts(titleFont, bodyFont, unicode);
    }

    private Optional<PDFont> loadUnicodeFont(PDDocument document, boolean bold) {
        for (Path candidate : unicodeFontCandidates(bold)) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                return Optional.of(PDType0Font.load(document, candidate.toFile()));
            } catch (IOException e) {
                log.debug("Could not load PDF font {}: {}", candidate, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private List<Path> unicodeFontCandidates(boolean bold) {
        List<Path> candidates = new ArrayList<>();
        List<String> windowsRoots = List.of(System.getenv("WINDIR"), System.getenv("SystemRoot")).stream()
            .filter(this::hasText)
            .distinct()
            .toList();
        for (String root : windowsRoots) {
            Path fonts = Path.of(root, "Fonts");
            if (bold) {
                candidates.add(fonts.resolve("arialbd.ttf"));
                candidates.add(fonts.resolve("segoeuib.ttf"));
            } else {
                candidates.add(fonts.resolve("arial.ttf"));
                candidates.add(fonts.resolve("segoeui.ttf"));
            }
            candidates.add(fonts.resolve("tahoma.ttf"));
        }
        if (bold) {
            candidates.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
        }
        candidates.add(Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        candidates.add(Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"));
        return candidates;
    }

    private ReelStoryboardResponse buildStoryboard(TripContext context) {
        List<ReelStoryboardResponse.ReelSlideResponse> slides = new ArrayList<>();
        Album album = selectAlbumForArtifacts(context);
        List<AlbumMedia> albumMedia = album != null
            ? albumMediaRepo.findByAlbumIdOrderByDayIndexAscOrderIdxAsc(album.getId())
            : List.of();

        slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
            .order(1)
            .type("COVER")
            .title(context.trip().getTitle())
            .caption(firstText(album != null ? album.getSummary() : null, context.memory().getSummary(), "Trip memory by GOLA"))
            .imageUrl(!albumMedia.isEmpty() ? albumMedia.get(0).getMediaUrl() : firstImage(context, SOURCE_ITINERARY))
            .durationSeconds(4)
            .build());

        int order = 2;
        if (!albumMedia.isEmpty()) {
            String slideType = SOURCE_PERSONAL_PHOTOS.equals(album.getAlbumSource()) ? "PHOTO" : "STOP";
            for (AlbumMedia item : albumMedia.stream().limit(4).toList()) {
                slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
                    .order(order++)
                    .type(slideType)
                    .title(firstText(item.getStopName(), "Memory photo"))
                    .caption(firstText(item.getCaption(), item.getHighlight(), "A saved trip moment"))
                    .imageUrl(item.getMediaUrl())
                    .durationSeconds(4)
                    .build());
            }
        } else {
            for (TripStop stop : context.stops().stream().limit(3).toList()) {
                slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
                    .order(order++)
                    .type("STOP")
                    .title(firstText(stop.getName(), "Trip stop"))
                    .caption(firstText(stop.getNotes(), stop.getCategory(), "A memorable stop"))
                    .imageUrl(stop.getImageUrl())
                    .durationSeconds(4)
                    .build());
            }
        }

        if (!context.completedQuestProgress().isEmpty()) {
            slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
                .order(order++)
                .type("QUEST")
                .title("Quest highlights")
                .caption(context.completedQuestProgress().size() + " quest(s) completed")
                .durationSeconds(4)
                .build());
        }

        slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
            .order(order)
            .type("SUMMARY")
            .title("Generated by GOLA")
            .caption("Storyboard preview. Video export will arrive in a later version.")
            .durationSeconds(4)
            .build());

        return ReelStoryboardResponse.builder()
            .status(READY)
            .title(context.trip().getTitle() + " Reel Preview")
            .durationSeconds(slides.stream().mapToInt(ReelStoryboardResponse.ReelSlideResponse::getDurationSeconds).sum())
            .slides(slides)
            .generatedAt(Instant.now())
            .build();
    }

    private AlbumAiResult fallbackAlbum(TripContext context, String summaryOverride, String albumSource, String albumStyle) {
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            return fallbackPersonalPhotoAlbum(context, summaryOverride, albumStyle);
        }
        return fallbackItineraryAlbum(context, summaryOverride, albumStyle);
    }

    private AlbumAiResult fallbackPersonalPhotoAlbum(TripContext context, String summaryOverride, String albumStyle) {
        AlbumAiResult result = new AlbumAiResult();
        String destination = firstText(context.trip().getDestination(), context.trip().getTitle(), "chuyến đi này");
        result.setTitle(firstText(context.trip().getTitle(), albumStyle + " Album"));
        result.setSubtitle(albumStyle + " Album");
        result.setSummary(firstText(summaryOverride, context.memory().getSummary(), "Album kỷ niệm được tạo từ ảnh cá nhân tại " + destination + "."));
        result.setIntroStory(firstText(summaryOverride, context.memory().getSummary(), styleIntro(albumStyle, destination)));
        result.setMood(styleMood(albumStyle));
        result.setColorPalette(defaultPalette(albumStyle));
        result.setDesignDirection(styleDesignDirection(albumStyle));
        context.photos().stream().findFirst().ifPresent(photo -> result.setCoverPhotoId(photo.getId().toString()));
        result.setCoverCaption(styleCoverCaption(albumStyle, destination));
        result.setShareCaption("Mình vừa tạo album kỷ niệm " + firstText(context.trip().getTitle(), "cho chuyến đi") + " cùng GOLA.");
        result.setHashtags(List.of("#GOLA", "#TripMemory", "#PhotoAlbum", "#" + hashtagSafe(destination)));
        result.setHighlightMoments(List.of("Ảnh cá nhân", "Lịch trình đã đi", "Ký ức sau chuyến đi"));
        result.setTravelQuotes(List.of(styleQuote(albumStyle)));

        Map<UUID, TripStop> stopsById = context.stops().stream()
            .collect(Collectors.toMap(TripStop::getId, stop -> stop, (a, b) -> a));
        Map<Integer, List<TripMemoryPhoto>> photosByDay = context.photos().stream()
            .collect(Collectors.groupingBy(
                photo -> photo.getDayIndex() != null && photo.getDayIndex() > 0 ? photo.getDayIndex() : 1,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<AlbumAiDay> days = new ArrayList<>();
        for (Map.Entry<Integer, List<TripMemoryPhoto>> entry : photosByDay.entrySet()) {
            int dayIndex = entry.getKey();
            AlbumAiDay day = new AlbumAiDay();
            day.setDayIndex(dayIndex);
            day.setTitle("Ngày " + dayIndex);
            day.setSummary("Những khung hình cá nhân đáng nhớ trong ngày " + dayIndex + ".");
            day.setLayoutType(layoutTypeForStyle(albumStyle));
            List<AlbumAiItem> items = entry.getValue().stream()
                .map(photo -> {
                    TripStop stop = photo.getTripStopId() != null ? stopsById.get(photo.getTripStopId()) : null;
                    int photoIndex = entry.getValue().indexOf(photo);
                    AlbumAiItem item = new AlbumAiItem();
                    item.setMemoryPhotoId(photo.getId().toString());
                    item.setTripStopId(photo.getTripStopId() != null ? photo.getTripStopId().toString() : null);
                    item.setStopName(firstText(stop != null ? stop.getName() : null, photo.getOriginalFileName(), "Ảnh kỷ niệm"));
                    item.setTitle(firstText(stop != null ? stop.getName() : null, "Khoảnh khắc ngày " + dayIndex));
                    item.setCaption(firstText(
                        photo.getCaptionNote(),
                        fallbackPhotoCaption(context, photo, stop, photoIndex, albumStyle)
                    ));
                    item.setShortCaption(shortFallbackCaption(stop, photoIndex, albumStyle));
                    item.setVisualSummary(firstText(stop != null ? stop.getName() : null, photo.getOriginalFileName(), "Khoảnh khắc cá nhân"));
                    item.setMicroStory("GOLA AI ghép khoảnh khắc này với nhịp của chuyến đi để tạo một trang album riêng.");
                    item.setStickerText(photoIndex == 0 ? "AI pick" : "Memory");
                    item.setCropFocus("center");
                    item.setFilterSuggestion(filterSuggestionForStyle(albumStyle));
                    item.setSceneType(sceneTypeFor(stop));
                    item.setMood(styleTone(albumStyle));
                    item.setMainColors(defaultPalette(albumStyle));
                    item.setQualityScore(photoIndex == 0 ? 0.92 : 0.78);
                    item.setCoverCandidate(photoIndex == 0);
                    item.setTags(List.of(styleTone(albumStyle), firstText(stop != null ? stop.getCategory() : null, "memory")));
                    item.setEmotionalTone(styleTone(albumStyle));
                    item.setLocationHint(firstText(stop != null ? stop.getName() : null, destination));
                    item.setHighlight(firstText(stop != null ? stop.getCategory() : null, styleTone(albumStyle)));
                    item.setImageUrl(photo.getImageUrl());
                    return item;
                })
                .toList();
            day.setItems(items);
            days.add(day);
        }
        if (days.isEmpty()) {
            AlbumAiDay day = new AlbumAiDay();
            day.setDayIndex(1);
            day.setTitle("Ảnh cá nhân");
            day.setSummary("Hãy upload ảnh để tạo album cá nhân.");
            day.setItems(List.of());
            days.add(day);
        }
        result.setDays(days);
        return result;
    }

    private AlbumAiResult fallbackItineraryAlbum(TripContext context, String summaryOverride, String albumStyle) {
        AlbumAiResult result = new AlbumAiResult();
        String destination = firstText(context.trip().getDestination(), context.trip().getTitle(), "chuyến đi này");
        result.setTitle(firstText(context.trip().getTitle(), albumStyle + " Travel Album"));
        result.setSubtitle(albumStyle + " Album");
        result.setSummary(firstText(summaryOverride, context.memory().getSummary(), "Album được dựng từ lịch trình tại " + destination + "."));
        result.setIntroStory(firstText(summaryOverride, context.memory().getSummary(), styleIntro(albumStyle, destination)));
        result.setMood(styleMood(albumStyle));
        result.setColorPalette(defaultPalette(albumStyle));
        result.setDesignDirection(styleDesignDirection(albumStyle));
        result.setCoverCaption(styleCoverCaption(albumStyle, destination));
        result.setShareCaption("Mình vừa hoàn thành chuyến đi " + firstText(context.trip().getTitle(), "của mình") + " cùng GOLA.");
        result.setHashtags(List.of("#GOLA", "#Travel", "#TripMemory", "#" + hashtagSafe(destination)));
        result.setHighlightMoments(List.of("Lịch trình đã lưu", "Điểm dừng nổi bật", "Ký ức sau chuyến đi"));
        result.setTravelQuotes(List.of(styleQuote(albumStyle)));

        List<AlbumAiDay> days = new ArrayList<>();
        int dayIndex = 1;
        List<TripStop> stops = context.stops();
        int chunkSize = Math.max(1, (int) Math.ceil(stops.size() / 3.0));
        for (int i = 0; i < stops.size(); i += chunkSize) {
            final int dayNumber = dayIndex;
            AlbumAiDay day = new AlbumAiDay();
            day.setDayIndex(dayNumber);
            day.setTitle("Ngày " + dayNumber);
            day.setSummary("Những điểm nhấn trong ngày " + dayNumber + ".");
            day.setLayoutType(layoutTypeForStyle(albumStyle));
            List<AlbumAiItem> items = stops.subList(i, Math.min(stops.size(), i + chunkSize)).stream()
                .map(stop -> {
                    int stopIndex = stops.indexOf(stop);
                    AlbumAiItem item = new AlbumAiItem();
                    item.setTripStopId(stop.getId().toString());
                    item.setStopName(stop.getName());
                    item.setTitle(firstText(stop.getName(), "Khoảnh khắc ngày " + dayNumber));
                    item.setCaption(fallbackStopCaption(context, stop, stopIndex, albumStyle));
                    item.setShortCaption(shortFallbackCaption(stop, stopIndex, albumStyle));
                    item.setVisualSummary(firstText(stop.getName(), stop.getPlaceAddress(), "Điểm nhấn chuyến đi"));
                    item.setMicroStory("GOLA AI dùng lịch trình và ảnh địa điểm để dựng khoảnh khắc này thành một trang album.");
                    item.setStickerText(stopIndex == 0 ? "Highlight" : "Moment");
                    item.setCropFocus("center");
                    item.setFilterSuggestion(filterSuggestionForStyle(albumStyle));
                    item.setSceneType(sceneTypeFor(stop));
                    item.setMood(styleTone(albumStyle));
                    item.setMainColors(defaultPalette(albumStyle));
                    item.setQualityScore(stopIndex == 0 ? 0.88 : 0.72);
                    item.setCoverCandidate(stopIndex == 0);
                    item.setTags(List.of(styleTone(albumStyle), firstText(stop.getCategory(), "travel")));
                    item.setEmotionalTone(styleTone(albumStyle));
                    item.setLocationHint(firstText(stop.getPlaceAddress(), stop.getName(), destination));
                    item.setHighlight(firstText(stop.getCategory(), styleTone(albumStyle)));
                    item.setImageUrl(stop.getImageUrl());
                    return item;
                })
                .toList();
            day.setItems(items);
            days.add(day);
            dayIndex++;
        }
        if (days.isEmpty()) {
            AlbumAiDay day = new AlbumAiDay();
            day.setDayIndex(1);
            day.setTitle("Kỷ niệm chuyến đi");
            day.setSummary("Chuyến đi đã được lưu lại trong GOLA.");
            day.setItems(List.of());
            days.add(day);
        }
        result.setDays(days);
        return result;
    }

    private String fallbackPhotoCaption(TripContext context, TripMemoryPhoto photo, TripStop stop, int index, String albumStyle) {
        String destination = firstText(context.trip().getDestination(), context.trip().getTitle(), "chuyến đi");
        String place = firstText(stop != null ? stop.getName() : null, photo != null ? photo.getOriginalFileName() : null, destination);
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Một lát cắt ấm áp ở " + place + ", như khung phim cũ còn giữ lại mùi nắng của " + destination + ".";
            case "Modern Clean" -> "Khoảnh khắc gọn gàng tại " + place + ", đủ nhẹ để nhớ lâu sau chuyến đi.";
            case "Adventure" -> "Dấu mốc #" + (index + 1) + " trên hành trình " + destination + ": dừng lại, nhìn quanh và thấy mình đang thật sự đi xa.";
            case "Travel Postcard" -> "Gửi từ " + place + " - một mảnh ký ức nhỏ nhưng rất riêng của " + destination + ".";
            default -> "Một cảnh nhỏ tại " + place + ", nơi chuyến đi " + destination + " bắt đầu có nhịp phim của riêng mình.";
        };
    }

    private String fallbackStopCaption(TripContext context, TripStop stop, int index, String albumStyle) {
        String destination = firstText(context.trip().getDestination(), context.trip().getTitle(), "chuyến đi");
        String place = firstText(stop != null ? stop.getName() : null, destination);
        String category = stop != null ? firstText(stop.getCategory(), "điểm dừng") : "điểm dừng";
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> place + " hiện lên bằng tông màu hoài niệm, một " + category.toLowerCase(Locale.ROOT) + " đáng giữ lại trong album.";
            case "Modern Clean" -> place + " là điểm nhấn tinh gọn trong lịch trình " + destination + ".";
            case "Adventure" -> "Chặng #" + (index + 1) + " đưa cả nhóm tới " + place + ", thêm năng lượng cho hành trình khám phá.";
            case "Travel Postcard" -> "Một lời chào từ " + place + ", điểm dừng mang màu sắc rất riêng của " + destination + ".";
            default -> place + " trở thành một cảnh đáng nhớ trong bộ phim nhỏ về " + destination + ".";
        };
    }

    private String shortFallbackCaption(TripStop stop, int index, String albumStyle) {
        String place = firstText(stop != null ? stop.getName() : null, "Khoảnh khắc " + (index + 1));
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Màu phim ở " + place;
            case "Modern Clean" -> "Nhẹ nhàng tại " + place;
            case "Adventure" -> "Chạm mốc " + (index + 1);
            case "Travel Postcard" -> "Bưu thiếp từ " + place;
            default -> "Cảnh nhớ tại " + place;
        };
    }

    private String styleIntro(String albumStyle, String destination) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Một album mang màu phim cũ, gom lại những chi tiết ấm áp nhất của " + destination + ".";
            case "Modern Clean" -> "Một bản ghi tối giản về " + destination + ", giữ lại các khoảnh khắc rõ ràng và đáng nhớ nhất.";
            case "Adventure" -> "Một hành trình nhiều năng lượng qua " + destination + ", nơi mỗi điểm dừng là một dấu mốc khám phá.";
            case "Travel Postcard" -> "Một lá thư gửi về từ " + destination + ", kể lại chuyến đi bằng những khung hình thân mật.";
            default -> "Một album điện ảnh nhỏ về " + destination + ", dựng từ ảnh cá nhân và lịch trình đã lưu.";
        };
    }

    private String styleCoverCaption(String albumStyle, String destination) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Những thước phim ấm áp từ " + destination;
            case "Modern Clean" -> "Kỷ niệm tinh gọn tại " + destination;
            case "Adventure" -> "Hành trình khám phá " + destination;
            case "Travel Postcard" -> "Bưu thiếp gửi từ " + destination;
            default -> "Một chuyến đi như thước phim tại " + destination;
        };
    }

    private String styleQuote(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Có những nơi đi qua rồi vẫn còn ánh sáng ở lại trong ảnh.";
            case "Modern Clean" -> "Đi ít nhưng nhớ kỹ từng khoảnh khắc.";
            case "Adventure" -> "Điểm dừng đẹp nhất là nơi mình dám bước tiếp.";
            case "Travel Postcard" -> "Gửi một chút nắng, một chút đường xa, và rất nhiều ký ức.";
            default -> "Mỗi chuyến đi đều xứng đáng có một khung hình mở màn.";
        };
    }

    private String styleMood(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Adventure" -> "ADVENTURE";
            case "Vintage Film" -> "FRIENDS";
            case "Travel Postcard" -> "DISCOVERY";
            case "Modern Clean" -> "CHILL";
            default -> "DISCOVERY";
        };
    }

    private String styleTone(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Hoài niệm";
            case "Modern Clean" -> "Tinh gọn";
            case "Adventure" -> "Năng lượng";
            case "Travel Postcard" -> "Ấm áp";
            default -> "Điện ảnh";
        };
    }

    private List<String> defaultPalette(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> List.of("nâu film", "vàng nắng", "kem giấy");
            case "Modern Clean" -> List.of("trắng", "xanh GOLA", "xám sáng");
            case "Adventure" -> List.of("xanh rừng", "cam san hô", "xanh trời");
            case "Travel Postcard" -> List.of("kem postcard", "xanh biển", "đỏ tem thư");
            default -> List.of("xanh đêm", "teal", "cam hoàng hôn");
        };
    }

    private String styleDesignDirection(String albumStyle) {
        return switch (normalizeAlbumStyle(albumStyle)) {
            case "Vintage Film" -> "Tông film ấm, khung ảnh hoài niệm, caption như nhật ký cũ.";
            case "Modern Clean" -> "Bố cục magazine sạch, nhiều khoảng thở, caption ngắn và sắc.";
            case "Adventure" -> "Scrapbook năng lượng cao, sticker hành trình và nhãn địa điểm rõ.";
            case "Travel Postcard" -> "Bưu thiếp du lịch với nền giấy ấm, tem thư và lời nhắn ngắn.";
            default -> "Trang bìa điện ảnh, overlay tối nhẹ và caption như một cảnh phim.";
        };
    }

    private String sceneTypeFor(TripStop stop) {
        String category = stop != null && stop.getCategory() != null ? stop.getCategory().toLowerCase(Locale.ROOT) : "";
        String name = stop != null && stop.getName() != null ? stop.getName().toLowerCase(Locale.ROOT) : "";
        String text = category + " " + name;
        if (text.contains("food") || text.contains("cafe") || text.contains("restaurant") || text.contains("ăn") || text.contains("cà phê")) return "food";
        if (text.contains("hotel") || text.contains("homestay") || text.contains("lưu trú") || text.contains("khách sạn")) return "hotel";
        if (text.contains("beach") || text.contains("bãi") || text.contains("biển")) return "beach";
        if (text.contains("road") || text.contains("travel") || text.contains("di chuyển")) return "road";
        if (text.contains("park") || text.contains("núi") || text.contains("rừng")) return "nature";
        if (text.contains("city") || text.contains("market") || text.contains("chợ") || text.contains("phố")) return "city";
        return "other";
    }

    private String hashtagSafe(String value) {
        String text = firstText(value, "TripMemory");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^A-Za-z0-9]+", "");
        return normalized.isBlank() ? "TripMemory" : normalized;
    }

    private TravelBookResponse bookResponse(TripMemory memory) {
        boolean fileAvailable = READY.equals(memory.getBookStatus()) && bookFileAvailable(memory.getBookFilePath());
        if (READY.equals(memory.getBookStatus()) && !fileAvailable) {
            log.warn("Travel book marked READY but PDF file is missing tripId={} path={}",
                memory.getTripId(), memory.getBookFilePath());
        }
        String downloadUrl = fileAvailable ? "/api/trips/" + memory.getTripId() + "/memory/book/download" : null;
        return TravelBookResponse.builder()
            .status(defaultStatus(memory.getBookStatus()))
            .pdfUrl(fileAvailable ? memory.getBookUrl() : null)
            .downloadUrl(downloadUrl)
            .error(firstText(memory.getBookError(), READY.equals(memory.getBookStatus()) && !fileAvailable ? "File PDF không còn tồn tại. Vui lòng tạo lại." : null))
            .generatedAt(memory.getBookGeneratedAt())
            .build();
    }

    private boolean bookFileAvailable(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            return Files.isRegularFile(Path.of(value));
        } catch (Exception e) {
            return false;
        }
    }

    private ReelStoryboardResponse mapToStoryboard(TripMemory memory) {
        JsonNode root = objectMapper.valueToTree(memory.getReelStoryboard());
        List<ReelStoryboardResponse.ReelSlideResponse> slides = new ArrayList<>();
        for (JsonNode slide : root.path("slides")) {
            slides.add(ReelStoryboardResponse.ReelSlideResponse.builder()
                .order(slide.path("order").asInt())
                .type(slide.path("type").asText(null))
                .title(slide.path("title").asText(null))
                .caption(slide.path("caption").asText(null))
                .imageUrl(slide.path("imageUrl").asText(null))
                .durationSeconds(slide.path("durationSeconds").asInt(4))
                .build());
        }
        return ReelStoryboardResponse.builder()
            .status(defaultStatus(memory.getReelStatus()))
            .title(root.path("title").asText(null))
            .durationSeconds(root.path("durationSeconds").asInt())
            .slides(slides)
            .error(memory.getReelError())
            .generatedAt(memory.getReelGeneratedAt())
            .build();
    }

    private Map<String, Object> storyboardToMap(ReelStoryboardResponse storyboard) {
        return objectMapper.convertValue(storyboard, Map.class);
    }

    private List<String> questTitles(TripContext context) {
        return context.completedQuestProgress().stream()
            .sorted(Comparator.comparing(QuestProgress::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(progress -> context.questsById().get(progress.getQuestId()))
            .filter(Objects::nonNull)
            .map(Quest::getTitle)
            .filter(this::hasText)
            .toList();
    }

    private String expenseSummary(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            return "No expenses recorded";
        }
        Map<String, BigDecimal> totals = expenses.stream()
            .collect(Collectors.groupingBy(Expense::getCurrency, Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        return totals.entrySet().stream()
            .map(entry -> entry.getValue().stripTrailingZeros().toPlainString() + " " + entry.getKey())
            .collect(Collectors.joining(", "));
    }

    private String firstImage(TripContext context, String albumSource) {
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            return context.photos().stream()
                .map(TripMemoryPhoto::getImageUrl)
                .filter(this::hasText)
                .findFirst()
                .orElse(context.trip().getCoverUrl());
        }
        return context.stops().stream()
            .map(TripStop::getImageUrl)
            .filter(this::hasText)
            .findFirst()
            .orElse(context.trip().getCoverUrl());
    }

    private long countImages(TripContext context, String albumSource) {
        if (SOURCE_PERSONAL_PHOTOS.equals(albumSource)) {
            return context.photos().stream()
                .map(TripMemoryPhoto::getImageUrl)
                .filter(this::hasText)
                .count();
        }
        long stopImages = context.stops().stream()
            .map(TripStop::getImageUrl)
            .filter(this::hasText)
            .count();
        return stopImages + (hasText(context.trip().getCoverUrl()) ? 1 : 0);
    }

    private String placeholderImageFor(TripStop stop) {
        String label = "GOLA%20Trip%20Memory";
        String color = "%2314B8A6";
        String category = stop != null && stop.getCategory() != null ? stop.getCategory().toUpperCase(Locale.ROOT) : "";
        if (category.contains("FOOD") || category.contains("CAFE") || category.contains("RESTAURANT")) {
            label = "Food%20Stop";
            color = "%23F97316";
        } else if (category.contains("HOTEL") || category.contains("HOMESTAY") || category.contains("ACCOMMODATION")) {
            label = "Stay%20Memory";
            color = "%238B5CF6";
        } else if (category.contains("MARKET") || category.contains("SHOP")) {
            label = "Local%20Market";
            color = "%23EAB308";
        } else if (category.contains("BEACH") || category.contains("CHECKIN") || category.contains("SIGHT")) {
            label = "Travel%20Highlight";
            color = "%230EA5E9";
        }
        return "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='960' height='540'>"
            + "<rect width='960' height='540' rx='36' fill='" + color + "'/>"
            + "<text x='480' y='270' text-anchor='middle' font-family='Arial' font-size='44' font-weight='700' fill='white'>"
            + label
            + "</text></svg>";
    }

    private boolean isRemoteImageUrl(String value) {
        return hasText(value) && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private Optional<Path> resolveLocalImagePath(String imageUrl) {
        if (!hasText(imageUrl)) {
            return Optional.empty();
        }
        String normalized = imageUrl.trim();
        String marker = "/api/uploads/trip-memory-photos/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return Optional.empty();
        }
        String relative = normalized.substring(markerIndex + "/api/uploads/".length()).replace('\\', '/');
        if (relative.contains("..")) {
            return Optional.empty();
        }
        Path path = Path.of("uploads").resolve(relative).toAbsolutePath().normalize();
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private String dateRange(Trip trip) {
        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();
        if (start != null && end != null) return DATE.format(start) + " - " + DATE.format(end);
        if (start != null) return DATE.format(start);
        if (trip.getCompletedAt() != null) {
            return DATE.format(trip.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        return "";
    }

    private String memoryTitle(Trip trip) {
        return firstText(trip.getTitle(), trip.getDestination(), "Trip Memory");
    }

    private String memorySummary(Trip trip) {
        return firstText(trip.getOrigin(), "Trip") + " to " + firstText(trip.getDestination(), "destination")
            + " completed with " + (trip.getStops() != null ? trip.getStops().size() : 0) + " stops.";
    }

    private String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String[] cleanHashtags(List<String> hashtags) {
        if (hashtags == null) return new String[0];
        return hashtags.stream()
            .filter(this::hasText)
            .map(String::trim)
            .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
            .distinct()
            .limit(10)
            .toArray(String[]::new);
    }

    private boolean hasGeminiKey() {
        return properties.getGemini() != null
            && properties.getGemini().getApiKey() != null
            && !properties.getGemini().getApiKey().isBlank();
    }

    private String normalizeAlbumSource(String value) {
        if (!hasText(value)) {
            return SOURCE_PERSONAL_PHOTOS;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("PERSONAL".equals(normalized) || "PHOTOS".equals(normalized)) {
            return SOURCE_PERSONAL_PHOTOS;
        }
        if (SOURCE_PERSONAL_PHOTOS.equals(normalized) || SOURCE_ITINERARY.equals(normalized)) {
            return normalized;
        }
        throw GolaException.badRequest("Unsupported album mode: " + value);
    }

    private String normalizeAlbumStyle(String value) {
        if (!hasText(value)) {
            return "Cinematic";
        }
        String normalized = value.trim();
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "vintage film", "vintage", "film" -> "Vintage Film";
            case "modern clean", "modern", "clean" -> "Modern Clean";
            case "adventure" -> "Adventure";
            case "travel postcard", "postcard" -> "Travel Postcard";
            default -> "Cinematic";
        };
    }

    private String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        message = message
            .replaceAll("(?i)(key=)[^\\s&]+", "$1***")
            .replaceAll("(?i)(api[_-]?key['\"]?\\s*[:=]\\s*)[^\\s,}]+", "$1***");
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }

    private String defaultStatus(String status) {
        return hasText(status) ? status : NOT_GENERATED;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String... values) {
        return Arrays.stream(values).filter(this::hasText).findFirst().orElse(null);
    }

    private Instant firstInstant(Instant... values) {
        return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String optionalSuffix(String value) {
        return hasText(value) ? " - " + value : "";
    }

    private record TripContext(
        Trip trip,
        TripMemory memory,
        List<TripStop> stops,
        List<TripMemoryPhoto> photos,
        List<TripNote> notes,
        List<Expense> expenses,
        List<QuestProgress> completedQuestProgress,
        Map<UUID, Quest> questsById
    ) {}

    private record AlbumGenerationResult(AlbumAiResult result, String mode) {}

    private record PdfFonts(PDFont titleFont, PDFont bodyFont, boolean unicode) {}

    @Data
    public static class AlbumAiResult {
        private String title;
        private String albumTitle;
        private String subtitle;
        private String summary;
        private String introStory;
        private String coverPhotoId;
        private String mood;
        private List<String> colorPalette;
        private String designDirection;
        private String coverCaption;
        private String shareCaption;
        private List<String> hashtags;
        private List<String> highlightMoments;
        private List<String> travelQuotes;
        @JsonAlias({"daySections", "moments"})
        private List<AlbumAiDay> days;
    }

    @Data
    public static class AlbumAiDay {
        private int dayIndex;
        private String title;
        private String summary;
        private String layoutType;
        @JsonAlias({"photoCaptions", "captions"})
        private List<AlbumAiItem> items;
    }

    @Data
    public static class AlbumAiItem {
        private String tripStopId;
        private String memoryPhotoId;
        private String photoId;
        private String stopName;
        private String title;
        private String caption;
        private String shortCaption;
        private String visualSummary;
        private String microStory;
        private String stickerText;
        private String cropFocus;
        private String filterSuggestion;
        private String sceneType;
        private String mood;
        private List<String> mainColors;
        private Double qualityScore;
        @JsonAlias({"isCoverCandidate"})
        private Boolean coverCandidate;
        private List<String> tags;
        private String emotionalTone;
        private String locationHint;
        private String highlight;
        private String imageUrl;
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDFont titleFont;
        private final PDFont bodyFont;
        private final boolean unicode;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PdfWriter(PDDocument document, PDFont titleFont, PDFont bodyFont, boolean unicode) {
            this.document = document;
            this.titleFont = titleFont;
            this.bodyFont = bodyFont;
            this.unicode = unicode;
        }

        private void page(String title) throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = 770;
            write(titleFont, 22, title, 54, y);
            y -= 34;
        }

        private void heading(String text) throws IOException {
            writeWrapped(titleFont, 18, text, 54, 470);
            y -= 8;
        }

        private void line(String text) throws IOException {
            writeWrapped(bodyFont, 12, text, 54, 470);
        }

        private void small(String text) throws IOException {
            writeWrapped(bodyFont, 9, text, 72, 450);
        }

        private void paragraph(String text) throws IOException {
            writeWrapped(bodyFont, 12, text, 54, 470);
            spacer();
        }

        private void image(Path path) {
            if (path == null || !Files.isRegularFile(path)) {
                return;
            }
            try {
                if (y < 190) {
                    page("Continued");
                }
                PDImageXObject image = PDImageXObject.createFromFileByExtension(path.toFile(), document);
                float maxWidth = 260;
                float maxHeight = 150;
                float ratio = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
                float width = Math.max(1, image.getWidth() * ratio);
                float height = Math.max(1, image.getHeight() * ratio);
                content.drawImage(image, 54, y - height, width, height);
                y -= height + 16;
            } catch (Exception e) {
                // Broken or unsupported images should not block travel book generation.
            }
        }

        private void bullet(String text) {
            try {
                writeWrapped(bodyFont, 11, "- " + text, 64, 450);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private void spacer() {
            y -= 12;
        }

        private void footer() throws IOException {
            write(bodyFont, 9, "Generated by GOLA", 54, 32);
        }

        private void writeWrapped(PDFont font, int size, String text, float x, float width) throws IOException {
            if (text == null || text.isBlank()) return;
            String printableText = printable(font, text, unicode);
            for (String paragraph : printableText.split("\\R")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split("\\s+")) {
                    String candidate = line.length() == 0 ? word : line + " " + word;
                    if (safeStringWidth(font, candidate, size) > width) {
                        write(font, size, line.toString(), x, y);
                        y -= size + 5;
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(candidate);
                    }
                    if (y < 70) page("Continued");
                }
                if (line.length() > 0) {
                    write(font, size, line.toString(), x, y);
                    y -= size + 5;
                }
            }
        }

        private void write(PDFont font, int size, String text, float x, float lineY) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, lineY);
            content.showText(printable(font, text, unicode));
            content.endText();
        }

        private void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private static float safeStringWidth(PDFont font, String text, int size) throws IOException {
            try {
                return font.getStringWidth(text) / 1000 * size;
            } catch (IllegalArgumentException e) {
                return font.getStringWidth(asciiFallback(text)) / 1000 * size;
            }
        }

        private static String printable(PDFont font, String text, boolean unicode) {
            if (text == null) return "";
            String clean = text.replace('\t', ' ');
            try {
                font.encode(clean);
                return clean;
            } catch (Exception e) {
                if (!unicode) {
                    return asciiFallback(clean);
                }
                StringBuilder builder = new StringBuilder();
                clean.codePoints().forEach(codePoint -> {
                    String character = new String(Character.toChars(codePoint));
                    try {
                        font.encode(character);
                        builder.append(character);
                    } catch (Exception ignored) {
                        builder.append(' ');
                    }
                });
                return builder.toString().replaceAll(" {2,}", " ").trim();
            }
        }

        private static String asciiFallback(String text) {
            if (text == null) return "";
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
            return normalized.replaceAll("[^\\x20-\\x7E\\r\\n]", "?");
        }
    }
}
