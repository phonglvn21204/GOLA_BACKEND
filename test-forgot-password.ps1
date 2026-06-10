# Forgot Password Flow - Test Script
# This script demonstrates the complete forgot password flow

$BASE_URL = "http://localhost:8080/api"
$TEST_EMAIL = "testuser@example.com"

Write-Host "=== GOLA Backend - Forgot Password Flow Test ===" -ForegroundColor Cyan
Write-Host ""

# Test 1: Request OTP
Write-Host "Step 1: Request OTP" -ForegroundColor Green
Write-Host "POST $BASE_URL/auth/forgot-password" -ForegroundColor Gray

$forgotPasswordBody = @{
    email = $TEST_EMAIL
} | ConvertTo-Json

$response1 = Invoke-WebRequest -Uri "$BASE_URL/auth/forgot-password" `
    -Method Post `
    -ContentType "application/json" `
    -Body $forgotPasswordBody `
    -UseBasicParsing

Write-Host "Response Status: $($response1.StatusCode)" -ForegroundColor Yellow
$response1Content = $response1.Content | ConvertFrom-Json
$response1Content | ConvertTo-Json | Write-Host -ForegroundColor Gray
Write-Host ""

# Step 2: Get OTP from user (in real scenario, user checks email)
$OTP = Read-Host "Enter the 6-digit OTP sent to your email"

# Test 2: Verify OTP
Write-Host "Step 2: Verify OTP" -ForegroundColor Green
Write-Host "POST $BASE_URL/auth/verify-otp" -ForegroundColor Gray

$verifyOtpBody = @{
    email = $TEST_EMAIL
    otp = $OTP
} | ConvertTo-Json

try {
    $response2 = Invoke-WebRequest -Uri "$BASE_URL/auth/verify-otp" `
        -Method Post `
        -ContentType "application/json" `
        -Body $verifyOtpBody `
        -UseBasicParsing

    Write-Host "Response Status: $($response2.StatusCode)" -ForegroundColor Yellow
    $response2Content = $response2.Content | ConvertFrom-Json
    $resetToken = $response2Content.data.resetToken
    Write-Host "Reset Token: $resetToken" -ForegroundColor Cyan
    $response2Content | ConvertTo-Json | Write-Host -ForegroundColor Gray
    Write-Host ""

    # Test 3: Reset Password
    Write-Host "Step 3: Reset Password" -ForegroundColor Green
    Write-Host "POST $BASE_URL/auth/reset-password" -ForegroundColor Gray

    $newPassword = Read-Host "Enter new password (min 8 characters)"

    $resetPasswordBody = @{
        token = $resetToken
        newPassword = $newPassword
    } | ConvertTo-Json

    $response3 = Invoke-WebRequest -Uri "$BASE_URL/auth/reset-password" `
        -Method Post `
        -ContentType "application/json" `
        -Body $resetPasswordBody `
        -UseBasicParsing

    Write-Host "Response Status: $($response3.StatusCode)" -ForegroundColor Yellow
    $response3Content = $response3.Content | ConvertFrom-Json
    $response3Content | ConvertTo-Json | Write-Host -ForegroundColor Gray
    Write-Host ""

    Write-Host "✅ Password reset successful!" -ForegroundColor Green
    Write-Host "You can now login with your new password" -ForegroundColor Cyan

} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.Exception.Response.Content -ForegroundColor Red
}

