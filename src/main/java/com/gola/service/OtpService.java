package com.gola.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Slf4j
@Service
public class OtpService {
    private final JavaMailSender mailSender;
    private final ConcurrentHashMap<String, String> otpStore;
    private final ScheduledExecutorService scheduler;
    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALIDITY_MINUTES = 5;

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.otpStore = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "OTP-Expiry-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Generate and send OTP to email
     */
    public void generateAndSendOtp(String email) {
        String otp = generateRandomOtp();
        String key = email.toLowerCase();
        
        // Store OTP with expiration
        otpStore.put(key, otp);
        
        // Schedule expiration after OTP_VALIDITY_MINUTES
        scheduler.schedule(() -> {
            otpStore.remove(key);
            log.debug("OTP expired for email: {}", email);
        }, OTP_VALIDITY_MINUTES, TimeUnit.MINUTES);
        
        // Send email
        sendOtpEmail(email, otp);
        log.info("OTP generated and sent to email: {}", email);
    }

    /**
     * Verify OTP for email
     */
    public boolean verifyOtp(String email, String otp) {
        String key = email.toLowerCase();
        String storedOtp = otpStore.get(key);
        
        if (storedOtp == null) {
            log.warn("OTP verification failed: OTP not found or expired for email: {}", email);
            return false;
        }
        
        boolean isValid = storedOtp.equals(otp);
        if (isValid) {
            otpStore.remove(key);
            log.info("OTP verified successfully for email: {}", email);
        } else {
            log.warn("OTP verification failed: Invalid OTP for email: {}", email);
        }
        
        return isValid;
    }

    /**
     * Check if OTP exists for email (without consuming it)
     */
    public boolean otpExists(String email) {
        return otpStore.containsKey(email.toLowerCase());
    }

    /**
     * Generate random 6-digit OTP
     */
    private String generateRandomOtp() {
        int otp = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(otp);
    }

    /**
     * Send OTP email
     */
    private void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GOLA - Mã xác nhận đặt lại mật khẩu");
            message.setText("Mã OTP của bạn là: " + otp + ". Có hiệu lực trong 5 phút.\n\n" +
                    "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.");
            
            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            // Don't throw exception - let the OTP still be valid in case email service is temporarily down
        }
    }
}

