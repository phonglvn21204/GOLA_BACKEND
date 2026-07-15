package com.gola.service;

import com.gola.dto.trip.TripMemoryPhotoResponse;
import com.gola.dto.trip.UpdateTripMemoryPhotoRequest;
import com.gola.entity.Media;
import com.gola.entity.Trip;
import com.gola.entity.TripMemory;
import com.gola.entity.TripMemoryPhoto;
import com.gola.entity.TripStop;
import com.gola.entity.enums.TripStatus;
import com.gola.exception.GolaException;
import com.gola.repository.MediaRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripMemoryPhotoRepository;
import com.gola.repository.TripMemoryRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripMemoryPhotoService {
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final TripRepository tripRepo;
    private final TripMemberRepository memberRepo;
    private final TripMemoryRepository memoryRepo;
    private final TripMemoryPhotoRepository photoRepo;
    private final TripStopRepository stopRepo;
    private final MediaRepository mediaRepo;

    public List<TripMemoryPhotoResponse> listPhotos(UUID tripId, UUID userId) {
        requireTripMemory(tripId, userId);
        return photoRepo.findByTripIdAndUserIdOrderBySortOrderAscCreatedAtAsc(tripId, userId).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional
    public List<TripMemoryPhotoResponse> uploadPhotos(UUID tripId, UUID userId, MultipartFile[] files) {
        TripMemory memory = requireTripMemory(tripId, userId);
        if (files == null || files.length == 0) {
            throw GolaException.badRequest("No photos uploaded");
        }
        int nextOrder = photoRepo.countByTripIdAndUserId(tripId, userId);
        List<TripMemoryPhotoResponse> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            validateImage(file);
            uploaded.add(mapToResponse(storePhoto(memory, tripId, userId, file, nextOrder++)));
        }
        if (uploaded.isEmpty()) {
            throw GolaException.badRequest("No valid image files uploaded");
        }
        return uploaded;
    }

    @Transactional
    public TripMemoryPhotoResponse updatePhoto(UUID tripId, UUID photoId, UUID userId, UpdateTripMemoryPhotoRequest req) {
        requireTripMemory(tripId, userId);
        TripMemoryPhoto photo = photoRepo.findByIdAndTripIdAndUserId(photoId, tripId, userId)
            .orElseThrow(() -> GolaException.notFound("Trip memory photo"));
        if (req.getTripStopId() != null) {
            TripStop stop = stopRepo.findById(req.getTripStopId())
                .orElseThrow(() -> GolaException.notFound("Trip stop"));
            if (!stop.getTrip().getId().equals(tripId)) {
                throw GolaException.forbidden();
            }
        }
        photo.setDayIndex(req.getDayIndex());
        photo.setTripStopId(req.getTripStopId());
        photo.setCaptionNote(clean(req.getCaptionNote()));
        if (req.getSortOrder() != null) {
            photo.setSortOrder(Math.max(0, req.getSortOrder()));
        }
        return mapToResponse(photoRepo.save(photo));
    }

    @Transactional
    public void deletePhoto(UUID tripId, UUID photoId, UUID userId) {
        requireTripMemory(tripId, userId);
        TripMemoryPhoto photo = photoRepo.findByIdAndTripIdAndUserId(photoId, tripId, userId)
            .orElseThrow(() -> GolaException.notFound("Trip memory photo"));
        photoRepo.delete(photo);
        if (photo.getStoragePath() != null) {
            try {
                Files.deleteIfExists(Path.of(photo.getStoragePath()));
            } catch (IOException e) {
                log.warn("Could not delete trip memory photo file photoId={} path={}: {}", photoId, photo.getStoragePath(), e.getMessage());
            }
        }
    }

    private TripMemoryPhoto storePhoto(TripMemory memory, UUID tripId, UUID userId, MultipartFile file, int sortOrder) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "image/jpeg" : file.getContentType();
        String extension = extensionFor(originalName, contentType);
        String fileName = UUID.randomUUID() + extension;
        Path dir = Path.of("uploads", "trip-memory-photos", tripId.toString()).toAbsolutePath().normalize();
        Path path = dir.resolve(fileName).normalize();
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw GolaException.badRequest("Could not store uploaded photo");
        }

        String publicUrl = "/api/uploads/trip-memory-photos/" + tripId + "/" + fileName;
        Media media = mediaRepo.save(Media.builder()
            .ownerId(userId)
            .storagePath(path.toString())
            .mimeType(contentType)
            .width(0)
            .height(0)
            .build());

        TripMemoryPhoto photo = TripMemoryPhoto.builder()
            .tripMemoryId(memory.getId())
            .tripId(tripId)
            .userId(userId)
            .mediaId(media.getId())
            .imageUrl(publicUrl)
            .storagePath(path.toString())
            .originalFileName(originalName)
            .contentType(contentType)
            .fileSizeBytes(file.getSize())
            .sortOrder(sortOrder)
            .build();
        return photoRepo.save(photo);
    }

    private TripMemory requireTripMemory(UUID tripId, UUID userId) {
        Trip trip = tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        TripMemory memory = memoryRepo.findByTripIdAndUserId(tripId, userId).orElse(null);
        if (memory == null && trip.getStatus() != TripStatus.COMPLETED) {
            throw GolaException.badRequest("Complete the trip before adding memory photos");
        }
        if (memory != null) return memory;
        return memoryRepo.save(TripMemory.builder()
            .tripId(tripId)
            .userId(userId)
            .title(firstText(trip.getTitle(), trip.getDestination(), "Trip Memory"))
            .summary(firstText(trip.getOrigin(), "Trip") + " to " + firstText(trip.getDestination(), "destination")
                + " completed with " + (trip.getStops() != null ? trip.getStops().size() : 0) + " stops.")
            .status("NOT_GENERATED")
            .shareStatus("PRIVATE")
            .build());
    }

    private TripMemoryPhotoResponse mapToResponse(TripMemoryPhoto photo) {
        String stopName = photo.getTripStopId() == null
            ? null
            : stopRepo.findById(photo.getTripStopId()).map(TripStop::getName).orElse(null);
        return TripMemoryPhotoResponse.builder()
            .id(photo.getId())
            .tripMemoryId(photo.getTripMemoryId())
            .tripId(photo.getTripId())
            .userId(photo.getUserId())
            .mediaId(photo.getMediaId())
            .imageUrl(photo.getImageUrl())
            .originalFileName(photo.getOriginalFileName())
            .contentType(photo.getContentType())
            .fileSizeBytes(photo.getFileSizeBytes())
            .dayIndex(photo.getDayIndex())
            .tripStopId(photo.getTripStopId())
            .tripStopName(stopName)
            .captionNote(photo.getCaptionNote())
            .aiCaption(photo.getAiCaption())
            .sortOrder(photo.getSortOrder())
            .createdAt(photo.getCreatedAt())
            .updatedAt(photo.getUpdatedAt())
            .build();
    }

    private void validateImage(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw GolaException.badRequest("Photo must be 10MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw GolaException.badRequest("Only image files are allowed");
        }
    }

    private String extensionFor(String originalName, String contentType) {
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        }
        if (!ext.isBlank()) return ext;
        if (contentType.endsWith("png")) return ".png";
        if (contentType.endsWith("webp")) return ".webp";
        if (contentType.endsWith("gif")) return ".gif";
        return ".jpg";
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
