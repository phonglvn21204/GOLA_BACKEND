package com.gola.service;

import com.gola.dto.community.CreateAlbumRequest;
import com.gola.dto.community.AlbumResponse;
import com.gola.entity.Album;
import com.gola.exception.GolaException;
import com.gola.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {
    private final AlbumRepository albumRepo;

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

    private AlbumResponse mapToResponse(Album a) {
        return AlbumResponse.builder()
            .id(a.getId())
            .ownerId(a.getOwnerId())
            .tripId(a.getTripId())
            .title(a.getTitle())
            .coverUrl(a.getCoverUrl())
            .isPublic(a.isPublic())
            .isAiCurated(a.isAiCurated())
            .createdAt(a.getCreatedAt())
            .build();
    }
}
