package com.gola.service;

import com.gola.dto.common.PageResponse;
import com.gola.dto.safety.ReportRequest;
import com.gola.entity.Report;
import com.gola.exception.GolaException;
import com.gola.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepo;

    @Transactional
    public void submitReport(UUID reporterId, ReportRequest req) {
        String targetType = req.getTargetType().trim().toUpperCase();
        if (reportRepo.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, targetType, req.getTargetId(), "OPEN")) {
            return;
        }
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(req.getTargetId())
                .reason(req.getReason().trim())
                .status("OPEN")
                .build();
        reportRepo.save(report);
    }

    public PageResponse<Report> listReports(String status, Pageable pageable) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return new PageResponse<>(reportRepo.findAllByOrderByCreatedAtDesc(pageable));
        }
        return new PageResponse<>(reportRepo.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable));
    }

    @Transactional
    public Report updateStatus(UUID reportId, String status) {
        Report report = reportRepo.findById(reportId)
                .orElseThrow(() -> GolaException.notFound("Report"));
        report.setStatus(status.trim().toUpperCase());
        return reportRepo.save(report);
    }
}
