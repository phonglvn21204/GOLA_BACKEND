package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.common.PageResponse;
import com.gola.dto.safety.IncidentRequest;
import com.gola.dto.safety.IncidentResponse;
import com.gola.entity.enums.IncidentSeverity;
import com.gola.entity.enums.IncidentType;
import com.gola.security.SecurityUtils;
import com.gola.service.IncidentAiService;
import com.gola.service.IncidentService;
import com.gola.service.R2StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/trips/{tripId}/incidents")
@RequiredArgsConstructor
@Tag(name = "Trip Incidents", description = "Trip-scoped incident reporting")
public class TripIncidentController {
    private final IncidentService incidentService;
    private final IncidentAiService incidentAiService;
    private final R2StorageService r2StorageService;

    @GetMapping
    @Operation(summary = "Get incident history for a trip")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> list(
            @PathVariable UUID tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                incidentService.getTripIncidents(tripId, SecurityUtils.getCurrentUserId(), page, size)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Report a new trip incident with optional photo")
    public ResponseEntity<ApiResponse<IncidentResponse>> create(
            @PathVariable UUID tripId,
            @RequestParam IncidentType type,
            @RequestParam(defaultValue = "MEDIUM") IncidentSeverity severity,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) MultipartFile photo) throws IOException {
        UUID userId = SecurityUtils.getCurrentUserId();
        String photoUrl = photo != null && !photo.isEmpty() ? storeIncidentPhoto(userId, photo) : null;
        IncidentRequest request = new IncidentRequest();
        request.setTripId(tripId);
        request.setType(type);
        request.setSeverity(severity);
        request.setDescription(description);
        request.setLatitude(lat);
        request.setLongitude(lng);
        request.setPhotoUrl(photoUrl);
        request.setNeedsAiReroute(true);

        IncidentResponse created = incidentService.createIncident(userId, request);
        try {
            incidentAiService.generateSuggestions(created.getId(), userId);
            created = incidentService.getIncidentById(created.getId());
        } catch (Exception e) {
            // Incident creation is the source of truth; AI may fail-soft.
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Incident reported", created));
    }

    private String storeIncidentPhoto(UUID userId, MultipartFile photo) throws IOException {
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Incident photo must be an image.");
        }
        String extension = extensionFrom(photo.getOriginalFilename(), contentType);
        String fileName = "incident-" + UUID.randomUUID() + extension;
        String key = "community-media/" + userId + "/" + fileName;
        return r2StorageService.uploadFile(key, photo.getInputStream(), contentType, photo.getSize());
    }

    private String extensionFrom(String originalName, String contentType) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String ext = originalName.substring(dot).toLowerCase();
                if (ext.matches("\\.(png|jpe?g|webp|gif)")) return ext;
            }
        }
        if (contentType != null && contentType.toLowerCase().contains("png")) return ".png";
        if (contentType != null && contentType.toLowerCase().contains("webp")) return ".webp";
        if (contentType != null && contentType.toLowerCase().contains("gif")) return ".gif";
        return ".jpg";
    }
}
