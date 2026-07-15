package com.gola.service;

import com.gola.dto.map.SavePlaceRequest;
import com.gola.dto.map.SavedPlaceResponse;
import com.gola.entity.SavedPlace;
import com.gola.exception.GolaException;
import com.gola.repository.SavedPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedPlaceService {
    private final SavedPlaceRepository savedPlaceRepo;

    public List<SavedPlaceResponse> listSavedPlaces(UUID userId) {
        return savedPlaceRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SavedPlaceResponse savePlace(UUID userId, SavePlaceRequest req) {
        if ((req.getExternalPlaceId() == null || req.getExternalPlaceId().isBlank()) && req.getPlaceId() == null) {
            if (!hasValidCoordinate(req.getLatitude(), req.getLongitude())) {
                throw GolaException.badRequest("Place must include coordinates or an external place ID");
            }
        }

        var existing = req.getExternalPlaceId() != null && !req.getExternalPlaceId().isBlank()
                ? savedPlaceRepo.findByUserIdAndExternalPlaceId(userId, req.getExternalPlaceId())
                : req.getPlaceId() != null
                    ? savedPlaceRepo.findByUserIdAndPlaceId(userId, req.getPlaceId())
                    : java.util.Optional.<SavedPlace>empty();

        SavedPlace savedPlace = existing.orElseGet(SavedPlace::new);
        savedPlace.setUserId(userId);
        savedPlace.setPlaceId(req.getPlaceId());
        savedPlace.setExternalPlaceId(blankToNull(req.getExternalPlaceId()));
        savedPlace.setName(req.getName().trim());
        savedPlace.setAddress(blankToNull(req.getAddress()));
        savedPlace.setCategory(blankToNull(req.getCategory()));
        savedPlace.setLatitude(req.getLatitude());
        savedPlace.setLongitude(req.getLongitude());
        savedPlace.setProvider(blankToNull(req.getProvider()));

        return mapToResponse(savedPlaceRepo.save(savedPlace));
    }

    @Transactional
    public void deleteSavedPlace(UUID userId, UUID savedPlaceId) {
        SavedPlace savedPlace = savedPlaceRepo.findById(savedPlaceId)
                .orElseThrow(() -> GolaException.notFound("Saved place"));
        if (!savedPlace.getUserId().equals(userId)) {
            throw GolaException.forbidden();
        }
        savedPlaceRepo.delete(savedPlace);
    }

    private SavedPlaceResponse mapToResponse(SavedPlace savedPlace) {
        return SavedPlaceResponse.builder()
                .id(savedPlace.getId())
                .userId(savedPlace.getUserId())
                .placeId(savedPlace.getPlaceId())
                .externalPlaceId(savedPlace.getExternalPlaceId())
                .name(savedPlace.getName())
                .address(savedPlace.getAddress())
                .category(savedPlace.getCategory())
                .latitude(savedPlace.getLatitude())
                .longitude(savedPlace.getLongitude())
                .provider(savedPlace.getProvider())
                .createdAt(savedPlace.getCreatedAt())
                .build();
    }

    private static boolean hasValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null
                && Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0.0 && lng != 0.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
