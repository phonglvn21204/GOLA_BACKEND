package com.gola.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class FcmService {

    public void sendPushNotification(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FCM skipped because FirebaseApp is not initialized");
            return;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String messageId = FirebaseMessaging.getInstance().send(builder.build());
            log.debug("FCM sent to token prefix {}: {}", token.substring(0, Math.min(8, token.length())), messageId);
        } catch (Exception e) {
            log.warn("FCM send failed: {}", e.getMessage());
        }
    }
}
