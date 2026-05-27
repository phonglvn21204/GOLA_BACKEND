package com.gola.repository;
import com.gola.entity.AiQuota;
import com.gola.entity.AiQuotaId;
import com.gola.entity.enums.AiJobKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
@Repository public interface AiQuotaRepository extends JpaRepository<AiQuota, AiQuotaId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM AiQuota q WHERE q.userId=:uid AND q.kind=:kind AND q.periodStart=:period")
    Optional<AiQuota> findForUpdate(UUID uid, AiJobKind kind, LocalDate period);
}