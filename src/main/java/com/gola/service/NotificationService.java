package com.gola.service;

import com.gola.entity.Notification;
import com.gola.entity.enums.NotifType;
import com.gola.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final SimpMessagingTemplate   messaging;

    @Async
    @Transactional
    public void send(UUID userId, String type, String title, String body, Map<String, Object> payload) {
        var n = Notification.builder()
            .userId(userId)
            .type(NotifType.valueOf(type))
            .title(title)
            .body(body)
            .payload(payload)
            .build();
        repo.save(n);
        messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", n);
        log.info("Notification sent to user:{} type:{}", userId, type);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repo.markAllRead(userId, Instant.now());
    }
}