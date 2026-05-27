package com.gola.repository;

import com.gola.entity.Redemption;
import com.gola.entity.enums.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionRepository extends JpaRepository<Redemption, UUID> {
    List<Redemption> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Redemption> findByStatus(RedemptionStatus status);
    Optional<Redemption> findByCode(String code);
}
