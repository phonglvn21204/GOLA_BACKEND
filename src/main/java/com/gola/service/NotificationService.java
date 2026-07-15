package com.gola.service;

import com.gola.dto.notification.NotificationListResponse;
import com.gola.dto.notification.NotificationResponse;
import com.gola.entity.Notification;
import com.gola.entity.enums.NotifType;
import com.gola.exception.GolaException;
import com.gola.repository.NotificationRepository;
import com.gola.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final SimpMessagingTemplate   messaging;
    private final ProfileRepository profileRepo;

    @Async
    @Transactional
    public void send(UUID userId, String type, String title, String body, Map<String, Object> payload) {
        create(userId, null, NotifType.valueOf(type), type, title, body, null, null, null, payload);
    }

    public NotificationListResponse list(UUID userId, int page, int size, boolean unreadOnly) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> notifications = unreadOnly
                ? repo.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return NotificationListResponse.builder()
                .content(notifications.getContent().stream().map(this::mapToResponse).toList())
                .unreadCount(repo.countByUserIdAndReadAtIsNull(userId))
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .first(notifications.isFirst())
                .last(notifications.isLast())
                .build();
    }

    @Transactional
    public void notifySocial(UUID receiverId, UUID actorId, String notificationType, String title, String body, String targetType, UUID targetId, String targetUrl) {
        create(receiverId, actorId, NotifType.SOCIAL, notificationType, title, body, targetType, targetId, targetUrl, null);
    }

    @Transactional
    public void notifyQuest(UUID userId, String notificationType, String title, String body, UUID questId, String targetUrl) {
        create(userId, null, NotifType.QUEST, notificationType, title, body, "QUEST", questId, targetUrl, null);
    }

    @Transactional
    public void notifyTrip(UUID userId, String notificationType, String title, String body, UUID tripId, String targetUrl) {
        create(userId, null, NotifType.TRIP, notificationType, title, body, "TRIP", tripId, targetUrl, null);
    }

    @Transactional
    public void notifyPayment(UUID userId, String notificationType, String title, String body, UUID orderId, String targetUrl) {
        create(userId, null, NotifType.PAYMENT, notificationType, title, body, "ORDER", orderId, targetUrl, null);
    }

    @Transactional
    public NotificationResponse create(
            UUID receiverId,
            UUID actorId,
            NotifType type,
            String notificationType,
            String title,
            String body,
            String targetType,
            UUID targetId,
            String targetUrl,
            Map<String, Object> extraPayload
    ) {
        if (receiverId == null) return null;
        if (actorId != null && actorId.equals(receiverId)) {
            return null;
        }

        Map<String, Object> payload = new HashMap<>();
        if (extraPayload != null) payload.putAll(extraPayload);
        payload.put("notificationType", notificationType);
        if (actorId != null) {
            payload.put("actorId", actorId.toString());
            profileRepo.findById(actorId).ifPresent(actor -> {
                payload.put("actorName", safeActorName(actor.getDisplayName()));
                if (actor.getAvatarUrl() != null && !actor.getAvatarUrl().isBlank()) {
                    payload.put("actorAvatarUrl", actor.getAvatarUrl());
                }
            });
        }
        if (targetType != null) payload.put("targetType", targetType);
        if (targetId != null) payload.put("targetId", targetId.toString());
        if (targetUrl != null && !targetUrl.isBlank()) payload.put("targetUrl", targetUrl);

        var n = Notification.builder()
            .userId(receiverId)
            .type(type)
            .title(title)
            .body(body)
            .payload(payload)
            .build();
        Notification saved = repo.save(n);
        try {
            messaging.convertAndSendToUser(receiverId.toString(), "/queue/notifications", mapToResponse(saved));
        } catch (Exception e) {
            log.debug("Notification websocket publish skipped for user {}: {}", receiverId, e.getMessage());
        }
        log.info("Notification stored for user:{} type:{} subtype:{}", receiverId, type, notificationType);
        return mapToResponse(saved);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repo.markAllRead(userId, Instant.now());
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = repo.findById(notificationId)
                .orElseThrow(() -> GolaException.notFound("Notification"));
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw GolaException.forbidden();
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = repo.save(notification);
        }
        return mapToResponse(notification);
    }

    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        Notification notification = repo.findById(notificationId)
                .orElseThrow(() -> GolaException.notFound("Notification"));
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw GolaException.forbidden();
        }
        repo.delete(notification);
    }

    public NotificationResponse mapToResponse(Notification notification) {
        Map<String, Object> payload = notification.getPayload() != null ? notification.getPayload() : Map.of();
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .notificationType(text(payload.get("notificationType"), notification.getType().name()))
                .title(notification.getTitle())
                .message(notification.getBody())
                .body(notification.getBody())
                .actorId(uuid(payload.get("actorId")))
                .actorName(text(payload.get("actorName"), null))
                .actorAvatarUrl(text(payload.get("actorAvatarUrl"), null))
                .targetType(text(payload.get("targetType"), null))
                .targetId(uuid(payload.get("targetId")))
                .targetUrl(text(payload.get("targetUrl"), null))
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .payload(payload)
                .build();
    }

    private String safeActorName(String displayName) {
        return displayName == null || displayName.isBlank() ? "Một thành viên GOLA" : displayName.trim();
    }

    private String text(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private UUID uuid(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
