<#
LOCAL/DEV ONLY.

Runs scripts/dev-clean-test-data.sql against a PostgreSQL database.
The SQL file prints row counts first. This wrapper only enables deletion after
you type CLEAN_LOCAL_DB.

Connection info can come from PG* environment variables or Spring datasource env:
  PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD
  DB_URL, DB_USERNAME, DB_PASSWORD
  SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD

Examples:
  .\scripts\dev-clean-test-data.ps1 -DryRun
  .\scripts\dev-clean-test-data.ps1
  .\scripts\dev-clean-test-data.ps1 -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe" -DryRun
  .\scripts\dev-clean-test-data.ps1 -HostName localhost -Port 5432 -Database gola_dev -Username postgres
#>

[CmdletBinding()]
param(
    [string]$HostName = $env:PGHOST,
    [string]$Port = $env:PGPORT,
    [string]$Database = $env:PGDATABASE,
    [string]$Username = $env:PGUSER,
    [string]$PsqlPath,
    [switch]$DryRun
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

    $searched = @(
        "PATH",
        "C:\Program Files\PostgreSQL\*\bin\psql.exe",
        "C:\Program Files\PostgreSQL*\bin\psql.exe",
        "C:\Program Files (x86)\PostgreSQL\*\bin\psql.exe",
        "C:\Program Files (x86)\PostgreSQL*\bin\psql.exe"
    )

    throw @"
psql.exe was not found.

Next steps:
1. Install PostgreSQL client tools, or
2. Add your PostgreSQL bin folder to PATH, or
3. Run this script with an explicit psql path, for example:
   .\scripts\dev-clean-test-data.ps1 -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe" -DryRun

Searched:
- $($searched -join "`n- ")
"@
}

$scriptPath = Join-Path $PSScriptRoot "dev-clean-test-data.sql"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Cleanup SQL not found: $scriptPath"
}

$resolvedPsqlPath = Resolve-PsqlExecutable -ExplicitPath $PsqlPath
Write-Host "Using psql: $resolvedPsqlPath" -ForegroundColor Cyan

$jdbcUrl = if (-not [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_URL)) {
    $env:SPRING_DATASOURCE_URL
} else {
    $env:DB_URL
}

if (-not [string]::IsNullOrWhiteSpace($jdbcUrl) -and $jdbcUrl -match '^jdbc:postgresql://([^/:?]+)(?::([0-9]+))?/([^?]+)') {
    if ([string]::IsNullOrWhiteSpace($HostName)) {
        $HostName = $Matches[1]
    }
    if ([string]::IsNullOrWhiteSpace($Port) -and -not [string]::IsNullOrWhiteSpace($Matches[2])) {
        $Port = $Matches[2]
    }
    if ([string]::IsNullOrWhiteSpace($Database)) {
        $Database = [System.Uri]::UnescapeDataString($Matches[3])
    }
}

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

Write-Host ""
Write-Host "GOLA local/dev cleanup target:" -ForegroundColor Yellow
Write-Host "  Host:     $HostName"
Write-Host "  Port:     $Port"
Write-Host "  Database: $Database"
Write-Host "  Username: $Username"
Write-Host "  psql:     $resolvedPsqlPath"
Write-Host ""

if ($DryRun) {
    Write-Host "Dry run mode: counts will be printed and no rows will be deleted." -ForegroundColor Cyan
} else {
    Write-Warning "This will delete local/dev users, trips, payments, webhooks, notifications, media, AI jobs, and related test data."
    Write-Warning "It will not drop tables or run Flyway."

    $localHostNames = @("localhost", "127.0.0.1", "::1", "host.docker.internal", "postgres", "db")
    if ($localHostNames -notcontains $HostName.ToLowerInvariant()) {
        Write-Warning "Host '$HostName' does not look like a common local hostname."
        $remoteConfirm = Read-Host "Type LOCAL_DEV_DB to confirm this is still a local/dev database"
        if ($remoteConfirm -ne "LOCAL_DEV_DB") {
            Write-Host "Aborted. No cleanup was run." -ForegroundColor Yellow
            exit 1
        }
    }

    $confirmation = Read-Host "Type CLEAN_LOCAL_DB to delete the data listed by the SQL script"
    if ($confirmation -ne "CLEAN_LOCAL_DB") {
        Write-Host "Aborted. No cleanup was run." -ForegroundColor Yellow
        exit 1
    }
}

$argsList = @(
    "-v", "ON_ERROR_STOP=1",
    "-h", $HostName,
    "-p", $Port,
    "-U", $Username,
    "-d", $Database
)

if (-not $DryRun) {
    $argsList += @("-v", "do_delete=1")
}

$argsList += @("-f", $scriptPath)

Write-Host ""
Write-Host "Running psql..." -ForegroundColor Cyan
& $resolvedPsqlPath @argsList

if ($LASTEXITCODE -ne 0) {
    throw "psql exited with code $LASTEXITCODE"
}

Write-Host ""
if ($DryRun) {
    Write-Host "Dry run finished. No data was deleted." -ForegroundColor Green
} else {
    Write-Host "Cleanup finished." -ForegroundColor Green
}
