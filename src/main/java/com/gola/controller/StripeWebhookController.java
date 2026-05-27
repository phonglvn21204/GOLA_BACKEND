package com.gola.controller;

import com.gola.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {
    private final StripeWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        webhookService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}