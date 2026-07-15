package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.review.ReviewRequest;
import com.gola.dto.review.ReviewResponse;
import com.gola.entity.Profile;
import com.gola.entity.Review;
import com.gola.entity.enums.ReviewStatus;
import com.gola.exception.GolaException;
import com.gola.repository.ProfileRepository;
import com.gola.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepo;
    private final ProfileRepository profileRepo;

    @Transactional
    public ReviewResponse create(UUID userId, ReviewRequest req) {
        if (req.getTripId() != null && req.getStopId() == null) {
            return reviewRepo.findByUserIdAndTripIdAndStopIdIsNull(userId, req.getTripId())
                .map(existing -> {
                    existing.setPlaceName(req.getPlaceName().trim());
                    existing.setRating(req.getRating());
                    existing.setComment(req.getComment());
                    existing.setStatus(ReviewStatus.VISIBLE);
                    existing.setHidden(false);
                    existing.setHiddenAt(null);
                    existing.setHiddenBy(null);
                    existing.setHideReason(null);
                    existing.setUpdatedAt(Instant.now());
                    return toResponse(reviewRepo.save(existing));
                })
                .orElseGet(() -> createNew(userId, req));
        }

        return createNew(userId, req);
    }

    private ReviewResponse createNew(UUID userId, ReviewRequest req) {
        Review review = Review.builder()
            .userId(userId)
            .tripId(req.getTripId())
            .stopId(req.getStopId())
            .placeName(req.getPlaceName().trim())
            .rating(req.getRating())
            .comment(req.getComment())
            .status(ReviewStatus.VISIBLE)
            .hidden(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        return toResponse(reviewRepo.save(review));
    }

    public PageResponse<ReviewResponse> recent(Pageable pageable) {
        return new PageResponse<>(reviewRepo.findByStatus(ReviewStatus.VISIBLE, pageable).map(this::toResponse));
    }

    public PageResponse<ReviewResponse> listForAdmin(String status, Pageable pageable) {
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            ReviewStatus parsed = ReviewStatus.valueOf(status.trim().toUpperCase());
            return new PageResponse<>(reviewRepo.findByStatus(parsed, pageable).map(this::toResponse));
        }
        return new PageResponse<>(reviewRepo.findAll(pageable).map(this::toResponse));
    }

    @Transactional
    public ReviewResponse hide(UUID reviewId, UUID adminId, String reason) {
        Review review = reviewRepo.findById(reviewId).orElseThrow(() -> GolaException.notFound("Review"));
        review.setStatus(ReviewStatus.HIDDEN);
        review.setHidden(true);
        review.setHiddenAt(Instant.now());
        review.setHiddenBy(adminId);
        review.setHideReason(reason);
        review.setUpdatedAt(Instant.now());
        return toResponse(reviewRepo.save(review));
    }

    @Transactional
    public ReviewResponse restore(UUID reviewId) {
        Review review = reviewRepo.findById(reviewId).orElseThrow(() -> GolaException.notFound("Review"));
        review.setStatus(ReviewStatus.VISIBLE);
        review.setHidden(false);
        review.setHiddenAt(null);
        review.setHiddenBy(null);
        review.setHideReason(null);
        review.setUpdatedAt(Instant.now());
        return toResponse(reviewRepo.save(review));
    }

    private ReviewResponse toResponse(Review review) {
        Map<UUID, Profile> users = profileRepo.findAllById(java.util.List.of(review.getUserId())).stream()
            .collect(Collectors.toMap(Profile::getId, p -> p));
        Profile user = users.get(review.getUserId());
        return ReviewResponse.builder()
            .id(review.getId())
            .userId(review.getUserId())
            .userDisplayName(user != null ? user.getDisplayName() : null)
            .userEmail(user != null ? user.getEmail() : null)
            .tripId(review.getTripId())
            .stopId(review.getStopId())
            .placeName(review.getPlaceName())
            .rating(review.getRating())
            .comment(review.getComment())
            .status(review.getStatus())
            .reportCount(review.getReportCount())
            .createdAt(review.getCreatedAt())
            .updatedAt(review.getUpdatedAt())
            .hiddenAt(review.getHiddenAt())
            .hiddenBy(review.getHiddenBy())
            .hideReason(review.getHideReason())
            .build();
    }
}
