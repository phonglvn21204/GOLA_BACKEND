package com.gola.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {

            String firebaseJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

            InputStream credentials;

            if (firebaseJson != null && !firebaseJson.isBlank()) {

                credentials = new ByteArrayInputStream(
                        firebaseJson.getBytes(StandardCharsets.UTF_8)
                );

                log.info("Loading Firebase credentials from ENV");

            } else {

                credentials = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("firebase-service-account.json");

                if (credentials == null) {
                    log.warn("firebase-service-account.json not found. FCM disabled.");
                    return;
                }

                log.info("Loading Firebase credentials from classpath");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();

            FirebaseApp.initializeApp(options);

            log.info("Firebase Admin SDK initialized successfully.");

        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }
}