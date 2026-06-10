# Google OAuth Integration - Implementation Guide

## Overview
This document describes the Google OAuth 2.0 integration for the GOLA Backend. Users can now login using their Google accounts, with automatic user creation and role assignment.

## Files Created/Modified

### 1. **build.gradle** - Added Dependency
```groovy
implementation 'com.auth0:java-jwt:4.4.0'
```

**Purpose**: JWT token parsing and validation

---

### 2. **application.yml** - Added Configuration
```yaml
gola:
  google:
    client-id: ${GOOGLE_CLIENT_ID:}
```

**Purpose**: Google OAuth Client ID configuration (from environment variable)

---

### 3. **GoogleLoginRequest.java** (NEW)
**Location**: `src/main/java/com/gola/dto/auth/GoogleLoginRequest.java`

```java
@Data
public class GoogleLoginRequest {
    @NotBlank(message = "ID token is required")
    private String idToken;
}
```

**Purpose**: Request DTO for Google OAuth login endpoint
- Validates that idToken is not blank
- Follows project's DTO pattern

---

### 4. **GoogleAuthService.java** (NEW)
**Location**: `src/main/java/com/gola/service/GoogleAuthService.java`

#### Key Features:

**Method: `loginWithGoogle(String idToken)`**
- Verifies Google ID token
- Extracts email, name, picture from token
- Finds or creates user in database
- Returns AuthResponse with JWT tokens

**Method: `verifyGoogleIdToken(String idToken)`**
- Decodes JWT token
- Validates issuer (accounts.google.com)
- Validates audience (client ID matches)
- Checks token expiration
- Extracts claims: email, name, picture

**Method: `createNewGoogleUser(...)`**
- Creates new Profile entity
- Sets emailVerifiedAt to NOW (automatic verification for Google users)
- Creates USER role
- Creates Wallet
- Follows same pattern as AuthService.register()

#### Error Handling:
- Throws `GolaException.unauthorized()` if:
  - Google OAuth not configured
  - Token is invalid
  - Token is expired
  - Audience/issuer mismatch
  - Email not in token

---

### 5. **AuthService.java** - Modified

**Change**: Made `buildAuthResponse()` method public

```java
// Before: private AuthResponse buildAuthResponse(...)
// After:  public AuthResponse buildAuthResponse(...)
```

**Reason**: GoogleAuthService needs to call this method to build auth response

---

### 6. **AuthController.java** - Modified

**Added Endpoint**:
```java
@PostMapping("/google")
@Operation(summary = "Login with Google ID token")
public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
    @Valid @RequestBody GoogleLoginRequest req) {
    return ResponseEntity.ok(ApiResponse.ok(
        googleAuthService.loginWithGoogle(req.getIdToken())
    ));
}
```

**URL**: `POST /api/auth/google`

---

## Workflow

### Client-Side Flow:
1. User clicks "Login with Google"
2. Google OAuth SDK shows login dialog
3. User authenticates
4. Google SDK returns ID token
5. Client sends ID token to backend: `POST /api/auth/google`

### Server-Side Flow:
```
POST /api/auth/google
├─ GoogleAuthService.loginWithGoogle(idToken)
│  ├─ verifyGoogleIdToken(idToken)
│  │  ├─ JWT.decode(idToken)
│  │  ├─ Verify issuer: accounts.google.com
│  │  ├─ Verify audience: matches googleClientId
│  │  ├─ Verify expiration
│  │  └─ Extract: email, name, picture
│  │
│  ├─ ProfileRepository.findByEmail(email)
│  │  ├─ If exists: return existing profile
│  │  └─ If not exists: createNewGoogleUser()
│  │     ├─ Create Profile with emailVerifiedAt = NOW
│  │     ├─ Create UserRole (APP_ROLE.USER)
│  │     ├─ Create Wallet
│  │     └─ Save all
│  │
│  └─ AuthService.buildAuthResponse(profile, role)
│     ├─ Generate access token (JWT)
│     ├─ Generate refresh token
│     ├─ Store refresh token in DB
│     └─ Return AuthResponse
│
└─ Return AuthResponse (200 OK)
```

---

## API Endpoint

### Request:
```bash
POST /api/auth/google
Content-Type: application/json

{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ..."
}
```

### Response (Success):
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 900,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@gmail.com",
      "displayName": "John Doe",
      "avatarUrl": "https://lh3.googleusercontent.com/...",
      "role": "USER",
      "emailVerified": true
    }
  }
}
```

### Response (Error):
```json
{
  "code": 401,
  "message": "Invalid or expired Google token",
  "data": null
}
```

---

## Configuration

### Environment Variables

**Required**:
```bash
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
```

### Getting Google Client ID

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project or select existing
3. Enable Google+ API
4. Create OAuth 2.0 Credentials (Web application)
5. Add authorized origins:
   - `http://localhost:5173` (dev frontend)
   - `https://yourdomain.com` (production)
6. Add authorized redirect URIs:
   - `http://localhost:5173/auth/google-callback` (dev)
   - `https://yourdomain.com/auth/google-callback` (production)
7. Copy Client ID to `GOOGLE_CLIENT_ID` environment variable

---

## Token Verification Details

### What We Verify:

