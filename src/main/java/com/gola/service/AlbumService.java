package com.gola.service;

import com.gola.dto.community.CreateAlbumRequest;
import com.gola.dto.community.AlbumDayResponse;
import com.gola.dto.community.AlbumItemResponse;
import com.gola.dto.community.AlbumResponse;
import com.gola.entity.Album;
import com.gola.entity.AlbumMedia;
import com.gola.exception.GolaException;
import com.gola.repository.AlbumMediaRepository;
import com.gola.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {
    private final AlbumRepository albumRepo;
    private final AlbumMediaRepository albumMediaRepo;

    @Transactional
    public AlbumResponse createAlbum(UUID userId, CreateAlbumRequest req) {
        var album = Album.builder()
            .ownerId(userId)
            .tripId(req.getTripId())
            .title(req.getTitle())
            .isPublic(req.isPublic())
            .isAiCurated(false)
            .build();
        albumRepo.save(album);
        log.info("Album created: {} by user: {}", album.getId(), userId);
        return mapToResponse(album);
    }

    public List<AlbumResponse> getAlbumsByTrip(UUID tripId) {
        return albumRepo.findByTripId(tripId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public AlbumResponse getAlbumById(UUID id) {
        var album = albumRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Album"));
        return mapToResponse(album);
    }

    @Transactional
    public AlbumResponse addPhoto(UUID albumId, UUID userId, String photoUrl) {
        var album = albumRepo.findById(albumId)
            .orElseThrow(() -> GolaException.notFound("Album"));
        if (!album.getOwnerId().equals(userId)) {
            throw GolaException.forbidden();
        }
        if (album.getCoverUrl() == null || album.getCoverUrl().isBlank()) {
            album.setCoverUrl(photoUrl);
        }
        return mapToResponse(albumRepo.save(album));
    }

    @Transactional
    public void deleteAlbum(UUID albumId, UUID userId) {
        var album = albumRepo.findById(albumId)
            .orElseThrow(() -> GolaException.notFound("Album"));
        if (!album.getOwnerId().equals(userId)) {
            throw GolaException.forbidden();
        }
        albumRepo.delete(album);
        log.info("Album deleted: {} by user: {}", albumId, userId);
    }

    public AlbumResponse mapToResponse(Album a) {
        List<AlbumMedia> media = albumMediaRepo.findByAlbumIdOrderByDayIndexAscOrderIdxAsc(a.getId());
        List<AlbumDayResponse> days = mapDays(media);
        AlbumMedia firstMedia = media.stream().findFirst().orElse(null);
        AlbumMedia coverMedia = media.stream()
            .filter(item -> Boolean.TRUE.equals(metadataBoolean(item, "coverCandidate")))
            .findFirst()
            .orElse(firstMedia);
        List<String> palette = media.stream()
            .flatMap(item -> metadataList(item, "mainColors").stream())
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .limit(6)
            .toList();
        return AlbumResponse.builder()
            .id(a.getId())
            .ownerId(a.getOwnerId())
            .tripId(a.getTripId())
            .title(a.getTitle())
            .coverUrl(a.getCoverUrl())
            .isPublic(a.isPublic())
            .isAiCurated(a.isAiCurated())
            .albumSource(a.getAlbumSource())
            .status(a.getStatus())
            .summary(a.getSummary())
            .subtitle(metadataText(firstMedia, "subtitle"))
            .introStory(a.getSummary())
            .mood(a.getMood())
            .colorPalette(palette)
            .designDirection(metadataText(firstMedia, "designDirection"))
            .coverCaption(a.getCoverCaption())
            .coverPhotoId(coverMedia != null ? coverMedia.getMemoryPhotoId() : null)
            .analysisMode(metadataText(firstMedia, "generationMode"))
            .analyzedPhotoCount((int) media.stream().filter(item -> Boolean.TRUE.equals(metadataBoolean(item, "uploadedPhoto"))).count())
            .shareCaption(a.getShareCaption())
            .hashtags(a.getHashtags() != null ? Arrays.asList(a.getHashtags()) : List.of())
            .highlightMoments(media.stream().map(AlbumMedia::getHighlight).filter(Objects::nonNull).distinct().limit(5).toList())
            .travelQuotes(media.stream().map(item -> metadataText(item, "travelQuote")).filter(Objects::nonNull).distinct().limit(3).toList())
            .errorMessage(a.getErrorMessage())
            .days(days)
            .generatedAt(a.getGeneratedAt())
            .createdAt(a.getCreatedAt())
            .updatedAt(a.getUpdatedAt())
            .build();
    }

    private List<AlbumDayResponse> mapDays(List<AlbumMedia> media) {
        Map<Integer, List<AlbumMedia>> byDay = media.stream()
            .collect(Collectors.groupingBy(AlbumMedia::getDayIndex, LinkedHashMap::new, Collectors.toList()));
        List<AlbumDayResponse> days = new ArrayList<>();
        for (Map.Entry<Integer, List<AlbumMedia>> entry : byDay.entrySet()) {
            List<AlbumMedia> items = entry.getValue();
            AlbumMedia first = items.get(0);
            days.add(AlbumDayResponse.builder()
                .dayIndex(entry.getKey())
                .title(first.getDayTitle())
                .summary(first.getDaySummary())
                .layoutType(metadataText(first, "layoutType"))
                .items(items.stream()
                    .map(item -> AlbumItemResponse.builder()
                        .id(item.getId())
                        .tripStopId(item.getTripStopId())
                        .memoryPhotoId(item.getMemoryPhotoId())
                        .stopName(item.getStopName())
                        .title(metadataText(item, "title"))
                        .caption(item.getCaption())
                        .shortCaption(metadataText(item, "shortCaption"))
                        .emotionalTone(metadataText(item, "emotionalTone"))
                        .locationHint(metadataText(item, "locationHint"))
                        .highlight(item.getHighlight())
                        .imageUrl(item.getMediaUrl())
                        .visualSummary(metadataText(item, "visualSummary"))
                        .microStory(metadataText(item, "microStory"))
                        .stickerText(metadataText(item, "stickerText"))
                        .cropFocus(metadataText(item, "cropFocus"))
                        .filterSuggestion(metadataText(item, "filterSuggestion"))
                        .sceneType(metadataText(item, "sceneType"))
                        .mood(metadataText(item, "mood"))
                        .layoutType(metadataText(item, "layoutType"))
                        .qualityScore(metadataDouble(item, "qualityScore"))
                        .coverCandidate(metadataBoolean(item, "coverCandidate"))
                        .tags(metadataList(item, "tags"))
                        .mainColors(metadataList(item, "mainColors"))
                        .orderIdx(item.getOrderIdx())
                        .build())
                    .toList())
                .build());
        }
        return days;
    }

    private String metadataText(AlbumMedia item, String key) {
        if (item == null || item.getMetadata() == null || !item.getMetadata().containsKey(key)) {
            return null;
        }
        Object value = item.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private Boolean metadataBoolean(AlbumMedia item, String key) {
        if (item == null || item.getMetadata() == null || !item.getMetadata().containsKey(key)) {
            return null;
        }
        Object value = item.getMetadata().get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return Boolean.parseBoolean(text);
        return null;
    }

    private Double metadataDouble(AlbumMedia item, String key) {
        if (item == null || item.getMetadata() == null || !item.getMetadata().containsKey(key)) {
            return null;
        }
        Object value = item.getMetadata().get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> metadataList(AlbumMedia item, String key) {
        if (item == null || item.getMetadata() == null || !item.getMetadata().containsKey(key)) {
            return List.of();
        }
        Object value = item.getMetadata().get(key);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(text -> !text.isBlank())
                .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }
}
