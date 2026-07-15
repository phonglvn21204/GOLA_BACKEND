package com.gola.dto.notification;

import com.gola.entity.enums.NotifType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private NotifType type;
    private String notificationType;
    private String title;
    private String message;
    private String body;
    private UUID actorId;
    private String actorName;
    private String actorAvatarUrl;
    private String targetType;
    private UUID targetId;
    private String targetUrl;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
    private Map<String, Object> payload;
}