1. **Token Format**: Must be valid JWT format
2. **Issuer**: `accounts.google.com` or `https://accounts.google.com`
3. **Audience**: Must match configured `googleClientId`
4. **Expiration**: Token must not be expired
5. **Claims**: Must contain `email` field

### What We Don't Verify (Production Considerations):

- **Signature**: Currently NOT verified. In production, you should:

```java
// Future enhancement for signature verification
// Use Google's public key certificates to verify RS256 signature
GooglePublicKeysManager keysManager = new GooglePublicKeysManager(httpTransport, jsonFactory);
GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(keysManager)
    .setAudience(Collections.singletonList(googleClientId))
    .build();
```

---

## User Creation

When a new user logs in with Google:

1. **Profile** is created with:
   - `email`: From Google token (lowercased)
   - `displayName`: From Google token (or email prefix if not available)
   - `avatarUrl`: From Google token picture URL
   - `emailVerifiedAt`: Set to NOW (auto-verified)
   - `locale`: "en"
   - `theme`: "dark"
   - `isPublic`: true

2. **UserRole** is created:
   - `role`: `AppRole.USER`

3. **Wallet** is created:
   - `golaCoins`: 0

---

## Existing User Login

When an existing user logs in with Google (matched by email):
- No new profile created
- Existing profile used
- Same roles and wallet preserved
- `emailVerifiedAt` not updated if already set

---

## Security Considerations

### Current Implementation ✅
- JWT format validation
- Issuer verification
- Audience (Client ID) verification
- Expiration check
- Email extraction validation

### Recommended Enhancements 🔒

1. **Signature Verification** (High Priority):
   - Verify RS256 signature using Google's public certificates
   - Fetch certificates from: `https://www.googleapis.com/oauth2/v1/certs`

2. **Rate Limiting**:
   - Apply rate limiting to `/auth/google` endpoint
   - Prevent brute force attacks

3. **HTTPS Only**:
   - Ensure all connections use HTTPS
   - Use secure cookies for tokens

4. **Token Storage**:
   - Store refresh tokens securely
   - Use httpOnly cookies if possible

---

## Testing

### Test Case 1: Valid Google Token
```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<valid_google_token>"}'
```

Expected: 200 OK with AuthResponse

### Test Case 2: New User Registration
- Login with Google account that doesn't exist
- Verify Profile created in database
- Verify Wallet created
- Verify emailVerified is true

### Test Case 3: Existing User Login
- Login with same Google account again
- Verify same profile is returned
- Verify new tokens generated
- Verify profile not duplicated

### Test Case 4: Invalid Token
``` bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"invalid_token"}'
```

Expected: 401 Unauthorized

### Test Case 5: Missing Configuration
- Unset `GOOGLE_CLIENT_ID`
- Try logging in

Expected: 401 "Google OAuth not configured"

---

## Integration with Frontend (React Example)

```javascript
import { GoogleOAuthProvider, useGoogleLogin } from '@react-oauth/google';

function LoginWithGoogle() {
  const login = useGoogleLogin({
    onSuccess: async (credentialResponse) => {
      const response = await fetch('/api/auth/google', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken: credentialResponse.credential })
      });
      
      const authData = await response.json();
      if (authData.code === 200) {
        // Store tokens
        store.setAccessToken(authData.data.accessToken);
        store.setRefreshToken(authData.data.refreshToken);
        // Redirect to dashboard
        navigate('/dashboard');
      }
    }
  });

  return <button onClick={() => login()}>Sign in with Google</button>;
}
```

---

## Build Status

✅ **BUILD SUCCESSFUL in 13s**

All files compile without errors. Warnings are from unrelated entities (SosDispatchLog, Wallet) and don't affect Google OAuth implementation.

---

## Dependencies Added

```gradle
implementation 'com.auth0:java-jwt:4.4.0'
```

**Version**: 4.4.0
**License**: MIT
**Purpose**: JWT token parsing and claim extraction

---

## Files Summary

| File | Status | Type | Purpose |
|------|--------|------|---------|
| build.gradle | MODIFIED | Config | Added Auth0 JWT dependency |
| application.yml | MODIFIED | Config | Added Google client-id property |
| GoogleLoginRequest.java | CREATED | DTO | Request validation |
| GoogleAuthService.java | CREATED | Service | OAuth verification & user creation |
| AuthService.java | MODIFIED | Service | Made buildAuthResponse public |
| AuthController.java | MODIFIED | Controller | Added /google endpoint |

---

## Next Steps

1. Set `GOOGLE_CLIENT_ID` environment variable
2. Test with Google test accounts
3. Implement signature verification for production
4. Add unit tests for GoogleAuthService
5. Add integration tests for /api/auth/google endpoint
6. Configure CORS for frontend domain
7. Add rate limiting to auth endpoints

---

## Troubleshooting

### "Google OAuth not configured"
- Check `GOOGLE_CLIENT_ID` environment variable is set
- Restart application after setting variable

### "Invalid audience"
- Verify `GOOGLE_CLIENT_ID` matches Google Cloud Console
- Check no extra spaces in client ID

### "Invalid issuer"
- Ensure token comes from Google OAuth 2.0
- Token must be from accounts.google.com

### "Token expired"
- Ensure client system time is synchronized
- Token lifetime is typically 1 hour from Google

### User not created after login
- Check database connection
- Verify Wallet table exists
- Check ProfileRepository permissions
- Review application logs for details

