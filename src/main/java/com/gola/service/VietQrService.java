package com.gola.service;

import com.gola.config.GolaProperties;
import com.gola.dto.billing.VietQrPaymentResponse;
import com.gola.entity.Order;
import com.gola.entity.enums.PaymentStatus;
import com.gola.exception.GolaException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class VietQrService {

    private static final String DEFAULT_DESCRIPTION_PREFIX = "GOLA";
    private static final int MAX_ORDER_CODE_GENERATION_ATTEMPTS = 20;
    private static final int MIN_NUMERIC_SUFFIX_DIGITS = 8;
    private static final int MAX_NUMERIC_SUFFIX_DIGITS = 10;

    private final GolaProperties properties;
    private final BillingService billingService;

    public VietQrPaymentResponse createPayment(UUID userId, String priceId) {
        ensureConfigured();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        for (int attempt = 0; attempt < MAX_ORDER_CODE_GENERATION_ATTEMPTS; attempt++) {
            String orderCode = buildOrderCode();
            String transferContent = orderCode;
            try {
                Order order = billingService.createVietQrPendingOrder(userId, priceId, orderCode, transferContent, expiresAt);
                return map(order, userId);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_ORDER_CODE_GENERATION_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
        throw GolaException.badRequest("Could not create unique VietQR payment code");
    }

    public VietQrPaymentResponse getPayment(UUID userId, UUID orderId) {
        ensureConfigured();
        Order order = billingService.getOwnedOrderEntity(userId, orderId);
        return map(order, userId);
    }

    public VietQrPaymentResponse getPaymentAsAdmin(UUID orderId) {
        ensureConfigured();
        Order order = billingService.getOrderEntity(orderId);
        return map(order, order.getUserId());
    }

    public VietQrPaymentResponse markPaid(UUID orderId) {
        ensureConfigured();
        Order order = billingService.completeManualOrder(orderId);
        return map(order, order.getUserId());
    }

    private VietQrPaymentResponse map(Order order, UUID userId) {
        return VietQrPaymentResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .transferContent(order.getTransferContent())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(vietQrStatus(order))
                .qrImageUrl(qrImageUrl(order))
                .bankBin(config().getBankBin())
                .accountNo(config().getAccountNo())
                .maskedAccountNo(maskAccount(config().getAccountNo()))
                .accountName(config().getAccountName())
                .expiresAt(order.getExpiresAt())
                .premiumActivated(billingService.hasActivePremium(userId))
                .build();
    }

    private String qrImageUrl(Order order) {
        String base = "https://img.vietqr.io/image/%s-%s-%s.png"
                .formatted(
                        encodePath(config().getBankBin()),
                        encodePath(config().getAccountNo()),
                        encodePath(template())
                );
        return base
                + "?amount=" + encodeQuery(String.valueOf(order.getAmount()))
                + "&addInfo=" + encodeQuery(order.getTransferContent())
                + "&accountName=" + encodeQuery(config().getAccountName());
    }

    private String vietQrStatus(Order order) {
        if (order.getStatus() == PaymentStatus.SUCCEEDED) {
            return "PAID";
        }
        if (order.getStatus() == PaymentStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (order.getStatus() == PaymentStatus.FAILED) {
            return "CANCELLED";
        }
        if (order.getStatus() == PaymentStatus.PENDING
                && order.getExpiresAt() != null
                && order.getExpiresAt().isBefore(Instant.now())) {
            return "EXPIRED";
        }
        return "PENDING";
    }

    private String buildOrderCode() {
        String prefix = descriptionPrefix();
        for (int attempt = 0; attempt < MAX_ORDER_CODE_GENERATION_ATTEMPTS; attempt++) {
            String orderCode = prefix + randomNumericSuffix();
            if (!billingService.vietQrOrderCodeExists(orderCode)) {
                return orderCode;
            }
        }
        throw GolaException.badRequest("Could not create unique VietQR payment code");
    }

    private String descriptionPrefix() {
        String prefix = hasText(config().getDescriptionPrefix()) ? config().getDescriptionPrefix().trim() : DEFAULT_DESCRIPTION_PREFIX;
        String sanitized = prefix.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return hasText(sanitized) ? sanitized : DEFAULT_DESCRIPTION_PREFIX;
    }

    private String randomNumericSuffix() {
        int digits = ThreadLocalRandom.current().nextInt(MIN_NUMERIC_SUFFIX_DIGITS, MAX_NUMERIC_SUFFIX_DIGITS + 1);
        long min = switch (digits) {
            case 8 -> 10_000_000L;
            case 9 -> 100_000_000L;
            default -> 1_000_000_000L;
        };
        return String.valueOf(ThreadLocalRandom.current().nextLong(min, min * 10));
    }

    private void ensureConfigured() {
        if (!hasText(config().getBankBin()) || !hasText(config().getAccountNo()) || !hasText(config().getAccountName())) {
            throw GolaException.badRequest("VietQR payment is not configured");
        }
    }

    private GolaProperties.VietQr config() {
        return properties.getVietqr();
    }

    private String template() {
        return hasText(config().getTemplate()) ? config().getTemplate().trim() : "compact2";
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskAccount(String accountNo) {
        if (!hasText(accountNo) || accountNo.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.max(0, accountNo.length() - 4)) + accountNo.substring(accountNo.length() - 4);
    }
}
