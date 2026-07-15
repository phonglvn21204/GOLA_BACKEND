package com.gola.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gola.dto.common.ApiResponse;
import com.gola.service.SePayWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/billing/webhooks")
@RequiredArgsConstructor
@Tag(name = "Bank Webhooks", description = "Bank transfer provider webhooks")
public class BankWebhookController {

    private final SePayWebhookService sePayWebhookService;

    @PostMapping("/sepay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleSePay(
            @RequestParam(required = false) String secret,
            @RequestHeader(name = "X-Webhook-Secret", required = false) String headerSecret,
            @RequestBody(required = false) JsonNode payload
    ) {
        if (!sePayWebhookService.isValidSecret(secret, headerSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid webhook secret"));
        }
        return ResponseEntity.ok(ApiResponse.ok(sePayWebhookService.handle(payload)));
    }
}
