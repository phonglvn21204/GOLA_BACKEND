# Google OAuth API Testing Guide

## Quick Start

### 1. Obtain Google Client ID
- Go to [Google Cloud Console](https://console.cloud.google.com)
- Create OAuth 2.0 credentials for Web Application
- Get your **Client ID** (looks like: `123456789-abc...apps.googleusercontent.com`)

### 2. Get a Test ID Token
You can get a test token using:
- Google Sign-In SDK in your frontend app
- `curl-gso` tool
- Postman with Google OAuth 2.0 flow

### 3. Test the Endpoint

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEiLCJ0eXAiOiJKV1QifQ..."
  }'
```

**PowerShell Example:**
```powershell
$headers = @{
    "Content-Type" = "application/json"
}

$body = @{
    idToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEiLCJ0eXAiOiJKV1QifQ..."
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/google" `
    -Method POST `
    -Headers $headers `
    -Body $body

$response.Content | ConvertFrom-Json | ConvertTo-Json
```

**JavaScript/Fetch:**
```javascript
fetch('http://localhost:8080/api/auth/google', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ idToken: googleToken })
})
.then(res => res.json())
.then(data => console.log(data))
```

---

## Expected Response

### Success (200 OK):
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpYXQiOjE3MDM5MzI4MzUsImV4cCI6MTcwMzk3NjQzNX0.abc...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpYXQiOjE3MDM5MzI4MzUsImV4cCI6MTcwNDUzNzYzNX0.xyz...",
    "expiresIn": 900,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@gmail.com",
      "displayName": "John Doe",
      "avatarUrl": "https://lh3.googleusercontent.com/a-/...",
      "role": "USER",
      "emailVerified": true
    }
  }
}
```

### Error: Invalid Token (401 Unauthorized):
```json
{
  "code": 401,
  "message": "Invalid or expired Google token",
  "data": null
}
```

### Error: Google Not Configured (401 Unauthorized):
```json
{
  "code": 401,
  "message": "Google OAuth not configured",
  "data": null
}
```

### Error: Invalid Request (400 Bad Request):
```json
{
  "code": 400,
  "message": "Validation failed",
  "errors": {
    "idToken": "must not be blank"
  }
}
```

---

## Testing Scenarios

### Scenario 1: New User from Google
1. Get valid Google ID token from new Google account
2. Send to `/api/auth/google`
3. Expected:
   - ✅ New Profile created in database
   - ✅ New Wallet created
   - ✅ New UserRole created with USER role
   - ✅ accessToken returned
   - ✅ emailVerified set to true

**Verify in Database:**
```sql
-- Check profile created
SELECT * FROM profiles WHERE email = 'user@gmail.com';

-- Check wallet created
SELECT * FROM wallets WHERE user_id = '<profile_id>';

-- Check role created
SELECT * FROM user_roles WHERE profile_id = '<profile_id>';
```

### Scenario 2: Existing User Logs In Again
1. Use same Google account as Scenario 1
2. Send to `/api/auth/google`
3. Expected:
   - ✅ Same profile returned (no duplicate)
   - ✅ New tokens generated
   - ✅ Old refresh tokens revoked
   - ✅ 200 OK response

**Verify:**
```sql
SELECT COUNT(*) FROM profiles WHERE email = 'user@gmail.com';
-- Should return 1, not 2
```

### Scenario 3: Invalid Token
1. Create random JWT-like string: `eyJhbGciOiJIUzI1NiJ9...invalid...`
2. Send to `/api/auth/google`
3. Expected:
   - ❌ 401 Unauthorized
   - ❌ Error message: "Invalid or expired Google token"
   - ❌ No profile created

### Scenario 4: Expired Token
1. Get old Google ID token (> 1 hour old)
2. Send to `/api/auth/google`
3. Expected:
   - ❌ 401 Unauthorized
   - ❌ Error message: "Invalid or expired Google token"

### Scenario 5: Wrong Client ID
1. Set `GOOGLE_CLIENT_ID` to different value
2. Get valid Google token for original Client ID
3. Send to `/api/auth/google`
4. Expected:
   - ❌ 401 Unauthorized
   - ❌ Error message: "Invalid or expired Google token"

---

## Advanced Testing

### Using Postman

1. **Create POST request** to `http://localhost:8080/api/auth/google`
2. **Set Body** to:
   ```json
   {
     "idToken": "{{google_id_token}}"
   }
   ```
3. **Get Token from Google:**
   - Use Postman's OAuth 2.0 token endpoint
   - Or manually get from frontend Google SDK

