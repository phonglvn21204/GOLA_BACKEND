package com.gola.repository;

import com.gola.entity.Review;
import com.gola.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    Optional<Review> findByUserIdAndTripIdAndStopIdIsNull(UUID userId, UUID tripId);
}
