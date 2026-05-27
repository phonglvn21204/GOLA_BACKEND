package com.gola.service;

import com.gola.entity.AiQuota;
import com.gola.entity.enums.AiJobKind;
import com.gola.repository.AiQuotaRepository;
import com.gola.exception.GolaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiQuotaService {
    private final AiQuotaRepository repo;
    private static final int FREE_DAILY_TRIPS  = 3;
    private static final int PREM_DAILY_TRIPS  = 50;

    @Transactional
    public void checkAndIncrement(UUID userId, AiJobKind kind, boolean isPremium) {
        LocalDate today = LocalDate.now();
        int limit = kind == AiJobKind.TRIP_GENERATE
            ? (isPremium ? PREM_DAILY_TRIPS : FREE_DAILY_TRIPS)
            : (isPremium ? 10 : 1);
        var quota = repo.findForUpdate(userId, kind, today).orElseGet(() ->
            AiQuota.builder().userId(userId).kind(kind).periodStart(today).count(0).build());
        if (quota.getCount() >= limit) {
            throw GolaException.tooManyRequests("AI quota exceeded. Upgrade to premium for more.");
        }
        quota.setCount(quota.getCount() + 1);
        repo.save(quota);
    }
}