package com.gola.service;

import com.gola.config.GolaProperties;
import com.gola.entity.Album;
import com.gola.entity.Post;
import com.gola.entity.Profile;
import com.gola.entity.Trip;
import com.gola.entity.TripMemory;
import com.gola.entity.TripShare;
import com.gola.entity.TripStop;
import com.gola.exception.GolaException;
import com.gola.repository.AlbumRepository;
import com.gola.repository.PostRepository;
import com.gola.repository.ProfileRepository;
import com.gola.repository.TripMemoryRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicShareService {
    private final TripRepository tripRepository;
    private final TripShareRepository tripShareRepository;
    private final TripMemoryRepository tripMemoryRepository;
    private final AlbumRepository albumRepository;
    private final PostRepository postRepository;
    private final ProfileRepository profileRepository;
    private final GolaProperties properties;

    @Transactional(readOnly = true)
    public SharePreview tripPreview(UUID tripId, String token, String canonicalUrl) {
        Trip trip = tripRepository.findActiveById(tripId).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!trip.isPublic() && !hasValidTripToken(tripId, token)) {
            throw GolaException.notFound("Share page");
        }

        String route = joinNonBlank(trip.getOrigin(), trip.getDestination(), " → ");
        String dates = trip.getStartDate() != null
            ? trip.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE) +
                (trip.getEndDate() != null ? " - " + trip.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
            : "";
        String description = firstText(
            trip.getDescription(),
            joinNonBlank(route, dates, " · "),
            "Khám phá lịch trình du lịch được chia sẻ từ GOLA."
        );
        String imageUrl = firstText(trip.getCoverUrl(), firstStopImage(trip));
        String appUrl = frontendUrl("/post-trip?id=" + tripId);
        return new SharePreview(
            firstText(trip.getTitle(), "GOLA Trip"),
            excerpt(description, 180),
            absoluteUrl(imageUrl),
            canonicalUrl,
            appUrl
        );
    }

    @Transactional(readOnly = true)
    public SharePreview memoryPreview(UUID memoryId, String canonicalUrl) {
        TripMemory memory = tripMemoryRepository.findById(memoryId).orElseThrow(() -> GolaException.notFound("Memory"));
        if (!"SHARED".equalsIgnoreCase(memory.getShareStatus())) {
            throw GolaException.notFound("Share page");
        }
        Trip trip = tripRepository.findActiveById(memory.getTripId()).orElse(null);
        Album album = memory.getAlbumId() != null ? albumRepository.findById(memory.getAlbumId()).orElse(null) : null;
        String appUrl = frontendUrl("/post-trip?id=" + memory.getTripId());
        return new SharePreview(
            firstText(memory.getTitle(), trip != null ? trip.getTitle() : null, "GOLA Trip Memory"),
            excerpt(firstText(memory.getSummary(), trip != null ? trip.getDescription() : null, "Kỷ niệm chuyến đi được chia sẻ từ GOLA."), 180),
            absoluteUrl(album != null ? album.getCoverUrl() : trip != null ? trip.getCoverUrl() : null),
            canonicalUrl,
            appUrl
        );
    }

    @Transactional(readOnly = true)
    public SharePreview postPreview(UUID postId, String canonicalUrl) {
        Post post = postRepository.findById(postId).orElseThrow(() -> GolaException.notFound("Post"));
        if (post.isHidden()) {
            throw GolaException.notFound("Share page");
        }
        Profile author = profileRepository.findById(post.getAuthorId()).orElse(null);
        String authorName = author != null ? author.getDisplayName() : null;
        String imageUrl = firstArray(post.getMediumUrls())
            .or(() -> firstArray(post.getThumbnailUrls()))
            .or(() -> firstArray(post.getMediaUrls()))
            .orElse(null);
        String title = firstText(authorName != null ? authorName + " chia sẻ trên GOLA" : null, "GOLA Community");
        String appUrl = frontendUrl("/community/posts/" + postId);
        return new SharePreview(
            title,
            excerpt(firstText(post.getBody(), "Một khoảnh khắc du lịch được chia sẻ trong cộng đồng GOLA."), 180),
            absoluteUrl(imageUrl),
            canonicalUrl,
            appUrl
        );
    }

    private boolean hasValidTripToken(UUID tripId, String token) {
        if (token == null || token.isBlank()) return false;
        return tripShareRepository.findByToken(token)
            .filter(TripShare::isValid)
            .map(TripShare::getTripId)
            .filter(tripId::equals)
            .isPresent();
    }

    private String firstStopImage(Trip trip) {
        if (trip.getStops() == null) return null;
        return trip.getStops().stream()
            .map(TripStop::getImageUrl)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    private Optional<String> firstArray(String[] values) {
        if (values == null) return Optional.empty();
        return Arrays.stream(values)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    private String frontendUrl(String path) {
        String base = firstText(properties.getFrontendUrl(), "http://localhost:8081");
        return trimTrailingSlash(base) + (path.startsWith("/") ? path : "/" + path);
    }

    private String absoluteUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return isPublicHttpsUrl(url) ? url : null;
        }
        if (url.startsWith("data:")) {
            return null;
        }
        String base = trimTrailingSlash(firstText(properties.getPublicUrl(), properties.getFrontendUrl(), "http://localhost:8080"));
        String absolute = base + (url.startsWith("/") ? url : "/" + url);
        return isPublicHttpsUrl(absolute) ? absolute : null;
    }

    private String joinNonBlank(String left, String right, String separator) {
        if (left == null || left.isBlank()) return firstText(right, "");
        if (right == null || right.isBlank()) return left;
        return left + separator + right;
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String excerpt(String value, int maxLength) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private boolean isPublicHttpsUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && !host.equals("localhost")
                && !host.equals("127.0.0.1")
                && !host.equals("0.0.0.0")
                && !host.equals("::1");
        } catch (Exception ignored) {
            return false;
        }
    }

    public record SharePreview(String title, String description, String imageUrl, String canonicalUrl, String appUrl) {}
}
