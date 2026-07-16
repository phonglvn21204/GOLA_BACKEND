package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.exception.GolaException;
import com.gola.security.SecurityUtils;
import com.gola.service.R2StorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/uploads")
@Tag(name = "Uploads")
@Slf4j
@RequiredArgsConstructor
public class UploadController {
    private final R2StorageService r2StorageService;
    private static final long COMMUNITY_MEDIA_MAX_BYTES = 15L * 1024 * 1024;
    private static final String IMAGE_TOO_LARGE_MESSAGE = "Ảnh quá lớn. Vui lòng chọn ảnh nhỏ hơn 15MB.";
    private static final int THUMBNAIL_MAX_WIDTH = 480;
    private static final int MEDIUM_MAX_WIDTH = 1080;
    private static final CacheControl UPLOAD_CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePublic();

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

    @PostMapping(value = "/community-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadCommunityMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tripId", required = false) UUID tripId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (file == null || file.isEmpty()) {
            throw GolaException.badRequest("No file uploaded");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "photobooth.png" : file.getOriginalFilename());
        log.info("Community media upload received: userId={}, tripId={}, fileName={}, contentType={}, size={}",
                userId, tripId, original, contentType.isBlank() ? "unknown" : contentType, file.getSize());
        if (!isSupportedImage(original, contentType)) {
            throw GolaException.badRequest("Only image files are allowed");
        }
        if (file.getSize() > COMMUNITY_MEDIA_MAX_BYTES) {
            log.warn("Community media upload rejected as too large: userId={}, tripId={}, fileName={}, size={}, limit={}",
                    userId, tripId, original, file.getSize(), COMMUNITY_MEDIA_MAX_BYTES);
            throw new GolaException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", IMAGE_TOO_LARGE_MESSAGE);
        }

        String ext = extensionFrom(original, contentType);
        String fileName = UUID.randomUUID() + ext;
        Path tempOriginal = null;
        try {
            tempOriginal = Files.createTempFile("gola-orig-", ext);
            Files.copy(file.getInputStream(), tempOriginal, StandardCopyOption.REPLACE_EXISTING);

            String key = "community-media/" + userId + "/" + fileName;
            String publicUrl = r2StorageService.uploadFile(key, Files.newInputStream(tempOriginal), contentType, file.getSize());

            MediaVariants variants = generateVariants(tempOriginal, userId, fileName);
            log.info("Community media upload stored: userId={}, tripId={}, fileName={}, publicUrl={}",
                    userId, tripId, fileName, publicUrl);
            Map<String, String> response = new HashMap<>();
            response.put("publicUrl", publicUrl);
            response.put("originalUrl", publicUrl);
            response.put("fileName", fileName);
            response.put("thumbnailUrl", variants.thumbnailUrl() != null ? variants.thumbnailUrl() : publicUrl);
            response.put("mediumUrl", variants.mediumUrl() != null ? variants.mediumUrl() : publicUrl);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (IOException ex) {
            log.warn("Community media upload failed: userId={}, tripId={}, fileName={}, contentType={}, size={}, reason={}",
                    userId, tripId, original, contentType.isBlank() ? "unknown" : contentType, file.getSize(), ex.getMessage());
            throw GolaException.badRequest("Could not store uploaded image. Please try again.");
        } finally {
            if (tempOriginal != null) {
                try {
                    Files.deleteIfExists(tempOriginal);
                } catch (IOException e) {
                    log.warn("Failed to delete temp original file: {}", e.getMessage());
                }
            }
        }
    }

