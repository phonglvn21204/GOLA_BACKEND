package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/uploads")
@Tag(name = "Uploads")
public class UploadController {

    @PostMapping("/signed-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> signedUrl(@RequestBody SignedUrlRequest req) {
        String key = "uploads/" + UUID.randomUUID() + "/" + (req.getFileName() == null ? "file" : req.getFileName());
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "key", key,
                "uploadUrl", "/api/uploads/mock/" + encodedKey,
                "publicUrl", "https://picsum.photos/seed/" + encodedKey + "/1200/800"
        )));
    }

    @Data
    public static class SignedUrlRequest {
        private String fileName;
        private String contentType;
    }
}
