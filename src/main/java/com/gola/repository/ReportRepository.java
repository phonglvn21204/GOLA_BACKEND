package com.gola.repository;

import com.gola.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findByTargetTypeAndTargetId(String targetType, UUID targetId);
    List<Report> findByStatus(String status);
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, String targetType, UUID targetId, String status);
    long countByStatus(String status);
}
