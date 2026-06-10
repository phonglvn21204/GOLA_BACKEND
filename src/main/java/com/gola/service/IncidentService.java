package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.safety.IncidentRequest;
import com.gola.dto.safety.IncidentResponse;
import com.gola.entity.Incident;
import com.gola.exception.GolaException;
import com.gola.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {
    private final IncidentRepository incidentRepo;

    @Transactional
    public IncidentResponse createIncident(UUID userId, IncidentRequest req) {
        var incident = Incident.builder()
            .userId(userId)
            .tripId(req.getTripId())
            .type(req.getType())
            .description(req.getDescription())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .status("OPEN")
            .mediaUrls(new String[0])
            .build();
        incidentRepo.save(incident);
        log.info("Incident created: {} by user: {}", incident.getId(), userId);
        return mapToResponse(incident);
    }

    public PageResponse<IncidentResponse> getUserIncidents(UUID userId, int page, int size) {
        return new PageResponse<>(incidentRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
            .map(this::mapToResponse));
    }

    public IncidentResponse getIncidentById(UUID id) {
        var incident = incidentRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Incident"));
        return mapToResponse(incident);
    }

    public PageResponse<IncidentResponse> getNearbyIncidents(int page, int size) {
        return new PageResponse<>(incidentRepo.findByStatusOrderByCreatedAtDesc("OPEN", PageRequest.of(page, size))
            .map(this::mapToResponse));
    }

    @Transactional
    public IncidentResponse updateIncidentStatus(UUID id, String status) {
        var incident = incidentRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Incident"));
        incident.setStatus(status);
        log.info("Incident status updated: {} to status: {}", id, status);
        return mapToResponse(incidentRepo.save(incident));
    }

    public IncidentResponse mapToResponse(Incident i) {
        return IncidentResponse.builder()
            .id(i.getId())
            .userId(i.getUserId())
            .tripId(i.getTripId())
            .type(i.getType())
            .description(i.getDescription())
            .latitude(i.getLatitude())
            .longitude(i.getLongitude())
            .status(i.getStatus())
            .mediaUrls(i.getMediaUrls())
            .createdAt(i.getCreatedAt())
            .build();
    }
}
