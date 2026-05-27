package com.gola.service;

import com.gola.dto.safety.ReportRequest;
import com.gola.entity.Report;
import com.gola.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepo;

    @Transactional
    public void submitReport(UUID reporterId, ReportRequest req) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(req.getTargetType())
                .targetId(req.getTargetId())
                .reason(req.getReason())
                .status("OPEN")
                .build();
        reportRepo.save(report);
    }
}
