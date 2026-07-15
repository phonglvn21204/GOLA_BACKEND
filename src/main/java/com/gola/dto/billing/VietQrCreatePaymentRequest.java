package com.gola.dto.billing;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VietQrCreatePaymentRequest {
    @NotBlank
    private String priceId;
}
