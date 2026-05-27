package com.gola.controller;

import com.gola.dto.billing.PricingPlanResponse;
import com.gola.dto.common.ApiResponse;
import com.gola.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Public pricing plans")
public class PricingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    @Operation(summary = "List active products and prices")
    public ResponseEntity<ApiResponse<List<PricingPlanResponse>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.ok(billingService.listPricingPlans()));
    }
}