package com.gola.service;

import com.gola.config.GolaProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SupabaseStorageService {

    private final GolaProperties properties;
    private final RestTemplate restTemplate;

    private boolean configured;

    public SupabaseStorageService(
            GolaProperties properties,
            @Qualifier("supabaseRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        GolaProperties.Supabase supabase = properties.getSupabase();
        String bucket = bucketName();
        if (supabase == null
                || !hasText(supabase.getUrl())
                || !hasText(supabase.getServiceRoleKey())
                || !hasText(bucket)) {
            log.warn("Supabase Storage properties are NOT fully configured — uploads will fail. "
                    + "Resolved values: url=[{}], bucket=[{}], serviceRoleKey=[{}]",
                    supabase != null ? supabase.getUrl() : "null",
                    bucket,
                    supabase != null ? mask(supabase.getServiceRoleKey()) : "null");
            configured = false;
            return;
        }
        configured = true;
        log.info("Supabase Storage initialized: url=[{}], bucket=[{}]",
                normalizeBaseUrl(supabase.getUrl()), bucket);
    }

    public String uploadFile(String key, InputStream inputStream, String contentType, long contentLength) {
        ensureConfigured();
        GolaProperties.Supabase supabase = properties.getSupabase();
        String bucket = bucketName();
        String uploadUrl = objectUrl(supabase.getUrl(), bucket, key);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(supabase.getServiceRoleKey().trim());
        headers.setContentType(MediaType.parseMediaType(hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE));

        try {
            byte[] body = readBytes(inputStream, contentLength);
            HttpEntity<byte[]> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, request, String.class);
            log.info("Successfully uploaded file to Supabase Storage: bucket=[{}], key=[{}], size={}, status={}",
                    bucket, key, body.length, response.getStatusCode().value());
            return getPublicUrl(key);
        } catch (HttpStatusCodeException e) {
            log.error("Supabase Storage upload failed: bucket=[{}], key=[{}], status={}, responseBody={}",
                    bucket, key, e.getStatusCode().value(), safeResponseBody(e.getResponseBodyAsString()), e);
            throw new RuntimeException("Could not store uploaded file: Supabase Storage returned HTTP "
                    + e.getStatusCode().value(), e);
        } catch (IOException e) {
            log.error("Failed to read upload stream for Supabase Storage: bucket=[{}], key=[{}], error={}",
                    bucket, key, e.getMessage(), e);
            throw new RuntimeException("Could not store uploaded file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase Storage: bucket=[{}], key=[{}], error={}",
                    bucket, key, e.getMessage(), e);
            throw new RuntimeException("Could not store uploaded file: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String key) {
        if (!configured) {
            log.warn("Supabase Storage is not configured. Skipping deletion of key={}", key);
            return;
        }
        GolaProperties.Supabase supabase = properties.getSupabase();
        String bucket = bucketName();
        String deleteUrl = objectUrl(supabase.getUrl(), bucket, key);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(supabase.getServiceRoleKey().trim());

        try {
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            log.info("Successfully deleted file from Supabase Storage: bucket={}, key={}", bucket, key);
        } catch (HttpStatusCodeException e) {
            log.error("Supabase Storage delete failed: bucket=[{}], key=[{}], status={}, responseBody={}",
                    bucket, key, e.getStatusCode().value(), safeResponseBody(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            log.error("Failed to delete file from Supabase Storage: key={}, error={}", key, e.getMessage(), e);
        }
    }

    public String getPublicUrl(String key) {
        GolaProperties.Supabase supabase = properties.getSupabase();
        if (supabase == null || !hasText(supabase.getUrl()) || !hasText(bucketName())) {
            return "/" + key;
        }
        String baseUrl = normalizeBaseUrl(supabase.getUrl());
        return baseUrl + "/storage/v1/object/public/" + bucketName() + "/" + encodePath(key);
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "Supabase Storage is not configured. Set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, "
                            + "and optionally SUPABASE_STORAGE_BUCKET (default: trip-memory-photos).");
        }
    }

    private String bucketName() {
        GolaProperties.Supabase supabase = properties.getSupabase();
        if (supabase == null || supabase.getStorage() == null || !hasText(supabase.getStorage().getBucket())) {
            return "trip-memory-photos";
        }
        return supabase.getStorage().getBucket().trim();
    }

    private static String objectUrl(String supabaseUrl, String bucket, String key) {
        String baseUrl = normalizeBaseUrl(supabaseUrl);
        return baseUrl + "/storage/v1/object/" + bucket + "/" + encodePath(key);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.trim().replaceAll("/+$", "");
    }

    private static String encodePath(String key) {
        return Arrays.stream(key.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static byte[] readBytes(InputStream inputStream, long contentLength) throws IOException {
        if (contentLength >= 0 && contentLength <= Integer.MAX_VALUE) {
            byte[] buffer = new byte[(int) contentLength];
            int offset = 0;
            while (offset < buffer.length) {
                int read = inputStream.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset == buffer.length) {
                return buffer;
            }
            byte[] partial = new byte[offset];
            System.arraycopy(buffer, 0, partial, 0, offset);
            return partial;
        }
        return inputStream.readAllBytes();
    }

    private static String safeResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return "(empty)";
        }
        String trimmed = body.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "..." : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String mask(String value) {
        if (value == null) {
            return "null";
        }
        if (value.isBlank()) {
            return "(empty)";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}
