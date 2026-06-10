package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.community.AlbumResponse;
import com.gola.dto.community.CreateAlbumRequest;
import com.gola.security.SecurityUtils;
import com.gola.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/albums")
@RequiredArgsConstructor
@Tag(name = "Albums", description = "Trip photo albums")
public class AlbumController {

    private final AlbumService albumService;

    @PostMapping
    @Operation(summary = "Create a new album")
    public ResponseEntity<ApiResponse<AlbumResponse>> createAlbum(
            @Valid @RequestBody CreateAlbumRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Album created",
                        albumService.createAlbum(SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping("/trip/{tripId}")
    @Operation(summary = "Get all albums for a trip")
    public ResponseEntity<ApiResponse<List<AlbumResponse>>> getAlbumsByTrip(
            @PathVariable UUID tripId) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.getAlbumsByTrip(tripId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get album by ID")
    public ResponseEntity<ApiResponse<AlbumResponse>> getAlbumById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(albumService.getAlbumById(id)));
    }

    @PostMapping("/{id}/photos")
    @Operation(summary = "Attach a photo URL to an album")
    public ResponseEntity<ApiResponse<AlbumResponse>> addPhoto(@PathVariable UUID id, @RequestBody AddAlbumPhotoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Photo added",
                albumService.addPhoto(id, SecurityUtils.getCurrentUserId(), req.getUrl())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an album (owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteAlbum(@PathVariable UUID id) {
        albumService.deleteAlbum(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Album deleted", null));
    }

    @Data
    public static class AddAlbumPhotoRequest {
        private String url;
    }
}
