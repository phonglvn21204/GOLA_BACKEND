package com.gola.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ResetTokenService {
    private static final long TOKEN_VALIDITY_MINUTES = 15;
    private final ConcurrentHashMap<String, TokenData> tokenStore;
    private final ScheduledExecutorService scheduler;

    public ResetTokenService() {
        this.tokenStore = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ResetToken-Expiry-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Generate reset token for email
     */
    public String generateResetToken(String email) {
        String token = UUID.randomUUID().toString();
        TokenData data = TokenData.builder()
            .token(token)
            .email(email.toLowerCase())
            .createdAt(System.currentTimeMillis())
            .build();
        
        tokenStore.put(token, data);
        
        // Schedule expiration
        scheduler.schedule(() -> {
            tokenStore.remove(token);
            log.debug("Reset token expired for email: {}", email);
        }, TOKEN_VALIDITY_MINUTES, TimeUnit.MINUTES);
        
        log.info("Reset token generated for email: {}", email);
        return token;
    }

    /**
     * Verify reset token and return email
     */
    public String verifyResetToken(String token) {
        TokenData data = tokenStore.get(token);
        
        if (data == null) {
            log.warn("Reset token verification failed: Token not found or expired");
            return null;
        }
        
        long ageMs = System.currentTimeMillis() - data.createdAt;
        if (ageMs > TOKEN_VALIDITY_MINUTES * 60 * 1000) {
            tokenStore.remove(token);
            log.warn("Reset token verification failed: Token expired");
            return null;
        }
        
        log.info("Reset token verified for email: {}", data.email);
        return data.email;
    }

    /**
     * Consume reset token (remove it after use)
     */
    public void consumeResetToken(String token) {
        tokenStore.remove(token);
        log.info("Reset token consumed and removed");
    }

    static class TokenData {
        String token;
        String email;
        long createdAt;

        TokenData(String token, String email, long createdAt) {
            this.token = token;
            this.email = email;
            this.createdAt = createdAt;
        }

        static TokenDataBuilder builder() {
            return new TokenDataBuilder();
        }

        static class TokenDataBuilder {
            private String token;
            private String email;
            private long createdAt;

            public TokenDataBuilder token(String token) {
                this.token = token;
                return this;
            }

            public TokenDataBuilder email(String email) {
                this.email = email;
                return this;
            }

            public TokenDataBuilder createdAt(long createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public TokenData build() {
                return new TokenData(token, email, createdAt);
            }
        }
    }
}

