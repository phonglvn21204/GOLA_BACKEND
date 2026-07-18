# R2 Debug Script - Validates credentials and tests connectivity
# Does NOT print secrets to screen

# Load .env
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$parentDir = Split-Path -Parent $scriptDir
$envFile = Join-Path $parentDir ".env"

if (-not (Test-Path $envFile)) {
    Write-Host "Cannot find .env file at: $envFile" -ForegroundColor Red
    exit 1
}

$vars = @{}
foreach ($line in (Get-Content $envFile)) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
    if ($line -match '^([^=]+)=(.*)$') {
        $vars[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

Write-Host "`n=== STEP 1: Check R2 env vars presence ===" -ForegroundColor Cyan
$r2Keys = @('R2_ACCESS_KEY_ID','R2_SECRET_ACCESS_KEY','R2_BUCKET_NAME','R2_ENDPOINT','R2_PUBLIC_URL')
foreach ($k in $r2Keys) {
    $v = $vars[$k]
    if (-not $v) {
        Write-Host "  FAIL $k : MISSING or EMPTY" -ForegroundColor Red
    } else {
        $preview = $v.Substring(0, [Math]::Min(4, $v.Length)) + '****'
        Write-Host "  PASS $k : Present (len=$($v.Length), starts=$preview)" -ForegroundColor Green
    }
}

Write-Host "`n=== STEP 2: Validate R2_SECRET_ACCESS_KEY format ===" -ForegroundColor Cyan
$secret = $vars['R2_SECRET_ACCESS_KEY']
if (-not $secret) {
    Write-Host "  FAIL: R2_SECRET_ACCESS_KEY is empty" -ForegroundColor Red
} else {
    $secretLen = $secret.Length
    Write-Host "  Secret Key Length: $secretLen (expected: 64)"
    if ($secretLen -ne 64) {
        Write-Host "  FAIL: Length is NOT 64! Possible trailing whitespace, newline, or extra chars." -ForegroundColor Yellow
        # Show char codes of last 4 chars
        $lastChars = $secret.Substring([Math]::Max(0, $secretLen - 4))
        $codes = ($lastChars.ToCharArray() | ForEach-Object { [int]$_ }) -join ', '
        Write-Host "  Last 4 char codes: $codes" -ForegroundColor Yellow
    } else {
        Write-Host "  PASS: Length is exactly 64." -ForegroundColor Green
    }

    if ($secret -match '^[0-9a-fA-F]+$') {
        Write-Host "  PASS: All characters are valid hex." -ForegroundColor Green
    } else {
        $nonHex = ($secret.ToCharArray() | Where-Object { $_ -notmatch '[0-9a-fA-F]' })
        $codes = ($nonHex | ForEach-Object { "char='$_' code=$([int]$_)" }) -join '; '
        Write-Host "  FAIL: Non-hex characters found: $codes" -ForegroundColor Yellow
    }
}

$accessKey = $vars['R2_ACCESS_KEY_ID']
if (-not $accessKey) {
    Write-Host "  FAIL: R2_ACCESS_KEY_ID is empty" -ForegroundColor Red
} else {
    Write-Host "`n  Access Key Length: $($accessKey.Length) (expected: 32)"
    if ($accessKey.Length -eq 32 -and $accessKey -match '^[0-9a-fA-F]+$') {
        Write-Host "  PASS: Access Key format OK." -ForegroundColor Green
    } else {
        Write-Host "  FAIL: Access Key format issue!" -ForegroundColor Yellow
    }
}

# Check endpoint format
$endpoint = $vars['R2_ENDPOINT']
Write-Host "`n  Endpoint: $endpoint"
if ($endpoint -match '^https://[a-f0-9]+\.r2\.cloudflarestorage\.com$') {
    Write-Host "  PASS: Endpoint format looks correct." -ForegroundColor Green
} else {
    Write-Host "  WARNING: Endpoint format may be unusual." -ForegroundColor Yellow
}

Write-Host "`n=== STEP 3: Test connectivity with AWS CLI ===" -ForegroundColor Cyan

# Check if AWS CLI is installed
$awsCli = Get-Command aws -ErrorAction SilentlyContinue
if (-not $awsCli) {
    Write-Host "  AWS CLI not found. Skipping CLI test." -ForegroundColor Yellow
    Write-Host "  Install with: winget install Amazon.AWSCLI" -ForegroundColor Yellow
} else {
    Write-Host "  AWS CLI found: $($awsCli.Source)"
    
    # Configure AWS CLI profile for R2
    & aws configure set aws_access_key_id $vars['R2_ACCESS_KEY_ID'] --profile r2-test
    & aws configure set aws_secret_access_key $vars['R2_SECRET_ACCESS_KEY'] --profile r2-test
    & aws configure set region auto --profile r2-test
    
    Write-Host "  Running: aws s3 ls s3://$($vars['R2_BUCKET_NAME']) --endpoint-url $($vars['R2_ENDPOINT']) --region auto --profile r2-test"
    try {
        $result = & aws s3 ls "s3://$($vars['R2_BUCKET_NAME'])" --endpoint-url $vars['R2_ENDPOINT'] --region auto --profile r2-test 2>&1
        $exitCode = $LASTEXITCODE
        if ($exitCode -eq 0) {
            $lineCount = ($result | Measure-Object -Line).Lines
            Write-Host "  PASS: Listed bucket successfully. ($lineCount entries)" -ForegroundColor Green
            # Show first 5 entries
            $result | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" }
            if ($lineCount -gt 5) { Write-Host "    ... and $($lineCount - 5) more" }
        } else {
            Write-Host "  FAIL: aws s3 ls failed (exit=$exitCode)" -ForegroundColor Red
            Write-Host "  Error output:" -ForegroundColor Red
            $result | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
        }
    } catch {
        Write-Host "  FAIL: Exception running aws cli: $_" -ForegroundColor Red
    }
}
