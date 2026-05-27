package com.gola.repository;

import com.gola.entity.AiQuotaLimit;
import com.gola.entity.enums.AiQuotaKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiQuotaLimitRepository extends JpaRepository<AiQuotaLimit, UUID> {
    List<AiQuotaLimit> findByRoleId(UUID roleId);
    Optional<AiQuotaLimit> findByRoleIdAndKind(UUID roleId, AiQuotaKind kind);
}
