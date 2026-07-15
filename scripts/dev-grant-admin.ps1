<#
LOCAL/DEV ONLY.

Grants the GOLA admin role to an existing user by email.
This wrapper runs scripts/dev-grant-admin.sql manually through psql.

Connection info can come from PG* environment variables or Spring datasource env:
  PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD
  DB_URL, DB_USERNAME, DB_PASSWORD
  SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD

Examples:
  .\scripts\dev-grant-admin.ps1 -Email "user@example.com"
  .\scripts\dev-grant-admin.ps1 -Email "user@example.com" -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe"
  .\scripts\dev-grant-admin.ps1 -Email "user@example.com" -HostName localhost -Port 5432 -Database goladb -Username gola
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Email,
    [string]$HostName = $env:PGHOST,
    [string]$Port = $env:PGPORT,
    [string]$Database = $env:PGDATABASE,
    [string]$Username = $env:PGUSER,
    [string]$PsqlPath
)

$ErrorActionPreference = "Stop"

function Get-PsqlVersionFromPath {
    param([string]$Path)

    $patterns = @(
        'PostgreSQL[\\/](?<version>\d+(?:\.\d+)*)[\\/]bin[\\/]psql\.exe$',
        'PostgreSQL\s*(?<version>\d+(?:\.\d+)*)[\\/]bin[\\/]psql\.exe$'
    )

    foreach ($pattern in $patterns) {
        $match = [regex]::Match($Path, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            try {
                return [version]$match.Groups["version"].Value
            } catch {
                return [version]"0.0"
            }
        }
    }

    return [version]"0.0"
}

