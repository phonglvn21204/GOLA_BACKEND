package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.safety.IncidentRequest;
import com.gola.dto.safety.IncidentResponse;
import com.gola.entity.Incident;
import com.gola.entity.enums.IncidentSeverity;
import com.gola.exception.GolaException;
import com.gola.repository.IncidentRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {
    private final IncidentRepository incidentRepo;
    private final TripRepository tripRepo;
    private final TripMemberRepository memberRepo;

    @Transactional
    public IncidentResponse createIncident(UUID userId, IncidentRequest req) {
        if (req.getTripId() != null) {
            requireTripMember(req.getTripId(), userId);
        }

        String[] mediaUrls = req.getPhotoUrl() != null && !req.getPhotoUrl().isBlank()
                ? new String[] { req.getPhotoUrl() }
                : new String[0];

        var incident = Incident.builder()
            .userId(userId)
            .tripId(req.getTripId())
            .type(req.getType())
            .description(req.getDescription())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .currentStopId(req.getCurrentStopId())
            .affectedStopId(req.getAffectedStopId())
            .estimatedDelayMinutes(req.getEstimatedDelayMinutes())
            .needsAiReroute(Boolean.TRUE.equals(req.getNeedsAiReroute()))
            .context(req.getContext() != null ? req.getContext() : req.getNote())
            .severity(req.getSeverity() != null ? req.getSeverity() : IncidentSeverity.MEDIUM)
            .verifiedCount(0)
            .status("OPEN")
            .photoUrl(req.getPhotoUrl())
            .mediaUrls(mediaUrls)
            .build();
        incidentRepo.save(incident);
        log.info("Incident created: incidentId={} tripId={} userId={} type={} severity={} needsAiReroute={}",
                incident.getId(), incident.getTripId(), userId, incident.getType(), incident.getSeverity(), incident.isNeedsAiReroute());
        return mapToResponse(incident);
    }

    public PageResponse<IncidentResponse> getUserIncidents(UUID userId, int page, int size) {
        return new PageResponse<>(incidentRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
            .map(this::mapToResponse));
    }

    public PageResponse<IncidentResponse> getTripIncidents(UUID tripId, UUID userId, int page, int size) {
        requireTripMember(tripId, userId);
        return new PageResponse<>(incidentRepo.findByTripIdOrderByCreatedAtDesc(tripId, PageRequest.of(page, size))
                .map(this::mapToResponse));
    }

    public IncidentResponse getIncidentById(UUID id) {
        var incident = incidentRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Incident"));
        return mapToResponse(incident);
    }

    public PageResponse<IncidentResponse> getNearbyIncidents(com.gola.entity.enums.IncidentType type, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var incidents = type != null
            ? incidentRepo.findByStatusAndTypeOrderByCreatedAtDesc("OPEN", type, pageable)
            : incidentRepo.findByStatusOrderByCreatedAtDesc("OPEN", pageable);
        return new PageResponse<>(incidents
            .map(this::mapToResponse));
    }

    @Transactional
    public IncidentResponse updateIncidentStatus(UUID id, String status) {
        var incident = incidentRepo.findById(id)
            .orElseThrow(() -> GolaException.notFound("Incident"));
        return updateIncidentStatus(incident, status);
    }

    @Transactional
    public IncidentResponse resolveIncident(UUID id, UUID userId) {
        Incident incident = requireIncidentAndMember(id, userId);
        incident.setResolvedAt(Instant.now());
        return updateIncidentStatus(incident, "RESOLVED");
    }

    @Transactional
    public IncidentResponse cancelIncident(UUID id, UUID userId) {
        Incident incident = requireIncidentAndMember(id, userId);
        return updateIncidentStatus(incident, "CANCELLED");
    }

    @Transactional
    public IncidentResponse verify(UUID incidentId, UUID userId) {
        var incident = incidentRepo.findById(incidentId)
            .orElseThrow(() -> GolaException.notFound("Incident"));
        incident.setVerifiedCount((incident.getVerifiedCount() == null ? 0 : incident.getVerifiedCount()) + 1);
        log.info("Incident verified: {} by user: {}", incidentId, userId);
        return mapToResponse(incidentRepo.save(incident));
    }

    public Incident requireIncidentAndMember(UUID incidentId, UUID userId) {
        Incident incident = incidentRepo.findById(incidentId).orElseThrow(() -> GolaException.notFound("Incident"));
        if (incident.getTripId() != null) {
            requireTripMember(incident.getTripId(), userId);
        } else if (!incident.getUserId().equals(userId)) {
            throw GolaException.forbidden();
        }
        return incident;
    }

    public void requireTripMember(UUID tripId, UUID userId) {
        tripRepo.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
    }

    private IncidentResponse updateIncidentStatus(Incident incident, String status) {
        incident.setStatus(status);
        log.info("Incident status updated: {} to status: {}", incident.getId(), status);
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
            .currentStopId(i.getCurrentStopId())
            .affectedStopId(i.getAffectedStopId())
            .estimatedDelayMinutes(i.getEstimatedDelayMinutes())
            .needsAiReroute(i.isNeedsAiReroute())
            .context(i.getContext())
            .severity(i.getSeverity())
            .verifiedCount(i.getVerifiedCount())
            .status(i.getStatus())
            .aiSummary(i.getAiSummary())
            .aiSuggestionJson(i.getAiSuggestionJson())
            .photoUrl(i.getPhotoUrl())
            .mediaUrls(i.getMediaUrls())
            .createdAt(i.getCreatedAt())
            .updatedAt(i.getUpdatedAt())
            .resolvedAt(i.getResolvedAt())
            .build();
    }
}
