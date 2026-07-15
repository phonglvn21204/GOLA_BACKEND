package com.gola.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VietQrPaymentResponse {
    private UUID orderId;
    private String orderCode;
    private String transferContent;
    private Long amount;
    private String currency;
    private String status;
    private String qrImageUrl;
    private String bankBin;
    private String accountNo;
    private String maskedAccountNo;
    private String accountName;
    private Instant expiresAt;
    private boolean premiumActivated;
}
