package com.gola.service;

import com.gola.exception.GolaException;
import com.gola.dto.safety.*;
import com.gola.entity.*;
import com.gola.entity.enums.*;
import com.gola.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SosService {
    private final SosEventRepository      sosRepo;
    private final EmergencyContactRepository contactRepo;
    private final IncidentRepository       incidentRepo;
    private final SimpMessagingTemplate   messaging;
    private final LiveLocationRepository  liveLocationRepo;
    private final TripSessionRepository   tripSessionRepo;
    private final TripMemberRepository    tripMemberRepo;
    private final DeviceTokenRepository   deviceTokenRepo;
    private final ProfileRepository       profileRepo;
    private final FcmService              fcmService;
    private final TwilioSmsService        twilioSmsService;
    private final Set<UUID> escalatedSosIds = ConcurrentHashMap.newKeySet();

    @Transactional
    public SosResponse triggerSos(UUID userId, SosTriggerRequest req) {
        if (req.getClientToken() != null) {
            sosRepo.findByClientToken(req.getClientToken()).ifPresent(existing -> {
                if (existing.getStatus() == SosStatus.ACTIVE) {
                    throw GolaException.conflict("SOS already active");
                }
            });
        }
        var latestLocation = liveLocationRepo.findTopByUserIdOrderByTsDesc(userId).orElse(null);
        UUID tripId = req.getTripId() != null ? req.getTripId() : resolveTripId(latestLocation);
        Double latitude = req.getLatitude() != null ? req.getLatitude() : latestLocation != null ? latestLocation.getLat() : null;
        Double longitude = req.getLongitude() != null ? req.getLongitude() : latestLocation != null ? latestLocation.getLng() : null;

        var sos = SosEvent.builder()
            .userId(userId).tripId(tripId)
            .latitude(latitude).longitude(longitude)
            .clientToken(req.getClientToken() != null ? req.getClientToken() : UUID.randomUUID().toString())
            .status(SosStatus.ACTIVE).build();
        sosRepo.save(sos);
        log.warn("SOS TRIGGERED by user:{} at [{},{}]", userId, latitude, longitude);
        log.info("SOS trigger input: sosId={} tripId={} triggeringUserId={}",
                sos.getId(), sos.getTripId(), userId);
        broadcastSos(sos);
        dispatchSosAsync(sos, userId);
        return mapToResponse(sos);
    }

    @Transactional
    public void resolveSos(UUID sosId, UUID resolvedBy, String reason) {
        var sos = sosRepo.findById(sosId).orElseThrow(() -> GolaException.notFound("SOS event"));
        if (sos.getStatus() != SosStatus.ACTIVE && sos.getStatus() != SosStatus.ACKNOWLEDGED) {
            throw new GolaException(HttpStatus.CONFLICT, "SOS_INACTIVE", "SOS is no longer active");
        }
        sos.setStatus("FALSE_ALARM".equals(reason) ? SosStatus.FALSE_ALARM : SosStatus.RESOLVED);
        sos.setResolvedAt(Instant.now());
        sos.setResolvedBy(resolvedBy);
        sosRepo.save(sos);
        escalatedSosIds.remove(sosId);
        messaging.convertAndSend("/topic/sos/" + sosId, mapToResponse(sos));
        
        // Broadcast SOS_CANCELLED to trip members (excluding sender)
        if (sos.getTripId() != null) {
            messaging.convertAndSend("/topic/trip/" + sos.getTripId(), java.util.Map.of(
                "event", "sos.cancelled",
                "sosId", sos.getId().toString(),
                "status", sos.getStatus().toString(),
                "excludeUser", sos.getUserId().toString()
            ));
        }
    }

    @Transactional
    public SosResponse acknowledge(UUID sosId, UUID acknowledgerId) {
        var sos = sosRepo.findById(sosId).orElseThrow(() -> GolaException.notFound("SOS event"));
        if (sos.getStatus() != SosStatus.ACTIVE) {
            throw new GolaException(HttpStatus.CONFLICT, "SOS_INACTIVE", "SOS is no longer active");
        }
        sos.setStatus(SosStatus.ACKNOWLEDGED);
        sos.setAcknowledgedAt(Instant.now());
        sos.setAcknowledgedBy(acknowledgerId);
        sosRepo.save(sos);
        messaging.convertAndSend("/topic/sos/" + sosId, mapToResponse(sos));
        broadcastSos(sos);
        return mapToResponse(sos);
    }

    public List<SosEvent> getActiveSosEvents() {
        return sosRepo.findByStatusIn(List.of(SosStatus.ACTIVE, SosStatus.ACKNOWLEDGED));
    }

    @Async("sosExecutor")
    public void dispatchSosAsync(SosEvent sos, UUID userId) {
        sendSosNotifications(sos, userId, false);
    }

    @Scheduled(fixedDelay = 120_000)
    public void escalateUnacknowledgedSos() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(5, ChronoUnit.MINUTES);
        sosRepo.findByStatusAndCreatedAtBefore(SosStatus.ACKNOWLEDGED, cutoff).forEach(sos ->
                log.info("SOS escalation skipped: sosId={} status={} acknowledgedAt={} nextCheckAt={} reason=ACKNOWLEDGED",
                        sos.getId(), sos.getStatus(), sos.getAcknowledgedAt(), now.plus(120, ChronoUnit.SECONDS)));
        for (SosEvent sos : sosRepo.findByStatusAndCreatedAtBefore(SosStatus.ACTIVE, cutoff)) {
            if (!escalatedSosIds.add(sos.getId())) {
                log.info("SOS escalation already sent in this runtime: sosId={} status={} nextCheckAt={} resendSuppressed=true",
                        sos.getId(), sos.getStatus(), now.plus(120, ChronoUnit.SECONDS));
                continue;
            }
            log.warn("SOS escalation resend: sosId={} status={} tripId={} userId={} createdAt={} nextCheckAt={}",
                    sos.getId(), sos.getStatus(), sos.getTripId(), sos.getUserId(), sos.getCreatedAt(),
                    now.plus(120, ChronoUnit.SECONDS));
            sendSosNotifications(sos, sos.getUserId(), true);
        }
    }

    private void broadcastSos(SosEvent sos) {
        messaging.convertAndSend("/topic/admin/sos", mapToResponse(sos));
        if (sos.getTripId() != null) {
            // Include excludeUser so WebSocket subscribers (frontend) can filter out the sender
            messaging.convertAndSend("/topic/trip/" + sos.getTripId(), java.util.Map.of(
                "event", "sos.created",
                "sos", mapToResponse(sos),
                "excludeUser", sos.getUserId().toString()
            ));
        }
    }

    public SosResponse mapToResponse(SosEvent e) {
        return SosResponse.builder().id(e.getId()).userId(e.getUserId()).tripId(e.getTripId())
            .latitude(e.getLatitude()).longitude(e.getLongitude())
            .status(e.getStatus())
            .acknowledgedAt(e.getAcknowledgedAt())
            .acknowledgedBy(e.getAcknowledgedBy())
            .resolvedAt(e.getResolvedAt())
            .resolvedBy(e.getResolvedBy())
            .createdAt(e.getCreatedAt()).build();
    }

    private UUID resolveTripId(LiveLocation latestLocation) {
        if (latestLocation == null || latestLocation.getSessionId() == null) return null;
        return tripSessionRepo.findById(latestLocation.getSessionId())
                .map(TripSession::getTripId)
                .orElse(null);
    }

    private void sendSosNotifications(SosEvent sos, UUID userId, boolean escalation) {
        String userName = profileRepo.findById(userId)
                .map(Profile::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("A GOLA traveler");
        String title = escalation ? "SOS Alert - Still Active" : "SOS Alert";
        String body = userName + " needs help. Tap to view location.";
        String locationLink = buildLocationLink(sos);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "SOS");
        data.put("sosId", sos.getId().toString());
        data.put("tripId", sos.getTripId() != null ? sos.getTripId().toString() : "");
        data.put("latitude", sos.getLatitude() != null ? sos.getLatitude().toString() : "");
        data.put("longitude", sos.getLongitude() != null ? sos.getLongitude().toString() : "");
        data.put("lat", sos.getLatitude() != null ? sos.getLatitude().toString() : "");
        data.put("lng", sos.getLongitude() != null ? sos.getLongitude().toString() : "");
        data.put("triggeredByUserId", userId.toString());
        data.put("title", title);
        data.put("body", body);
        data.put("url", locationLink);

        Map<UUID, MemberRole> recipientRoles = resolveRecipientRoles(sos);
        for (var entry : recipientRoles.entrySet()) {
            UUID recipientId = entry.getKey();
            MemberRole role = entry.getValue();

            if (recipientId.equals(userId)) {
                log.info("Skipping triggering user self notification: userId={}", recipientId);
                continue;
            }

            Profile recipient = profileRepo.findById(recipientId).orElse(null);
            List<DeviceToken> tokens = deviceTokenRepo.findByUserId(recipientId);
            if (tokens.isEmpty()) {
                log.info("No FCM token for trip member: sosId={} tripId={} triggeringUserId={} userId={} role={} email={}",
                        sos.getId(), sos.getTripId(), userId, recipientId, role,
                        recipient != null ? recipient.getEmail() : null);
            }
            for (DeviceToken token : tokens) {
                log.info("Device token found for trip member: sosId={} tripId={} userId={} role={} tokenPrefix={}",
                        sos.getId(), sos.getTripId(), recipientId, role, tokenPrefix(token.getToken()));
                log.info("FCM recipients final: sosId={} tripId={} recipientUserId={} role={} tokenPrefix={}",
                        sos.getId(), sos.getTripId(), recipientId, role, tokenPrefix(token.getToken()));
                log.info("SOS FCM dispatch: sosId={} tripId={} triggeringUserId={} recipientUserId={} role={} email={} tokenPrefix={} title='{}' body='{}' data={}",
                        sos.getId(), sos.getTripId(), userId, recipientId, role,
                        recipient != null ? recipient.getEmail() : null,
                        tokenPrefix(token.getToken()), title, body, data);
                fcmService.sendPushNotification(token.getToken(), title, body, data);
            }
        }

        var contacts = contactRepo.findByUserIdOrderByPriorityAsc(userId);
        for (var c : contacts) {
            String sms = title + ": " + body + " " + locationLink;
            log.info("Dispatching SOS SMS to {} for user {}", c.getPhone(), userId);
            twilioSmsService.sendSms(c.getPhone(), sms);
        }
    }

    private Map<UUID, MemberRole> resolveRecipientRoles(SosEvent sos) {
        Map<UUID, MemberRole> recipients = new LinkedHashMap<>();
        if (sos.getTripId() == null) {
            log.info("SOS has no tripId; no trip member FCM recipients: sosId={}", sos.getId());
            return recipients;
        }

        List<TripMember> members = tripMemberRepo.findByTripId(sos.getTripId());
        if (members.isEmpty()) {
            log.info("Trip members found: sosId={} tripId={} members=[]", sos.getId(), sos.getTripId());
        }
        for (TripMember member : members) {
            log.info("Trip members found: sosId={} tripId={} userId={} role={}",
                    sos.getId(), sos.getTripId(), member.getUserId(), member.getRole());
            recipients.put(member.getUserId(), member.getRole());
        }

        return recipients;
    }

    private String tokenPrefix(String token) {
        if (token == null) return "";
        return token.substring(0, Math.min(12, token.length()));
    }

    private String buildLocationLink(SosEvent sos) {
        if (sos.getLatitude() != null && sos.getLongitude() != null) {
            return "https://www.google.com/maps?q=" + sos.getLatitude() + "," + sos.getLongitude();
        }
        return "/sos";
    }
}
