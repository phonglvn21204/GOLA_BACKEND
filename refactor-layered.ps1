# refactor-layered.ps1
# Refactor GOLA_BACKEND from Module-based to Layered Architecture
# Run from project root: .\refactor-layered.ps1

$baseDir = "src\main\java\com\gola"

# Modules that have sub-layer folders (excluding "common" and "config" to keep them as-is, and adding missing "admin" and "websocket")
$modules = @("admin", "ai", "auth", "community", "map", "notification", "payment", "quest", "safety", "trip", "user", "websocket")

# NOTE: "security", "common", and "config" are kept as-is (common/config are kept per initial requirements)

Write-Host ">>> [GOLA] Starting refactor to Layered Architecture..." -ForegroundColor Cyan
Write-Host ">>> Working directory: $(Get-Location)" -ForegroundColor Cyan

# ============================================================
# STEP 1: Create target layer directories
# ============================================================
Write-Host "`n[Step 1] Creating target directories..." -ForegroundColor Yellow

$targetLayers = @("controller", "service", "repository", "entity", "entity\enums", "mapper", "exception", "validator", "dto")
foreach ($layer in $targetLayers) {
    $path = "$baseDir\$layer"
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
        Write-Host "  Created: $path"
    }
}

# Create dto sub-folders per module
foreach ($module in $modules) {
    $path = "$baseDir\dto\$module"
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
        Write-Host "  Created: $path"
    }
}

# ============================================================
# STEP 2: Move files
# ============================================================
Write-Host "`n[Step 2] Moving files..." -ForegroundColor Yellow

foreach ($module in $modules) {
    $modulePath = "$baseDir\$module"
    if (-not (Test-Path $modulePath)) { continue }

    # --- controller, service, repository, mapper, validator ---
    foreach ($layer in @("controller", "service", "repository", "mapper", "validator")) {
        $src = "$modulePath\$layer"
        if (Test-Path $src) {
            Get-ChildItem -Path $src -Filter *.java | ForEach-Object {
                $dest = "$baseDir\$layer\$($_.Name)"
                Move-Item -Path $_.FullName -Destination $dest -Force
                Write-Host "  Moved [$module/$layer] $($_.Name) -> $layer\"
            }
        }
    }

    # --- exception ---
    $src = "$modulePath\exception"
    if (Test-Path $src) {
        Get-ChildItem -Path $src -Filter *.java | ForEach-Object {
            $dest = "$baseDir\exception\$($_.Name)"
            Move-Item -Path $_.FullName -Destination $dest -Force
            Write-Host "  Moved [$module/exception] $($_.Name) -> exception\"
        }
    }

    # --- entity (flat files only, not enums subfolder) ---
    $src = "$modulePath\entity"
    if (Test-Path $src) {
        Get-ChildItem -Path $src -Filter *.java | ForEach-Object {
            $dest = "$baseDir\entity\$($_.Name)"
            Move-Item -Path $_.FullName -Destination $dest -Force
            Write-Host "  Moved [$module/entity] $($_.Name) -> entity\"
        }
        # entity/enums subfolder
        $enumSrc = "$src\enums"
        if (Test-Path $enumSrc) {
            Get-ChildItem -Path $enumSrc -Filter *.java | ForEach-Object {
                $dest = "$baseDir\entity\enums\$($_.Name)"
                Move-Item -Path $_.FullName -Destination $dest -Force
                Write-Host "  Moved [$module/entity/enums] $($_.Name) -> entity\enums\"
            }
        }
    }

    # --- dto -> dto/<module>/ ---
    $src = "$modulePath\dto"
    if (Test-Path $src) {
        Get-ChildItem -Path $src -Filter *.java | ForEach-Object {
            $dest = "$baseDir\dto\$module\$($_.Name)"
            Move-Item -Path $_.FullName -Destination $dest -Force
            Write-Host "  Moved [$module/dto] $($_.Name) -> dto\$module\"
        }
    }
}

# ============================================================
# STEP 3: Update package declarations and imports
# ============================================================
Write-Host "`n[Step 3] Updating package and import statements..." -ForegroundColor Yellow

$allJavaFiles = Get-ChildItem -Path "src" -Recurse -Filter *.java
$updatedCount = 0

foreach ($file in $allJavaFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    if ([string]::IsNullOrEmpty($content)) { continue }

    $original = $content

    foreach ($module in $modules) {
        # PACKAGE declarations
        $content = $content -replace "package com\.gola\.$module\.entity\.enums;",    "package com.gola.entity.enums;"
        $content = $content -replace "package com\.gola\.$module\.entity;",           "package com.gola.entity;"
        $content = $content -replace "package com\.gola\.$module\.dto;",              "package com.gola.dto.$module;"
        $content = $content -replace "package com\.gola\.$module\.exception;",        "package com.gola.exception;"
        foreach ($layer in @("controller", "service", "repository", "mapper", "validator")) {
            $content = $content -replace "package com\.gola\.$module\.$layer;",       "package com.gola.$layer;"
        }

        # IMPORT statements (using [A-Za-z0-9_\*] to handle both specific imports and wildcard .* imports)
        $content = $content -replace "import com\.gola\.$module\.entity\.enums\.([A-Za-z0-9_\*]+);", 'import com.gola.entity.enums.$1;'
        $content = $content -replace "import com\.gola\.$module\.entity\.([A-Za-z0-9_\*]+);",        'import com.gola.entity.$1;'
        $content = $content -replace "import com\.gola\.$module\.dto\.([A-Za-z0-9_\*]+);",           "import com.gola.dto.$module.`$1;"
        $content = $content -replace "import com\.gola\.$module\.exception\.([A-Za-z0-9_\*]+);",     'import com.gola.exception.$1;'
        foreach ($layer in @("controller", "service", "repository", "mapper", "validator")) {
            $content = $content -replace "import com\.gola\.$module\.$layer\.([A-Za-z0-9_\*]+);",    "import com.gola.$layer.`$1;"
        }
    }

    if ($content -ne $original) {
        Set-Content -Path $file.FullName -Value $content -NoNewline -Encoding UTF8
        Write-Host "  Updated: $($file.Name)" -ForegroundColor Gray
        $updatedCount++
    }
}

Write-Host "  Total files updated: $updatedCount" -ForegroundColor Green

# ============================================================
# STEP 4: Clean up empty directories
# ============================================================
Write-Host "`n[Step 4] Cleaning up empty module directories..." -ForegroundColor Yellow

foreach ($module in $modules) {
    $modulePath = "$baseDir\$module"
    if (-not (Test-Path $modulePath)) { continue }

    # Remove empty subdirs bottom-up
    Get-ChildItem -Path $modulePath -Directory -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        ForEach-Object {
            if ((Get-ChildItem $_.FullName -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count -eq 0) {
                Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
            }
        }

    # Remove module root if empty
    if ((Get-ChildItem $modulePath -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count -eq 0) {
        Remove-Item $modulePath -Force -Recurse -ErrorAction SilentlyContinue
        Write-Host "  Removed empty folder: $module" -ForegroundColor Green
    } else {
        Write-Host "  Kept (not empty): $module" -ForegroundColor DarkYellow
    }
}

# ============================================================
# DONE
# ============================================================
Write-Host "`n>>> Refactoring complete!" -ForegroundColor Green
Write-Host ">>> Next steps:" -ForegroundColor Cyan
Write-Host "    1. Open IntelliJ -> File -> Invalidate Caches / Restart" -ForegroundColor White
Write-Host "    2. Run: .\gradlew clean build" -ForegroundColor White
Write-Host "    3. Fix any remaining compile errors manually" -ForegroundColor White
