package com.gola.service;

import com.gola.dto.map.PlaceResponse;
import com.gola.entity.Place;
import com.gola.exception.GolaException;
import com.gola.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {
    private final PlaceRepository placeRepo;

    public List<PlaceResponse> searchPlaces(String query, Double lat, Double lng) {
        return placeRepo.searchLocalPlaces(query, lat, lng).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public PlaceResponse getPlaceById(UUID id) {
        var place = placeRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Place"));
        return mapToResponse(place);
    }

    @Transactional
    public PlaceResponse upsertPlace(Place place) {
        if (place.getGooglePlaceId() != null) {
            var existingOpt = placeRepo.findByGooglePlaceId(place.getGooglePlaceId());
            if (existingOpt.isPresent()) {
                var existing = existingOpt.get();
                existing.setName(place.getName());
                existing.setCategory(place.getCategory());
                existing.setAddress(place.getAddress());
                existing.setCity(place.getCity());
                existing.setCountry(place.getCountry());
                existing.setPhotos(place.getPhotos());
                existing.setOpeningHours(place.getOpeningHours());
                existing.setRating(place.getRating());
                existing.setRefreshedAt(Instant.now());
                return mapToResponse(placeRepo.save(existing));
            }
        }
        place.setRefreshedAt(Instant.now());
        return mapToResponse(placeRepo.save(place));
    }

    private PlaceResponse mapToResponse(Place p) {
        return PlaceResponse.builder()
            .id(p.getId())
            .googlePlaceId(p.getGooglePlaceId())
            .name(p.getName())
            .category(p.getCategory())
            .address(p.getAddress())
            .city(p.getCity())
            .country(p.getCountry())
            .photos(p.getPhotos())
            .openingHours(p.getOpeningHours())
            .rating(p.getRating() != null ? p.getRating().doubleValue() : null)
            .refreshedAt(p.getRefreshedAt())
            .createdAt(p.getCreatedAt())
            .build();
    }
}
