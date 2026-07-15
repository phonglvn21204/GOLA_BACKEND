package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.entity.TripNote;
import com.gola.security.SecurityUtils;
import com.gola.service.TripNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/trips/{tripId}/notes", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
@Tag(name = "Trip Notes", description = "Notes for a trip")
public class TripNoteController {

    private final TripNoteService tripNoteService;

    @PostMapping
    @Operation(summary = "Add a note to a trip")
    public ResponseEntity<ApiResponse<TripNote>> addNote(
            @PathVariable UUID tripId,
            @RequestBody AddNoteRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Note added",
                tripNoteService.addNote(tripId, SecurityUtils.getCurrentUserId(), req.getContent())));
    }

    @GetMapping
    @Operation(summary = "Get notes for a trip")
    public ResponseEntity<ApiResponse<List<TripNote>>> getNotes(@PathVariable UUID tripId) {
        return ResponseEntity.ok(ApiResponse.ok(
                tripNoteService.getNotes(tripId, SecurityUtils.getCurrentUserId())));
    }

    @DeleteMapping("/{noteId}")
    @Operation(summary = "Delete a note")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable UUID tripId,
            @PathVariable UUID noteId) {
        tripNoteService.deleteNote(noteId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Note deleted", null));
    }

    @Data
    static class AddNoteRequest {
        @NotBlank
        private String content;
    }
}