function Resolve-PsqlExecutable {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $candidatePath = $ExplicitPath
        if (Test-Path -LiteralPath $candidatePath -PathType Container) {
            $candidatePath = Join-Path $candidatePath "psql.exe"
        }

        $resolved = Resolve-Path -LiteralPath $candidatePath -ErrorAction SilentlyContinue
        if (-not $resolved -or -not (Test-Path -LiteralPath $resolved.Path -PathType Leaf)) {
            throw "The -PsqlPath value does not point to an existing psql.exe: $ExplicitPath"
        }

        $item = Get-Item -LiteralPath $resolved.Path
        if ($item.Name -ne "psql.exe") {
            throw "The -PsqlPath value must point to psql.exe. Received: $($item.FullName)"
        }

        return $item.FullName
    }

    $pathCommand = Get-Command psql.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $pathCommand) {
        $pathCommand = Get-Command psql -CommandType Application -ErrorAction SilentlyContinue
    }
    if ($pathCommand) {
        return $pathCommand.Source
    }

    $programFiles = [Environment]::GetEnvironmentVariable("ProgramFiles")
    $programFilesX86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
    $searchPatterns = @()

    if (-not [string]::IsNullOrWhiteSpace($programFiles)) {
        $searchPatterns += Join-Path $programFiles "PostgreSQL\*\bin\psql.exe"
        $searchPatterns += Join-Path $programFiles "PostgreSQL*\bin\psql.exe"
    }

    if (-not [string]::IsNullOrWhiteSpace($programFilesX86)) {
        $searchPatterns += Join-Path $programFilesX86 "PostgreSQL\*\bin\psql.exe"
        $searchPatterns += Join-Path $programFilesX86 "PostgreSQL*\bin\psql.exe"
    }

    $candidates = @()
    foreach ($pattern in $searchPatterns) {
        $candidates += Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue
    }

    $selected = $candidates |
        Sort-Object -Property `
            @{ Expression = { Get-PsqlVersionFromPath $_.FullName }; Descending = $true }, `
            @{ Expression = { $_.LastWriteTimeUtc }; Descending = $true }, `
            @{ Expression = { $_.FullName }; Descending = $true } |
        Select-Object -First 1

    if ($selected) {
        return $selected.FullName
    }

    throw @"
psql.exe was not found.

Next steps:
1. Install PostgreSQL client tools, or
2. Add your PostgreSQL bin folder to PATH, or
3. Run this script with an explicit psql path, for example:
   .\scripts\dev-grant-admin.ps1 -Email "$Email" -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe"
"@
}

function Read-JdbcConnection {
    $jdbcUrl = if (-not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_URL)) {
        $env:SPRING_DATASOURCE_URL
    } else {
        $env:DB_URL
    }

    if (-not [string]::IsNullOrWhiteSpace($jdbcUrl) -and $jdbcUrl -match '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)') {
        if ([string]::IsNullOrWhiteSpace($script:HostName)) {
            $script:HostName = $Matches[1]
        }
        if ([string]::IsNullOrWhiteSpace($script:Port) -and -not [string]::IsNullOrWhiteSpace($Matches[2])) {
            $script:Port = $Matches[2]
        }
        if ([string]::IsNullOrWhiteSpace($script:Database)) {
            $script:Database = [System.Uri]::UnescapeDataString($Matches[3])
        }
    }
}

if ([string]::IsNullOrWhiteSpace($Email) -or $Email -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    throw "A valid -Email value is required."
}

$scriptPath = Join-Path $PSScriptRoot "dev-grant-admin.sql"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Grant admin SQL not found: $scriptPath"
}

$resolvedPsqlPath = Resolve-PsqlExecutable -ExplicitPath $PsqlPath
Read-JdbcConnection

if ([string]::IsNullOrWhiteSpace($Username) -and -not [string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
    $Username = $env:DB_USERNAME
}

if ([string]::IsNullOrWhiteSpace($Username) -and -not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_USERNAME)) {
    $Username = $env:SPRING_DATASOURCE_USERNAME
}

if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD) -and -not [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    $env:PGPASSWORD = $env:DB_PASSWORD
}

if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD) -and -not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD)) {
    $env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
}

if ([string]::IsNullOrWhiteSpace($HostName)) {
    $HostName = Read-Host "PostgreSQL host [localhost]"
    if ([string]::IsNullOrWhiteSpace($HostName)) {
        $HostName = "localhost"
    }
}

if ([string]::IsNullOrWhiteSpace($Port)) {
    $Port = Read-Host "PostgreSQL port [5432]"
    if ([string]::IsNullOrWhiteSpace($Port)) {
        $Port = "5432"
    }
}

if ([string]::IsNullOrWhiteSpace($Database)) {
    $Database = Read-Host "PostgreSQL database name"
}

if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Read-Host "PostgreSQL username"
}

if ([string]::IsNullOrWhiteSpace($Database) -or [string]::IsNullOrWhiteSpace($Username)) {
    throw "Database and username are required."
}

Write-Host "Using psql: $resolvedPsqlPath" -ForegroundColor Cyan
Write-Host ""
Write-Host "GOLA local/dev admin grant target:" -ForegroundColor Yellow
Write-Host "  Host:     $HostName"
Write-Host "  Port:     $Port"
Write-Host "  Database: $Database"
Write-Host "  Username: $Username"
Write-Host "  Email:    $Email"
Write-Host ""

$localHostNames = @("localhost", "127.0.0.1", "::1", "host.docker.internal", "postgres", "db")
if ($localHostNames -notcontains $HostName.ToLowerInvariant()) {
    Write-Warning "Host '$HostName' does not look like a common local hostname."
    $remoteConfirm = Read-Host "Type LOCAL_DEV_DB to confirm this is still a local/dev database"
    if ($remoteConfirm -ne "LOCAL_DEV_DB") {
        Write-Host "Aborted. No role was granted." -ForegroundColor Yellow
        exit 1
    }
}

$argsList = @(
    "-v", "ON_ERROR_STOP=1",
    "-v", "user_email=$Email",
    "-h", $HostName,
    "-p", $Port,
    "-U", $Username,
    "-d", $Database,
    "-f", $scriptPath
)

Write-Host "Running psql..." -ForegroundColor Cyan
& $resolvedPsqlPath @argsList

if ($LASTEXITCODE -ne 0) {
    throw "psql exited with code $LASTEXITCODE"
}

Write-Host ""
Write-Host "Admin grant finished." -ForegroundColor Green
