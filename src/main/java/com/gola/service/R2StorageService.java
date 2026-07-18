package com.gola.service;

import com.gola.config.GolaProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Legacy Cloudflare R2 storage client kept for rollback. Not registered as a Spring bean.
 * Active storage implementation: {@link SupabaseStorageService}.
 */
@Slf4j
@RequiredArgsConstructor
public class R2StorageService {

    private final GolaProperties properties;
    private S3Client s3Client;

    @PostConstruct
    public void init() {
        GolaProperties.R2 r2 = properties.getR2();
        if (r2 == null
                || !hasText(r2.getAccessKeyId())
                || !hasText(r2.getSecretAccessKey())
                || !hasText(r2.getEndpoint())
                || !hasText(r2.getBucketName())) {
            log.warn("Cloudflare R2 properties are NOT fully configured — R2 uploads will fail. "
                    + "Resolved values: endpoint=[{}], bucket=[{}], accessKeyId=[{}], publicUrl=[{}]",
                    r2 != null ? r2.getEndpoint() : "null",
                    r2 != null ? r2.getBucketName() : "null",
                    r2 != null ? mask(r2.getAccessKeyId()) : "null",
                    r2 != null ? r2.getPublicUrl() : "null");
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
            log.info("Cloudflare R2 S3Client initialized: endpoint=[{}], bucket=[{}], publicUrl=[{}]",
                    r2.getEndpoint(), r2.getBucketName(), r2.getPublicUrl());
        } catch (Exception e) {
            log.error("Failed to initialize Cloudflare R2 S3Client: endpoint=[{}], bucket=[{}], error={}",
                    r2.getEndpoint(), r2.getBucketName(), e.getMessage(), e);
        }
    }

    public String uploadFile(String key, InputStream inputStream, String contentType, long contentLength) {
        GolaProperties.R2 r2 = properties.getR2();
        if (s3Client == null) {
            throw new IllegalStateException(
                "R2 Storage client is not initialized. Check that R2_ACCESS_KEY_ID, "
                + "R2_SECRET_ACCESS_KEY, R2_BUCKET_NAME, R2_ENDPOINT environment variables are set.");
        }
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(r2.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("Successfully uploaded file to R2: bucket=[{}], key=[{}], size={}", r2.getBucketName(), key, contentLength);
            return getPublicUrl(key);
        } catch (Exception e) {
            log.error("Failed to upload file to R2: bucket=[{}], endpoint=[{}], key=[{}], error={}",
                    r2.getBucketName(), r2.getEndpoint(), key, e.getMessage(), e);
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
        if (!hasText(publicUrl)) {
            return "/" + key;
        }
        if (publicUrl.endsWith("/")) {
            return publicUrl + key;
        }
        return publicUrl + "/" + key;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Masks a credential for safe logging: shows first 4 chars then asterisks. */
    private static String mask(String value) {
        if (value == null) return "null";
        if (value.isBlank()) return "(empty)";
        if (value.length() <= 4) return "****";
        return value.substring(0, 4) + "****";
    }
}
