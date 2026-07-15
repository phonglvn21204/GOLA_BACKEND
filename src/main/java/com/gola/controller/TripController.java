package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.ai.AiInsightsResponse;
import com.gola.dto.ai.OptimizeRouteResponse;
import com.gola.dto.community.AlbumResponse;
import com.gola.security.SecurityUtils;
import com.gola.dto.trip.*;
import com.gola.service.AiInsightsService;
import com.gola.service.AiOptimizeService;
import com.gola.service.AiTripService;
import com.gola.service.TripMemoryArtifactService;
import com.gola.service.TripMemoryPhotoService;
import com.gola.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/trips", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trips", description = "CRUD, live sessions, share")
public class TripController {
    private final TripService tripService;
    private final AiOptimizeService aiOptimizeService;
    private final AiInsightsService aiInsightsService;
    private final AiTripService aiTripService;
    private final TripMemoryArtifactService memoryArtifactService;
    private final TripMemoryPhotoService memoryPhotoService;

    @PostMapping
    @Operation(summary = "Create a new trip")
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(@Valid @RequestBody CreateTripRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Trip created", tripService.createTrip(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping
    @Operation(summary = "List my trips")
    public ResponseEntity<ApiResponse<PageResponse<TripResponse>>> listTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(
            tripService.listMyTrips(SecurityUtils.getCurrentUserId(), PageRequest.of(page, size), status)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trip details")
    public ResponseEntity<ApiResponse<TripResponse>> getTrip(
            @PathVariable UUID id,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng) {
        log.info("[TripController.getTrip] id={} userLat={} userLng={}", id, userLat, userLng);
        return ResponseEntity.ok(ApiResponse.ok(
                tripService.getTrip(id, SecurityUtils.getCurrentUserId(), userLat, userLng)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update trip")
    public ResponseEntity<ApiResponse<TripResponse>> updateTrip(
            @PathVariable UUID id, @Valid @RequestBody CreateTripRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.updateTrip(id, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete trip (soft)")
    public ResponseEntity<ApiResponse<Void>> deleteTrip(@PathVariable UUID id) {
        tripService.deleteTrip(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Trip deleted", null));
    }

    @PostMapping("/{id}/stops")
    @Operation(summary = "Add stop to trip")
    public ResponseEntity<ApiResponse<TripStopResponse>> addStop(
            @PathVariable UUID id, @Valid @RequestBody AddStopRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Stop added", tripService.addStop(id, SecurityUtils.getCurrentUserId(), req)));
    }

    @PatchMapping("/{id}/stops/{stopId}")
    @Operation(summary = "Update a trip stop")
    public ResponseEntity<ApiResponse<TripStopResponse>> updateStop(
            @PathVariable UUID id,
            @PathVariable UUID stopId,
            @Valid @RequestBody AddStopRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stop updated",
                tripService.updateStop(id, stopId, SecurityUtils.getCurrentUserId(), req)));
    }

    @PostMapping("/{id}/stops/reorder")
    @Operation(summary = "Persist trip stop order")
    public ResponseEntity<ApiResponse<List<TripStopResponse>>> reorderStops(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderStopsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stops reordered",
                tripService.reorderStops(id, SecurityUtils.getCurrentUserId(), req)));
    }

    @PostMapping("/{id}/stops/{stopId}/refresh-place-data")
    @Operation(summary = "Refresh place enrichment for one stop")
    public ResponseEntity<ApiResponse<TripStopResponse>> refreshStopPlaceData(
            @PathVariable UUID id,
            @PathVariable UUID stopId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stop place data refreshed",
                tripService.refreshStopPlaceData(id, stopId, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/stops/refresh-place-data")
    @Operation(summary = "Refresh missing place enrichment for trip stops")
    public ResponseEntity<ApiResponse<List<TripStopResponse>>> refreshMissingStopPlaceData(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Trip place data refreshed",
                tripService.refreshMissingStopPlaceData(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/stops/repair-place-data")
    @Operation(summary = "Repair trusted place coordinates and enrichment for trip stops")
    public ResponseEntity<ApiResponse<RepairPlaceDataResponse>> repairTripPlaceData(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Trip place data repaired",
                tripService.repairTripPlaceData(id, SecurityUtils.getCurrentUserId())));
    }

    @DeleteMapping("/{id}/stops/{stopId}")
    @Operation(summary = "Remove stop from trip")
    public ResponseEntity<ApiResponse<Void>> deleteStop(@PathVariable UUID id, @PathVariable UUID stopId) {
        tripService.deleteStop(id, stopId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Stop removed", null));
    }

    @PatchMapping("/{id}/stops/{stopId}/complete")
    @Operation(summary = "Mark a trip stop as completed")
    public ResponseEntity<ApiResponse<TripStopResponse>> completeStop(@PathVariable UUID id, @PathVariable UUID stopId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stop completed",
                tripService.completeStop(id, stopId, SecurityUtils.getCurrentUserId())));
    }

    @PatchMapping("/{id}/stops/{stopId}/uncomplete")
    @Operation(summary = "Clear a trip stop completion")
    public ResponseEntity<ApiResponse<TripStopResponse>> uncompleteStop(@PathVariable UUID id, @PathVariable UUID stopId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Stop completion cleared",
                tripService.uncompleteStop(id, stopId, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start live trip session")
    public ResponseEntity<ApiResponse<Object>> startTrip(@PathVariable UUID id) {
        var session = tripService.startTrip(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Trip started", Map.of("sessionId", session.getId())));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End live trip session")
    public ResponseEntity<ApiResponse<TripResponse>> endTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Trip ended",
                tripService.endTrip(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a trip and create trip memory")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Trip completed",
                tripService.endTrip(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}/memory")
    @Operation(summary = "Get current user's memory for a completed trip")
    public ResponseEntity<ApiResponse<TripMemoryResponse>> getTripMemory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                tripService.getTripMemory(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}/memory/photos")
    @Operation(summary = "List current user's uploaded trip memory photos")
    public ResponseEntity<ApiResponse<List<TripMemoryPhotoResponse>>> listMemoryPhotos(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                memoryPhotoService.listPhotos(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping(value = "/{id}/memory/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload photos for the current user's trip memory")
    public ResponseEntity<ApiResponse<List<TripMemoryPhotoResponse>>> uploadMemoryPhotos(
            @PathVariable UUID id,
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Photos uploaded",
                        memoryPhotoService.uploadPhotos(id, SecurityUtils.getCurrentUserId(), files)));
    }

    @PatchMapping("/{id}/memory/photos/{photoId}")
    @Operation(summary = "Update trip memory photo metadata")
    public ResponseEntity<ApiResponse<TripMemoryPhotoResponse>> updateMemoryPhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId,
            @RequestBody UpdateTripMemoryPhotoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Photo updated",
                memoryPhotoService.updatePhoto(id, photoId, SecurityUtils.getCurrentUserId(), req)));
    }

    @DeleteMapping("/{id}/memory/photos/{photoId}")
    @Operation(summary = "Delete a trip memory photo")
    public ResponseEntity<ApiResponse<Void>> deleteMemoryPhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId) {
        memoryPhotoService.deletePhoto(id, photoId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Photo deleted", null));
    }

    @PostMapping("/{id}/memory/album/generate")
    @Operation(summary = "Generate AI travel album for a completed trip memory")
    public ResponseEntity<ApiResponse<AlbumResponse>> generateAlbum(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "PERSONAL_PHOTOS") String mode,
            @RequestParam(defaultValue = "Cinematic") String style) {
        return ResponseEntity.ok(ApiResponse.ok("Album generated",
                memoryArtifactService.generateAlbum(id, SecurityUtils.getCurrentUserId(), false, mode, style)));
    }

    @GetMapping("/{id}/memory/album")
    @Operation(summary = "Get generated AI travel album")
    public ResponseEntity<ApiResponse<AlbumResponse>> getAlbum(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                memoryArtifactService.getAlbum(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/memory/album/regenerate")
    @Operation(summary = "Regenerate AI travel album")
    public ResponseEntity<ApiResponse<AlbumResponse>> regenerateAlbum(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "PERSONAL_PHOTOS") String mode,
            @RequestParam(defaultValue = "Cinematic") String style) {
        return ResponseEntity.ok(ApiResponse.ok("Album regenerated",
                memoryArtifactService.generateAlbum(id, SecurityUtils.getCurrentUserId(), true, mode, style)));
    }

    @PostMapping("/{id}/memory/book/generate")
    @Operation(summary = "Generate digital travel book PDF")
    public ResponseEntity<ApiResponse<TravelBookResponse>> generateTravelBook(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Travel book generated",
                memoryArtifactService.generateBook(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}/memory/book")
    @Operation(summary = "Get digital travel book status")
    public ResponseEntity<ApiResponse<TravelBookResponse>> getTravelBook(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                memoryArtifactService.getBook(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping(value = "/{id}/memory/book/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download digital travel book PDF")
    public ResponseEntity<Resource> downloadTravelBook(@PathVariable UUID id) {
        Path path = memoryArtifactService.getBookPath(id, SecurityUtils.getCurrentUserId());
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/memory/reel/generate")
    @Operation(summary = "Generate AI reel storyboard preview")
    public ResponseEntity<ApiResponse<ReelStoryboardResponse>> generateReel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Reel storyboard generated",
                memoryArtifactService.generateReel(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}/memory/reel")
    @Operation(summary = "Get AI reel storyboard preview")
    public ResponseEntity<ApiResponse<ReelStoryboardResponse>> getReel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                memoryArtifactService.getReel(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/me/memories")
    @Operation(summary = "List current user's trip memories")
    public ResponseEntity<ApiResponse<List<TripMemoryResponse>>> listTripMemories() {
        return ResponseEntity.ok(ApiResponse.ok(
                tripService.listMyTripMemories(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Generate a share token")
    public ResponseEntity<ApiResponse<Object>> shareTrip(
            @PathVariable UUID id, @RequestBody ShareTripRequest req) {
        String token = tripService.createShareLink(id, SecurityUtils.getCurrentUserId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Share link created", Map.of("token", token)));
    }

    @PostMapping("/{id}/post-trip-summary")
    @Operation(summary = "Generate post-trip summary")
    public ResponseEntity<ApiResponse<TripResponse>> postTripSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Post-trip summary ready",
                tripService.getTrip(id, SecurityUtils.getCurrentUserId(), null, null)));
    }

    @PostMapping("/{id}/optimize")
    @Operation(summary = "Optimize trip route with AI")
    public ResponseEntity<ApiResponse<OptimizeRouteResponse>> optimizeRoute(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Route optimized",
                aiOptimizeService.optimizeRoute(id, SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/{id}/insights")
    @Operation(summary = "Get AI travel insights for trip")
    public ResponseEntity<ApiResponse<AiInsightsResponse>> getInsights(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Insights generated",
                aiInsightsService.getInsights(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/{id}/ai-improve")
    @Operation(summary = "AI improve – critic + gap fill + quality gate")
    public ResponseEntity<ApiResponse<TripResponse>> aiImprove(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        aiTripService.aiImproveTrip(id, userId);
        TripResponse trip = tripService.getTrip(id, userId, null, null);
        return ResponseEntity.ok(ApiResponse.ok("Lịch trình đã được AI cải thiện", trip));
    }
}
