package com.gola.service;

import com.gola.config.GolaProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final GolaProperties properties;
    private S3Client s3Client;

    @PostConstruct
    public void init() {
        GolaProperties.R2 r2 = properties.getR2();
        if (r2 == null || r2.getAccessKeyId() == null || r2.getSecretAccessKey() == null) {
            log.warn("Cloudflare R2 properties are not configured. R2 uploads will fail.");
            return;
        }
        try {
            s3Client = S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(r2.getAccessKeyId(), r2.getSecretAccessKey())
                ))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build())
                .build();
            log.info("Cloudflare R2 S3Client initialized successfully with endpoint: {}", r2.getEndpoint());
        } catch (Exception e) {
            log.error("Failed to initialize Cloudflare R2 S3Client: {}", e.getMessage(), e);
        }
    }

    public String uploadFile(String key, InputStream inputStream, String contentType, long contentLength) {
        GolaProperties.R2 r2 = properties.getR2();
        if (s3Client == null) {
            throw new IllegalStateException("R2 Storage client is not initialized.");
        }
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(r2.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("Successfully uploaded file to R2: bucket={}, key={}, size={}", r2.getBucketName(), key, contentLength);
            return getPublicUrl(key);
        } catch (Exception e) {
            log.error("Failed to upload file to R2: key={}, error={}", key, e.getMessage(), e);
            throw new RuntimeException("Could not store uploaded file: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String key) {
        GolaProperties.R2 r2 = properties.getR2();
        if (s3Client == null) {
            log.warn("R2 Storage client not initialized. Skipping deletion of key={}", key);
            return;
        }
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(r2.getBucketName())
                .key(key)
                .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file from R2: bucket={}, key={}", r2.getBucketName(), key);
        } catch (Exception e) {
            log.error("Failed to delete file from R2: key={}, error={}", key, e.getMessage(), e);
        }
    }

    public String getPublicUrl(String key) {
        GolaProperties.R2 r2 = properties.getR2();
        String publicUrl = r2.getPublicUrl();
        if (publicUrl == null) {
            return "/" + key;
        }
        if (publicUrl.endsWith("/")) {
            return publicUrl + key;
        }
        return publicUrl + "/" + key;
    }
}
