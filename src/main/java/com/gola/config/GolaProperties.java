package com.gola.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gola")
public class GolaProperties {
    private String frontendUrl = "http://localhost:3000";
    private Jwt jwt = new Jwt();
    private Stripe stripe = new Stripe();
    private Twilio twilio = new Twilio();
    private GooglePlaces googlePlaces = new GooglePlaces();
    private Gemini gemini = new Gemini();
    private RateLimit rateLimit = new RateLimit();

    @Data public static class Jwt {
        private String secret;
        private long accessTokenExpiryMs = 900_000L;
        private long refreshTokenExpiryMs = 604_800_000L;
    }
    @Data public static class Stripe {
        private String secretKey;
        private String webhookSecret;
        private String publishableKey;
    }
    @Data public static class Twilio {
        private String accountSid;
        private String authToken;
        private String fromNumber;
    }
    @Data public static class GooglePlaces {
        private String apiKey;
    }
    @Data public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
    }
    @Data public static class RateLimit {
        private boolean enabled = true;
    }
}