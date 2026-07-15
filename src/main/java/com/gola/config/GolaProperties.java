package com.gola.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "gola")
public class GolaProperties {
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";

    private String frontendUrl = "http://localhost:3000";
    private String publicUrl = "http://localhost:8080";
    private Payment payment = new Payment();
    private Jwt jwt = new Jwt();
    private Stripe stripe = new Stripe();
    private VietQr vietqr = new VietQr();
    private BankWebhook bankWebhook = new BankWebhook();
    private SePay sepay = new SePay();
    private Twilio twilio = new Twilio();
    private GooglePlaces googlePlaces = new GooglePlaces();
    private Goong goong = new Goong();
    private SerpApi serpapi = new SerpApi();
    private Gemini gemini = new Gemini();
    private Ai ai = new Ai();
    private RateLimit rateLimit = new RateLimit();

    @Data public static class Jwt {
        private String secret;
        private long accessTokenExpiryMs = 3_600_000L;
        private long refreshTokenExpiryMs = 2_592_000_000L;
    }
    @Data public static class Payment {
        private String provider = "VIETQR";
    }
    @Data public static class Stripe {
        private String secretKey;
        private String webhookSecret;
        private String publishableKey;
    }
    @Data public static class VietQr {
        private String bankBin;
        private String accountNo;
        private String accountName;
        private String template = "compact2";
        private String descriptionPrefix = "GOLA";
    }
    @Data public static class BankWebhook {
        private String provider;
    }
    @Data public static class SePay {
        private String webhookSecret;
        private String allowedAccountNo;
    }
    @Data public static class Twilio {
        private String accountSid;
        private String authToken;
        private String fromNumber;
    }
    @Data public static class GooglePlaces {
        private String apiKey;
    }
    @Data public static class Goong {
        private String apiKey;
    }
    @Data public static class SerpApi {
        private String apiKey;
    }
    @Data public static class Gemini {
        private String apiKey;
        private String model = DEFAULT_GEMINI_MODEL;
        private boolean analyzePhotos = false;
    }
    @Data public static class Ai {
        private Generate generate = new Generate();
        private Improve improve = new Improve();
    }
    @Data public static class Generate {
        private String mode = "ai_first";
        private boolean useVerifiedCandidatePool = false;
        private boolean useScrapingdogContext = true;
        private boolean enrichAfterGemini = true;
        private boolean criticEnabled = false;
        private boolean deepGapFillerEnabled = false;
        private boolean routeDuringPreview = false;
        private int maxProviderCalls = 10;
        private int maxCandidatesToVerify = 12;
        private int targetVerifiedCandidates = 10;
        private boolean fallbackCategorySearchEnabled = true;
    }
    @Data public static class Improve {
        private boolean criticEnabled = true;
        private int maxProviderCalls = 20;
    }
    @Data public static class RateLimit {
        private boolean enabled = true;
    }

    @PostConstruct
    void validatePaymentProviderConfig() {
        String geminiModel = gemini != null && hasText(gemini.getModel())
                ? gemini.getModel().trim()
                : DEFAULT_GEMINI_MODEL;
        log.info("Gemini model configured: {}", geminiModel);

        boolean vietQrSelected = payment != null
                && payment.getProvider() != null
                && "VIETQR".equalsIgnoreCase(payment.getProvider().trim());
        boolean vietQrConfigured = vietqr != null
                && hasText(vietqr.getBankBin())
                && hasText(vietqr.getAccountNo())
                && hasText(vietqr.getAccountName());
        log.info("VietQR configured: {}", vietQrConfigured);
        if (vietQrSelected && !vietQrConfigured) {
            throw new IllegalStateException("PAYMENT_PROVIDER=VIETQR requires VIETQR_BANK_BIN, VIETQR_ACCOUNT_NO, and VIETQR_ACCOUNT_NAME");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
