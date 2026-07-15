package com.gola.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {
    private static final String DEFAULT_SERVICE_ACCOUNT = "src/main/resources/firebase-service-account.json";

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        String path = System.getenv().getOrDefault("FIREBASE_SERVICE_ACCOUNT_PATH", DEFAULT_SERVICE_ACCOUNT);
        try (InputStream credentialsStream = openCredentials(path)) {
            if (credentialsStream == null) {
                log.warn("Firebase service account not found at '{}'; FCM is disabled", path);
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized");
        } catch (Exception e) {
            log.warn("Firebase Admin SDK initialization failed: {}", e.getMessage());
        }
    }

    private InputStream openCredentials(String path) throws Exception {
        File file = new File(path);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (resource != null) return resource;
        return Thread.currentThread().getContextClassLoader().getResourceAsStream("firebase-service-account.json");
    }
}
