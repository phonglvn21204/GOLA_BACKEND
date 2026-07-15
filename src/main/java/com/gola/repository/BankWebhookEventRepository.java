package com.gola.repository;

import com.gola.entity.BankWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BankWebhookEventRepository extends JpaRepository<BankWebhookEvent, UUID> {
    boolean existsByProviderAndExternalReference(String provider, String externalReference);
}