    @GetMapping("/community-media/{userId}/{fileName}")
    public ResponseEntity<Resource> communityMedia(@PathVariable String userId, @PathVariable String fileName) throws Exception {
        Path root = Path.of("uploads", "community-media").toAbsolutePath().normalize();
        Path file = root.resolve(userId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .cacheControl(UPLOAD_CACHE)
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/trip-memory-photos/{tripId}/{fileName}")
    public ResponseEntity<Resource> tripMemoryPhoto(@PathVariable String tripId, @PathVariable String fileName) throws Exception {
        Path root = Path.of("uploads", "trip-memory-photos").toAbsolutePath().normalize();
        Path file = root.resolve(tripId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .cacheControl(UPLOAD_CACHE)
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/avatars/{userId}/{fileName}")
    public ResponseEntity<Resource> avatar(@PathVariable String userId, @PathVariable String fileName) throws Exception {
        Path root = Path.of("uploads", "avatars").toAbsolutePath().normalize();
        Path file = root.resolve(userId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .cacheControl(UPLOAD_CACHE)
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/quests/{userId}/{fileName}")
    public ResponseEntity<Resource> questProof(@PathVariable String userId, @PathVariable String fileName) throws Exception {
        Path root = Path.of("uploads", "quests").toAbsolutePath().normalize();
        Path file = root.resolve(userId).resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .cacheControl(UPLOAD_CACHE)
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    private MediaVariants generateVariants(Path tempOriginal, UUID userId, String fileName) {
        Path tempThumb = null;
        Path tempMedium = null;
        try {
            BufferedImage source = ImageIO.read(tempOriginal.toFile());
            if (source == null) {
                log.warn("Community media variant generation skipped: ImageIO cannot decode fileName={}", fileName);
                return MediaVariants.empty();
            }
            String baseName = stripExtension(fileName);
            tempThumb = Files.createTempFile("gola-thumb-", ".jpg");
            tempMedium = Files.createTempFile("gola-med-", ".jpg");

            writeJpegVariant(source, tempThumb, THUMBNAIL_MAX_WIDTH, 0.78f);
            writeJpegVariant(source, tempMedium, MEDIUM_MAX_WIDTH, 0.84f);

            String thumbKey = "community-media/" + userId + "/" + baseName + "-thumb.jpg";
            String mediumKey = "community-media/" + userId + "/" + baseName + "-medium.jpg";

            String thumbnailUrl = r2StorageService.uploadFile(thumbKey, Files.newInputStream(tempThumb), "image/jpeg", Files.size(tempThumb));
            String mediumUrl = r2StorageService.uploadFile(mediumKey, Files.newInputStream(tempMedium), "image/jpeg", Files.size(tempMedium));

            return new MediaVariants(thumbnailUrl, mediumUrl);
        } catch (Exception ex) {
            log.warn("Community media variant generation failed: fileName={}, reason={}", fileName, ex.getMessage());
            return MediaVariants.empty();
        } finally {
            if (tempThumb != null) {
                try {
                    Files.deleteIfExists(tempThumb);
                } catch (IOException e) {
                    log.warn("Failed to delete temp thumb file: {}", e.getMessage());
                }
            }
            if (tempMedium != null) {
                try {
                    Files.deleteIfExists(tempMedium);
                } catch (IOException e) {
                    log.warn("Failed to delete temp medium file: {}", e.getMessage());
                }
            }
        }
    }

    private void writeJpegVariant(BufferedImage source, Path target, int maxWidth, float quality) throws IOException {
        BufferedImage scaled = scaleToMaxWidth(source, maxWidth);
        BufferedImage rgb = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(scaled, 0, 0, null);
        g.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(rgb, "jpg", target.toFile());
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage scaleToMaxWidth(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int targetWidth = maxWidth;
        int targetHeight = Math.max(1, Math.round((float) source.getHeight() * targetWidth / source.getWidth()));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private record MediaVariants(String thumbnailUrl, String mediumUrl) {
        static MediaVariants empty() {
            return new MediaVariants(null, null);
        }
    }

    private String extensionFrom(String fileName, String contentType) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(png|jpe?g|webp|gif)")) return ext;
        }
        if (contentType.contains("jpeg")) return ".jpg";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("gif")) return ".gif";
        return ".png";
    }

    private boolean isSupportedImage(String fileName, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|webp|gif)$");
    }
}
