package com.gola.controller;

import com.gola.dto.billing.VietQrCreatePaymentRequest;
import com.gola.dto.billing.VietQrPaymentResponse;
import com.gola.dto.common.ApiResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.VietQrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/billing/vietqr")
@RequiredArgsConstructor
@Tag(name = "VietQR", description = "Manual VietQR bank transfer payment flow")
public class VietQrController {

    private final VietQrService vietQrService;

    @PostMapping("/create-payment")
    @Operation(summary = "Create pending VietQR manual bank transfer order")
    public ResponseEntity<ApiResponse<VietQrPaymentResponse>> createPayment(
            @Valid @RequestBody VietQrCreatePaymentRequest body
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(vietQrService.createPayment(userId, body.getPriceId())));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get VietQR payment order status")
    public ResponseEntity<ApiResponse<VietQrPaymentResponse>> getOrder(@PathVariable UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.ok(ApiResponse.ok(vietQrService.getPaymentAsAdmin(orderId)));
        }
        return ResponseEntity.ok(ApiResponse.ok(vietQrService.getPayment(userId, orderId)));
    }

    @PostMapping("/orders/{orderId}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin/dev manual confirmation for VietQR transfer")
    public ResponseEntity<ApiResponse<VietQrPaymentResponse>> markPaid(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok(vietQrService.markPaid(orderId)));
    }
}
