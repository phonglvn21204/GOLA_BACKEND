package com.gola;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class R2ConnectionTest {

    private Map<String, String> loadEnv() throws Exception {
        Map<String, String> env = new HashMap<>();
        File file = new File(".env");
        if (!file.exists()) {
            // Thử tìm ở thư mục cha nếu chạy từ thư mục con
            file = new File("../.env");
        }
        if (!file.exists()) {
            throw new RuntimeException("Cannot find .env file at " + new File(".").getAbsolutePath());
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    env.put(key, val);
                }
            }
        }
        return env;
    }

    @Test
    public void testR2Connection() {
        System.out.println("=== Starting Java SDK R2 Connection Test ===");
        Map<String, String> envVars;
        try {
            envVars = loadEnv();
        } catch (Exception e) {
            System.err.println("Failed to load .env file: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        String accessKeyId = envVars.get("R2_ACCESS_KEY_ID");
        String secretAccessKey = envVars.get("R2_SECRET_ACCESS_KEY");
        String bucketName = envVars.get("R2_BUCKET_NAME");
        String endpoint = envVars.get("R2_ENDPOINT");
        String publicUrl = envVars.get("R2_PUBLIC_URL");

        System.out.println("Loaded configurations:");
        System.out.println("  R2_ACCESS_KEY_ID length: " + (accessKeyId != null ? accessKeyId.length() : "null"));
        System.out.println("  R2_SECRET_ACCESS_KEY length: " + (secretAccessKey != null ? secretAccessKey.length() : "null"));
        System.out.println("  R2_BUCKET_NAME: " + bucketName);
        System.out.println("  R2_ENDPOINT: " + endpoint);
        System.out.println("  R2_PUBLIC_URL: " + publicUrl);

        if (accessKeyId == null || secretAccessKey == null || bucketName == null || endpoint == null) {
            System.err.println("Required R2 properties are missing in .env");
            return;
        }

        // Test khởi tạo với Region.US_EAST_1 (giống cấu hình hiện tại trong R2StorageService)
        System.out.println("\n--- Attempting with Region.US_EAST_1 (Current Config) ---");
        tryS3ClientConnection(endpoint, accessKeyId, secretAccessKey, Region.US_EAST_1, bucketName);

        // Test khởi tạo với Region auto (Region.of("auto"))
        System.out.println("\n--- Attempting with Region.of(\"auto\") ---");
        tryS3ClientConnection(endpoint, accessKeyId, secretAccessKey, Region.of("auto"), bucketName);
    }

    private void tryS3ClientConnection(String endpoint, String accessKey, String secretKey, Region region, String bucketName) {
        S3Client s3Client = null;
        try {
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .region(region)
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();

            System.out.println("S3Client built successfully. Running headBucket...");
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
            s3Client.headBucket(headBucketRequest);
            System.out.println("  SUCCESS: headBucket completed successfully!");

            System.out.println("Running listObjectsV2...");
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder().bucket(bucketName).maxKeys(5).build();
            ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);
            System.out.println("  SUCCESS: listObjectsV2 completed. Found " + listObjectsResponse.contents().size() + " items.");

            // Thử upload 1 file test nhỏ dùng String
            String testKey = "test-connection-" + System.currentTimeMillis() + ".txt";
            System.out.println("Attempting to upload file (String): " + testKey);
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKey)
                    .contentType("text/plain")
                    .build();

            String content = "Gola R2 Connection Test Successful!";
            s3Client.putObject(putObjectRequest, RequestBody.fromString(content, StandardCharsets.UTF_8));
            System.out.println("  SUCCESS: File uploaded successfully: " + testKey);

            // Thử upload file bằng InputStream giống R2StorageService
            String testKeyStream = "test-stream-" + System.currentTimeMillis() + ".txt";
            System.out.println("Attempting to upload file (InputStream): " + testKeyStream);
            java.io.ByteArrayInputStream byteStream = new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            PutObjectRequest putObjectRequestStream = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(testKeyStream)
                    .contentType("text/plain")
                    .build();
            s3Client.putObject(putObjectRequestStream, RequestBody.fromInputStream(byteStream, (long) content.getBytes(StandardCharsets.UTF_8).length));
            System.out.println("  SUCCESS: InputStream file uploaded successfully: " + testKeyStream);

        } catch (S3Exception e) {
            System.err.println("  FAIL: AWS S3Exception occurred!");
            System.err.println("    Status Code: " + e.statusCode());
            System.err.println("    Error Message: " + e.getMessage());
            System.err.println("    AWS Error Code: " + e.awsErrorDetails().errorCode());
            System.err.println("    AWS Service Name: " + e.awsErrorDetails().serviceName());
            System.err.println("    Full Exception Details:");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("  FAIL: General Exception occurred!");
            System.err.println("    Class: " + e.getClass().getName());
            System.err.println("    Message: " + e.getMessage());
            System.err.println("    Cause: " + (e.getCause() != null ? e.getCause().toString() : "null"));
            System.err.println("    Full Stack Trace:");
            e.printStackTrace();
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }
}
