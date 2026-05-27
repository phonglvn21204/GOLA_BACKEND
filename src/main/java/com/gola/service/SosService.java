package com.gola.service;

import com.gola.exception.GolaException;
import com.gola.dto.safety.*;
import com.gola.entity.*;
import com.gola.entity.enums.*;
import com.gola.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SosService {
    private final SosEventRepository      sosRepo;
    private final EmergencyContactRepository contactRepo;
    private final IncidentRepository       incidentRepo;
    private final SimpMessagingTemplate   messaging;

    @Transactional
    public SosResponse triggerSos(UUID userId, SosTriggerRequest req) {
        if (req.getClientToken() != null) {
            sosRepo.findByClientToken(req.getClientToken()).ifPresent(existing -> {
                if (existing.getStatus() == SosStatus.ACTIVE) {
                    throw GolaException.conflict("SOS already active");
                }
            });
        }
        var sos = SosEvent.builder()
            .userId(userId).tripId(req.getTripId())
            .latitude(req.getLatitude()).longitude(req.getLongitude())
            .clientToken(req.getClientToken() != null ? req.getClientToken() : UUID.randomUUID().toString())
            .status(SosStatus.ACTIVE).build();
        sosRepo.save(sos);
        log.warn("SOS TRIGGERED by user:{} at [{},{}]", userId, req.getLatitude(), req.getLongitude());
        broadcastSos(sos);
        dispatchSosAsync(sos, userId);
        return mapToResponse(sos);
    }

    @Transactional
    public void resolveSos(UUID sosId, UUID resolvedBy, String reason) {
        var sos = sosRepo.findById(sosId).orElseThrow(() -> GolaException.notFound("SOS event"));
        sos.setStatus("FALSE_ALARM".equals(reason) ? SosStatus.FALSE_ALARM : SosStatus.RESOLVED);
        sos.setResolvedAt(Instant.now());
        sos.setResolvedBy(resolvedBy);
        sosRepo.save(sos);
        messaging.convertAndSend("/topic/sos/" + sosId, java.util.Map.of("event","sos.resolved","sosId",sosId));
    }

    public List<SosEvent> getActiveSosEvents() {
        return sosRepo.findByStatus(SosStatus.ACTIVE);
    }

    @Async("sosExecutor")
    public void dispatchSosAsync(SosEvent sos, UUID userId) {
        var contacts = contactRepo.findByUserIdOrderByPriorityAsc(userId);
        for (var c : contacts) {
            log.info("Dispatching SOS SMS to {} for user {}", c.getPhone(), userId);
            // TODO: integrate TwilioService here
        }
    }

    private void broadcastSos(SosEvent sos) {
        messaging.convertAndSend("/topic/admin/sos", mapToResponse(sos));
        if (sos.getTripId() != null) {
            messaging.convertAndSend("/topic/trip/" + sos.getTripId(), java.util.Map.of("event","sos.created","sos", mapToResponse(sos)));
        }
    }

    private SosResponse mapToResponse(SosEvent e) {
        return SosResponse.builder().id(e.getId()).userId(e.getUserId()).tripId(e.getTripId())
            .latitude(e.getLatitude()).longitude(e.getLongitude())
            .status(e.getStatus()).createdAt(e.getCreatedAt()).build();
    }
}