package com.gola.controller;

import com.gola.dto.billing.OrderResponse;
import com.gola.dto.billing.PremiumStatusResponse;
import com.gola.dto.billing.SubscriptionResponse;
import com.gola.dto.common.ApiResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscriptions, orders, and premium status")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/premium")
    @Operation(summary = "Get premium subscription status for current user")
    public ResponseEntity<ApiResponse<PremiumStatusResponse>> getPremiumStatus() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(billingService.getPremiumStatus(userId)));
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "List subscriptions for current user")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> listSubscriptions() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(billingService.listSubscriptions(userId)));
    }

    @GetMapping("/orders")
    @Operation(summary = "List payment orders for current user")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOrders() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(billingService.listOrders(userId)));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get payment order by id")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(billingService.getOrder(userId, id)));
    }
}