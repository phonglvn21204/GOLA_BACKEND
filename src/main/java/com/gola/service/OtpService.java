package com.gola.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpService {

    private final StringRedisTemplate redisTemplate;

    private static final long OTP_VALIDITY_MINUTES = 5;
    private static final String OTP_PREFIX = "otp:";

    public OtpService(StringRedisTemplate redisTemplate) {
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
        try {
            Email from = new Email("se180340luongvanngocphong@gmail.com", "GOLA");
            Email to = new Email(email);
            String subject = "GOLA - Mã xác nhận đặt lại mật khẩu";

            String html = buildOtpEmailHtml(otp);
            Content content = new Content("text/html", html);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            if (response.getStatusCode() >= 400) {
                throw new RuntimeException("SendGrid error: " + response.getBody());
            }

        } catch (Exception e) {
            throw new RuntimeException("Không thể gửi email OTP", e);
        }
    }

    private String buildOtpEmailHtml(String otp) {
        String spacedOtp = String.join(" ", otp.split(""));

        return "<!DOCTYPE html>"
                + "<html><body style=\"margin:0;padding:0;background-color:#f4f4f7;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f4f7;padding:40px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.08);\">"

                // Header
                + "<tr><td style=\"background:linear-gradient(135deg,#4f46e5,#6366f1);padding:32px 24px;text-align:center;\">"
                + "<span style=\"color:#ffffff;font-size:26px;font-weight:bold;letter-spacing:0.5px;\">GOLA</span>"
                + "</td></tr>"

                // Body
                + "<tr><td style=\"padding:40px 32px;text-align:center;\">"
                + "<h2 style=\"color:#111827;margin:0 0 12px 0;font-size:20px;\">Mã xác nhận đặt lại mật khẩu</h2>"
                + "<p style=\"color:#6b7280;font-size:14px;margin:0 0 28px 0;line-height:1.6;\">"
                + "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. "
                + "Sử dụng mã bên dưới để tiếp tục:</p>"

                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td style=\"background-color:#f3f4f6;border:1px dashed #c7d2fe;border-radius:10px;padding:22px;\">"
                + "<span style=\"font-size:34px;font-weight:bold;color:#4f46e5;letter-spacing:8px;\">"
                + spacedOtp + "</span>"
                + "</td></tr></table>"

                + "<p style=\"color:#9ca3af;font-size:13px;margin:24px 0 0 0;\">"
                + "⏱ Mã có hiệu lực trong <b>5 phút</b>.</p>"
                + "</td></tr>"

                // Footer
                + "<tr><td style=\"background-color:#f9fafb;padding:20px 32px;text-align:center;"
                + "border-top:1px solid #f0f0f0;\">"
                + "<p style=\"color:#9ca3af;font-size:12px;margin:0;line-height:1.6;\">"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.<br>"
                + "© 2026 GOLA. Mọi quyền được bảo lưu.</p>"
                + "</td></tr>"

                + "</table></td></tr></table>"
                + "</body></html>";
    }
}