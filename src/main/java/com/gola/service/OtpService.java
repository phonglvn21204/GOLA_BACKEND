package com.gola.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    private static final long OTP_VALIDITY_MINUTES = 5;
    private static final String OTP_PREFIX = "otp:";

    public OtpService(JavaMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    public void generateAndSendOtp(String email) {
        String otp = generateRandomOtp();
        String key = OTP_PREFIX + email.toLowerCase();

        redisTemplate.opsForValue().set(
                key,
                otp,
                OTP_VALIDITY_MINUTES,
                TimeUnit.MINUTES
        );

        sendOtpEmail(email, otp);
    }

    public boolean verifyOtp(String email, String otp) {
        String key = OTP_PREFIX + email.toLowerCase();
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) return false;

        boolean isValid = storedOtp.equals(otp);
        if (isValid) redisTemplate.delete(key);

        return isValid;
    }

    private String generateRandomOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private void sendOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("GOLA - Mã xác nhận đặt lại mật khẩu");
        message.setText("Mã OTP của bạn là: " + otp + ". Có hiệu lực trong 5 phút.");

        mailSender.send(message);
    }
}