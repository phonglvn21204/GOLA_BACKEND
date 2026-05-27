package com.gola.repository;

import com.gola.entity.TrafficAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrafficAlertRepository extends JpaRepository<TrafficAlert, UUID> {
    List<TrafficAlert> findBySessionIdOrderByTsDesc(UUID sessionId);
    List<TrafficAlert> findBySessionIdAndSeverityOrderByTsDesc(UUID sessionId, String severity);
}
