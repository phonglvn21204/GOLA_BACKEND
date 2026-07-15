package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.config.GolaProperties;
import com.gola.entity.BankWebhookEvent;
import com.gola.entity.Order;
import com.gola.entity.enums.PaymentStatus;
import com.gola.repository.BankWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SePayWebhookService {

    private static final String PROVIDER = "SEPAY";
    private static final String DEFAULT_DESCRIPTION_PREFIX = "GOLA";
    private static final int MIN_SEPAY_SUFFIX_DIGITS = 3;
    private static final int MAX_SEPAY_SUFFIX_DIGITS = 10;
    private static final int MAX_LEGACY_SUFFIX_DIGITS = 20;

    private final GolaProperties properties;
    private final BillingService billingService;
    private final BankWebhookEventRepository eventRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> handle(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            log.warn("SePay webhook ignored: invalid payload");
            return success("invalid_payload");
        }

        ParsedSePayEvent event = parse(payload);
        if (event.externalReference().isBlank()) {
            log.warn("SePay webhook ignored: missing external reference");
            return success("missing_reference");
        }
        if (eventRepo.existsByProviderAndExternalReference(PROVIDER, event.externalReference())) {
            log.info("SePay webhook duplicate ignored: reference={}", event.externalReference());
            return success("duplicate");
        }

        if (!isAllowedAccount(event.accountNo())) {
            log.warn("SePay webhook ignored: account number not allowed");
            saveEvent(event, null, "ACCOUNT_MISMATCH", payload);
            return success("account_mismatch");
        }

        if (!event.incoming()) {
            log.warn("SePay webhook ignored: transfer is not incoming reference={}", event.externalReference());
            saveEvent(event, null, "NOT_INCOMING", payload);
            return success("not_incoming");
        }

        Optional<Order> orderOpt = billingService.findByTransferContentOrOrderCode(event.orderCode());
        if (orderOpt.isEmpty()) {
            log.warn("SePay webhook unmatched: reference={} orderCode={}", event.externalReference(), event.orderCode());
            saveEvent(event, null, "UNMATCHED", payload);
            return success("unmatched");
        }

        Order order = orderOpt.get();
        if (order.getStatus() != PaymentStatus.PENDING) {
            log.info("SePay webhook matched non-pending order ignored: order={} status={}", order.getId(), order.getStatus());
            saveEvent(event, order, "ALREADY_FINAL", payload);
            return success("already_final");
        }

        long expectedAmount = order.getAmount() == null ? 0L : order.getAmount();
        long paidAmount = event.amount() == null ? 0L : event.amount();
        boolean exactCodeFieldMatch = event.rawCode() != null
                && order.getOrderCode() != null
                && event.rawCode().trim().equalsIgnoreCase(order.getOrderCode());

        if (paidAmount == expectedAmount || (paidAmount > expectedAmount && exactCodeFieldMatch)) {
            if (paidAmount > expectedAmount) {
                log.warn("SePay overpayment accepted: order={} paid={} expected={}", order.getId(), paidAmount, expectedAmount);
            }
            Order paid = billingService.completeBankConfirmedOrder(
                    order,
                    paidAmount,
                    event.referenceCode(),
                    event.transactionId(),
                    PROVIDER
            );
            saveEvent(event, paid, "PAID", payload);
            return success("paid");
        }

        log.warn("SePay amount mismatch: order={} paid={} expected={}", order.getId(), paidAmount, expectedAmount);
        saveEvent(event, order, "AMOUNT_MISMATCH", payload);
        return success("amount_mismatch");
    }

    public boolean isValidSecret(String querySecret, String headerSecret) {
        String configured = properties.getSepay() == null ? null : properties.getSepay().getWebhookSecret();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return configured.equals(querySecret) || configured.equals(headerSecret);
    }

    private ParsedSePayEvent parse(JsonNode payload) {
        String accountNo = firstText(payload, "accountNumber", "accountNo");
        String rawCode = firstText(payload, "code");
        String content = firstText(payload, "content", "description", "transactionContent", "addInfo");
        String transferType = firstText(payload, "transferType", "type");
        Long amount = firstLong(payload, "transferAmount", "amount", "creditAmount");
        String referenceCode = firstText(payload, "referenceCode");
        String transactionId = firstText(payload, "transactionId", "id");
        String externalReference = firstNonBlank(referenceCode, transactionId);
        if (externalReference == null || externalReference.isBlank()) {
            externalReference = firstNonBlank(rawCode, content, String.valueOf(System.nanoTime()));
        }

        String orderCode = extractOrderCode(rawCode, content);
        boolean incoming = isIncoming(transferType, amount);
        return new ParsedSePayEvent(
                nullToBlank(accountNo),
                nullToBlank(rawCode),
                nullToBlank(content),
                nullToBlank(transferType),
                amount,
                nullToBlank(referenceCode),
                nullToBlank(transactionId),
                nullToBlank(externalReference),
                nullToBlank(orderCode),
                incoming
        );
    }

    private String extractOrderCode(String rawCode, String content) {
        String rawCodeMatch = extractOrderCodeFromText(rawCode);
        if (!rawCodeMatch.isBlank()) {
            return rawCodeMatch;
        }

        String haystack = firstNonBlank(content, rawCode);
        if (haystack == null || haystack.isBlank()) {
            return "";
        }
        return extractOrderCodeFromText(haystack);
    }

    private String extractOrderCodeFromText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String haystack = text.toUpperCase(Locale.ROOT);
        for (String prefix : acceptedDescriptionPrefixes()) {
            Pattern pattern = Pattern.compile(Pattern.quote(prefix) + suffixPattern(prefix) + "(?![0-9A-Z])");
            Matcher matcher = pattern.matcher(haystack);
            if (matcher.find()) {
                return sanitizeOrderCode(matcher.group());
            }
        }
        return "";
    }

    private String[] acceptedDescriptionPrefixes() {
        String configured = descriptionPrefix();
        if (DEFAULT_DESCRIPTION_PREFIX.equals(configured)) {
            return new String[] {configured};
        }
        return new String[] {configured, DEFAULT_DESCRIPTION_PREFIX};
    }

    private String suffixPattern(String prefix) {
        int maxDigits = DEFAULT_DESCRIPTION_PREFIX.equals(prefix) ? MAX_LEGACY_SUFFIX_DIGITS : MAX_SEPAY_SUFFIX_DIGITS;
        return "\\d{" + MIN_SEPAY_SUFFIX_DIGITS + "," + maxDigits + "}";
    }

    private boolean isIncoming(String transferType, Long amount) {
        if (transferType != null && !transferType.isBlank()) {
            String normalized = transferType.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("in") || normalized.equals("credit") || normalized.equals("deposit")) {
                return true;
            }
            if (normalized.equals("out") || normalized.equals("debit") || normalized.equals("withdraw")) {
                return false;
            }
        }
        return amount != null && amount > 0;
    }

    private boolean isAllowedAccount(String accountNo) {
        String allowed = properties.getSepay() == null ? null : properties.getSepay().getAllowedAccountNo();
        if (allowed == null || allowed.isBlank()) {
            return true;
        }
        return normalizeAccount(allowed).equals(normalizeAccount(accountNo));
    }

    private void saveEvent(ParsedSePayEvent event, Order order, String status, JsonNode payload) {
        try {
            eventRepo.save(BankWebhookEvent.builder()
                    .provider(PROVIDER)
                    .externalReference(event.externalReference())
                    .orderId(order == null ? null : order.getId())
                    .amount(event.amount())
                    .status(status)
                    .payload(toPayloadText(payload))
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("SePay webhook duplicate raced and was ignored: reference={}", event.externalReference());
        }
    }

    private String toPayloadText(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload == null ? null : payload.toString();
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) return text.trim();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.asLong();
            }
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    try {
                        return Long.parseLong(text.replaceAll("[^0-9-]", ""));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private String descriptionPrefix() {
        String prefix = properties.getVietqr() == null ? null : properties.getVietqr().getDescriptionPrefix();
        String sanitized = prefix == null || prefix.isBlank()
                ? DEFAULT_DESCRIPTION_PREFIX
                : prefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return sanitized.isBlank() ? DEFAULT_DESCRIPTION_PREFIX : sanitized;
    }

    private String sanitizeOrderCode(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String normalizeAccount(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> success(String status) {
        return Map.of("success", true, "status", status);
    }

    private record ParsedSePayEvent(
            String accountNo,
            String rawCode,
            String content,
            String transferType,
            Long amount,
            String referenceCode,
            String transactionId,
            String externalReference,
            String orderCode,
            boolean incoming
    ) {}
}
