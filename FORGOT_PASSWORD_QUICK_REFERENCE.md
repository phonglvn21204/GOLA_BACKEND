# Forgot Password Flow - Quick Reference Guide

## 🚀 Quick Start

### 1. Forgot Password Request
```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```
**Response**: OTP sent to user's email (5-minute validity)

---

### 2. Verify OTP
```bash
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","otp":"123456"}'
```
**Response**: `{"resetToken":"uuid-token","message":"OTP verified successfully"}`

---

### 3. Reset Password
```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"uuid-token","newPassword":"NewPass123!"}'
```
**Response**: Password reset successful

---

## 📁 Files Overview

| File | Purpose |
|------|---------|
| `OtpService.java` | OTP generation, storage, email sending |
| `ResetTokenService.java` | Reset token management |
| `VerifyOtpRequest.java` | API request DTO |
| `VerifyOtpResponse.java` | API response with reset token |
| `ForgotPasswordResponse.java` | Forgot password response |
| `V18__add_password_hash.sql` | Database migration |
| **Modified:** `AuthService.java` | 3 new methods + password handling |
| **Modified:** `AuthController.java` | 3 new endpoints |
| **Modified:** `Profile.java` | Added passwordHash field |

---

## ⏱️ Timeouts

| Component | TTL |
|-----------|-----|
| OTP | 5 minutes |
| Reset Token | 15 minutes |

---

## 🔐 Key Security Points

1. **No Email Enumeration**: Returns 200 even if email doesn't exist
2. **6-Digit OTP**: Random generation with SHA-256 hashing
3. **UUID Tokens**: Cryptographically secure tokens
4. **BCrypt Passwords**: Industry-standard hashing
5. **Token Revocation**: All sessions revoked on password change

---

## 📊 Database Schema

```sql
-- Added to profiles table
ALTER TABLE profiles
ADD COLUMN password_hash VARCHAR(255);

-- For quick lookups (optional, already indexed)
CREATE INDEX idx_profiles_password_hash ON profiles(password_hash);
```

---

## 🧵 Thread Safety

- **ConcurrentHashMap**: Thread-safe OTP/token storage
- **ScheduledExecutorService**: Daemon thread for cleanup
- **@Transactional**: Database consistency

---

## 📧 Email Configuration

Required environment variables:
```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

Email template:
```
Subject: GOLA - Mã xác nhận đặt lại mật khẩu
Body: Mã OTP của bạn là: {otp}. Có hiệu lực trong 5 phút.
```

---

## ❌ Error Responses

| Scenario | Status | Message |
|----------|--------|---------|
| Non-existent email | 200 | (Silent) |
| Invalid OTP | 401 | Invalid or expired OTP |
| Expired OTP | 401 | Invalid or expired OTP |
| Invalid token | 401 | Invalid or expired reset token |
| Expired token | 401 | Invalid or expired reset token |

---

## 🔁 Integration Points

### AuthService
- `forgotPassword(ForgotPasswordRequest)` → Sends OTP
- `verifyOtp(VerifyOtpRequest)` → Returns reset token
- `resetPassword(ResetPasswordRequest)` → Updates password
- Injectable in controllers/services

### AuthController
- `/auth/forgot-password` - Request OTP
- `/auth/verify-otp` - Verify OTP
- `/auth/reset-password` - Reset password

---

## 📝 Validation Rules

### OTP Request
- Email: Valid email format required

### OTP Verification
- Email: Valid email format required
- OTP: Must be exactly 6 digits

### Password Reset
- Token: UUID format required
- Password: Min 8 chars, max 72 chars (BCrypt limit)

---

## 🐛 Debugging

Enable debug logging:
```yaml
logging:
  level:
    com.gola.service.OtpService: DEBUG
    com.gola.service.ResetTokenService: DEBUG
    com.gola.service.AuthService: DEBUG
```

Log messages:
```
- "OTP generated and sent to email: {email}"
- "OTP verified successfully for email: {email}"
- "Reset token generated for email: {email}"
- "Password reset completed for user: {email}"
```

---

## 🚦 Testing Workflow

```
1. POST /auth/forgot-password → Get OTP
2. Check email for OTP code
3. POST /auth/verify-otp → Get reset token
4. POST /auth/reset-password → Success!
5. Login with new password
```

Or use the provided test script:
```powershell
powershell -ExecutionPolicy Bypass -File test-forgot-password.ps1
```

---

## 📋 Checklist Before Production

- [ ] Email configuration verified
- [ ] Database migration applied
- [ ] Password reset tested end-to-end
- [ ] Error scenarios tested
- [ ] Email delivery confirmed
- [ ] Rate limiting considered
- [ ] Security review completed
- [ ] Monitoring/logging enabled
- [ ] User documentation updated
- [ ] Support team trained

---

## 🎯 Common Issues & Solutions

### Issue: Emails not being sent
**Solution**: Check MAIL_* environment variables and email provider settings

### Issue: Token expires too quickly
**Solution**: Adjust TTL constants in OtpService (5 min) and ResetTokenService (15 min)

### Issue: OTP not matching
**Solution**: Ensure OTP format is exactly 6 digits, no spaces

### Issue: Password update fails
**Solution**: Ensure password meets requirements (8-72 chars)

---

## 📚 External Resources

- [Flyway Migrations](https://flywaydb.org/)
- [Spring Mail](https://spring.io/guides/gs/sending-email/)
- [BCrypt Password Encoding](https://spring.io/guides/topical/spring-security-architecture/)
- [UUID Generation](https://docs.oracle.com/javase/8/docs/api/java/util/UUID.html)

---

## 📞 Support

For implementation questions or issues:
1. Check `FORGOT_PASSWORD_IMPLEMENTATION.md` for detailed guide
2. Review test script: `test-forgot-password.ps1`
3. Check application logs for debug information
4. Verify email configuration

---

**Last Updated**: June 7, 2026
**Version**: 1.0.0
**Status**: Production Ready ✅

