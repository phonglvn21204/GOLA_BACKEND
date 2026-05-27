package com.gola.service;

import com.gola.config.GolaProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {
    private final GolaProperties props;

    public void handleWebhook(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, props.getStripe().getWebhookSecret());
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

            switch (event.getType()) {
                case "customer.subscription.created":
                case "customer.subscription.updated":
                    log.info("Subscription created/updated");
                    break;
                case "customer.subscription.deleted":
                    log.info("Subscription deleted");
                    break;
                case "checkout.session.completed":
                    log.info("Checkout session completed");
                    break;
                default:
                    log.warn("Unhandled event type: {}", event.getType());
            }
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature", e);
            throw new RuntimeException("Invalid Stripe signature");
        }
    }
}