### Using Docker

```bash
# Start GOLA Backend
docker run -e GOOGLE_CLIENT_ID=your-client-id gola-backend

# Test endpoint
docker run --rm curlimages/curl -X POST http://gola-backend:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"..."}'
```

### Manual Database Verification

```bash
# Connect to PostgreSQL
psql -h localhost -U gola -d goladb

# Check new profile
SELECT id, email, display_name, email_verified_at FROM profiles WHERE email LIKE '%@gmail.com';

# Check wallet balance
SELECT wallet.*, profile.email 
FROM wallets wallet
JOIN profiles profile ON wallet.user_id = profile.id
ORDER BY wallet.created_at DESC LIMIT 5;

# Check refresh tokens
SELECT token_hash, expires_at, revoked_at FROM refresh_tokens 
WHERE profile_id = '<uuid>' 
ORDER BY created_at DESC;
```

---

## Debugging Checklist

If `/api/auth/google` is not working:

- [ ] Application is running (`http://localhost:8080/api/health` returns 200)
- [ ] `GOOGLE_CLIENT_ID` environment variable is set
- [ ] Client ID matches Google Cloud Console settings
- [ ] ID token is valid and not expired
- [ ] ID token comes from same Client ID as configuration
- [ ] Request headers include `Content-Type: application/json`
- [ ] Request body has `idToken` field (not `token` or `id_token`)
- [ ] PostgreSQL database is running and accessible
- [ ] Database has `profiles`, `wallets`, `user_roles` tables

### Check Logs:
```bash
# View application logs
docker logs gola-backend

# Look for:
# - "Google login attempt for email: user@gmail.com"
# - "Creating new user from Google OAuth: user@gmail.com"
# - "User logged in with Google: user@gmail.com"

# Or errors:
# - "Failed to verify Google ID token"
# - "Google OAuth not configured"
```

---

## Using the Access Token

After successful login, use the `accessToken` in subsequent requests:

```bash
# Get user profile
curl -X GET http://localhost:8080/api/profile \
  -H "Authorization: Bearer <accessToken>"

# Create a trip
curl -X POST http://localhost:8080/api/trips \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"title":"My Trip", ...}'
```

---

## Common Issues & Solutions

### Issue: "Invalid audience"
**Cause**: Client ID in token doesn't match `GOOGLE_CLIENT_ID`  
**Solution**:
1. Verify Client ID in Google Cloud Console
2. Copy exact value (including .apps.googleusercontent.com)
3. Set as `GOOGLE_CLIENT_ID` environment variable
4. Restart application

### Issue: "Token expired"
**Cause**: Token is > 1 hour old  
**Solution**: Get fresh token from Google OAuth SDK

### Issue: "No method matching /post /google"
**Cause**: Endpoint not registered  
**Solution**: Restart application, rebuild with `gradle build`

### Issue: New user not created
**Cause**: Database or wallet creation failed  
**Solution**:
1. Check PostgreSQL is running
2. Check tables exist
3. Check logs for SQL errors
4. Verify user has DB permissions

### Issue: User created but without avatarUrl
**Cause**: Google token didn't include picture claim  
**Solution**: 
1. Ensure Google Settings include "picture" scope
2. This is normal - user can update avatar later

---

## Example Flow (Complete Testing Sequence)

```bash
# 1. Set environment variable
export GOOGLE_CLIENT_ID=123456789-abc...apps.googleusercontent.com

# 2. Start application
cd gola-backend
./gradlew bootRun

# 3. Wait for startup (logs show "Started GolaApplication")

# 4. Get Google ID token from SDK or Google OAuth endpoint

# 5. Test endpoint
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"eyJhbGciOiJSUzI1NiIsImtpZCI6IjEi..."}'

# 6. Verify response
# Should see user data and tokens

# 7. Use access token
curl -X GET http://localhost:8080/api/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."

# 8. Verify in database
psql -h localhost -U gola -d goladb
> SELECT * FROM profiles WHERE email = 'your-email@gmail.com';
> SELECT * FROM wallets WHERE user_id = '<id>';
```

---

## Performance Notes

- **Token Verification**: ~50-100ms (JWT decoding only)
- **User Creation**: ~200-300ms (DB insert + wallet creation)
- **Existing User Login**: ~100-150ms (DB query + token generation)
- **Total Response Time**: 100-400ms depending on operations

For high-traffic scenarios, consider:
- Email lookup caching
- Batch user operations
- Read replicas for profile queries

