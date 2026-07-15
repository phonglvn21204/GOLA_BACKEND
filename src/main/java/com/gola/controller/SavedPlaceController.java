package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.map.SavePlaceRequest;
import com.gola.dto.map.SavedPlaceResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.SavedPlaceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
public class SavedPlaceController {
    private final SavedPlaceService savedPlaceService;

    @GetMapping("/me/saved-places")
    @Operation(summary = "List current user's saved places")
    public ResponseEntity<ApiResponse<List<SavedPlaceResponse>>> listSavedPlaces() {
        return ResponseEntity.ok(ApiResponse.ok(savedPlaceService.listSavedPlaces(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/places/save")
    @Operation(summary = "Save a place for the current user")
    public ResponseEntity<ApiResponse<SavedPlaceResponse>> savePlace(@Valid @RequestBody SavePlaceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Place saved",
                savedPlaceService.savePlace(SecurityUtils.getCurrentUserId(), req)
        ));
    }

    @DeleteMapping("/places/save/{id}")
    @Operation(summary = "Remove a saved place")
    public ResponseEntity<ApiResponse<Void>> deleteSavedPlace(@PathVariable UUID id) {
        savedPlaceService.deleteSavedPlace(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Place unsaved", null));
    }
}
