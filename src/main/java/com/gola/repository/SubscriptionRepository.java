package com.gola.repository;
import com.gola.entity.Subscription;
import com.gola.entity.enums.SubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Subscription> findFirstByUserIdAndStatusOrderByCurrentPeriodEndDesc(UUID userId, SubStatus status);
}
