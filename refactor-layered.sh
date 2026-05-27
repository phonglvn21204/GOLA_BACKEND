#!/bin/bash
# refactor-layered.sh
# Refactor GOLA_BACKEND from Module-based to Layered Architecture
# Run from project root: chmod +x refactor-layered.sh && ./refactor-layered.sh

BASE_DIR="src/main/java/com/gola"

# Modules that have sub-layer folders (excluding "common" and "config" to keep them as-is, and adding missing "admin" and "websocket")
MODULES=("admin" "ai" "auth" "community" "map" "notification" "payment" "quest" "safety" "trip" "user" "websocket")
LAYERS=("controller" "service" "repository" "entity" "mapper" "exception" "validator")

echo "=========================================================="
echo ">>> [GOLA] Starting refactor to Layered Architecture..."
echo "=========================================================="

# ============================================================
# STEP 1: Create target layer directories
# ============================================================
echo "Creating target directories..."
for layer in "${LAYERS[@]}"; do
    mkdir -p "$BASE_DIR/$layer"
done

# Create dto directories for each module (to prevent class name collisions)
mkdir -p "$BASE_DIR/dto"
for module in "${MODULES[@]}"; do
    mkdir -p "$BASE_DIR/dto/$module"
done

# Create entity/enums directory
mkdir -p "$BASE_DIR/entity/enums"

# ============================================================
# STEP 2: Move files
# ============================================================
echo "Moving Java files to their corresponding layers..."
for module in "${MODULES[@]}"; do
    module_path="$BASE_DIR/$module"
    if [ -d "$module_path" ]; then
        # Move regular layers
        for layer in "controller" "service" "repository" "mapper" "validator"; do
            src="$module_path/$layer"
            if [ -d "$src" ]; then
                find "$src" -maxdepth 1 -name "*.java" -exec mv {} "$BASE_DIR/$layer/" \; 2>/dev/null
                echo "  Moved [$module/$layer] -> $layer/"
            fi
        done

        # Move exception
        src="$module_path/exception"
        if [ -d "$src" ]; then
            find "$src" -maxdepth 1 -name "*.java" -exec mv {} "$BASE_DIR/exception/" \; 2>/dev/null
            echo "  Moved [$module/exception] -> exception/"
        fi

        # Move entity & enums
        src="$module_path/entity"
        if [ -d "$src" ]; then
            find "$src" -maxdepth 1 -name "*.java" -exec mv {} "$BASE_DIR/entity/" \; 2>/dev/null
            echo "  Moved [$module/entity] -> entity/"
            if [ -d "$src/enums" ]; then
                find "$src/enums" -maxdepth 1 -name "*.java" -exec mv {} "$BASE_DIR/entity/enums/" \; 2>/dev/null
                echo "  Moved [$module/entity/enums] -> entity/enums/"
            fi
        fi

        # Move dto -> dto/<module>/
        src="$module_path/dto"
        if [ -d "$src" ]; then
            find "$src" -maxdepth 1 -name "*.java" -exec mv {} "$BASE_DIR/dto/$module/" \; 2>/dev/null
            echo "  Moved [$module/dto] -> dto/$module/"
        fi
    fi
done

# ============================================================
# STEP 3: Update package declarations and imports
# ============================================================
echo "Updating package declarations and imports in all .java files..."
updated_count=0

find src/ -name "*.java" -type f | while read -r file; do
    modified=0
    for module in "${MODULES[@]}"; do
        # Detect changes first
        if grep -q -E "package com\.gola\.$module\.(entity|dto|exception|controller|service|repository|mapper|validator)" "$file" || \
           grep -q -E "import com\.gola\.$module\.(entity|dto|exception|controller|service|repository|mapper|validator)" "$file"; then
            
            # PACKAGE declarations
            perl -pi -e "s/package com\.gola\.$module\.entity\.enums;/package com.gola.entity.enums;/g" "$file"
            perl -pi -e "s/package com\.gola\.$module\.entity;/package com.gola.entity;/g" "$file"
            perl -pi -e "s/package com\.gola\.$module\.dto;/package com.gola.dto.$module;/g" "$file"
            perl -pi -e "s/package com\.gola\.$module\.exception;/package com.gola.exception;/g" "$file"
            for layer in "controller" "service" "repository" "mapper" "validator"; do
                perl -pi -e "s/package com\.gola\.$module\.$layer;/package com.gola.$layer;/g" "$file"
            done

            # IMPORT statements (using ([A-Za-z0-9_\*]+) to handle specific and wildcard imports)
            perl -pi -e "s/import com\.gola\.$module\.entity\.enums\.([A-Za-z0-9_\*]+);/import com.gola.entity.enums.\$1;/g" "$file"
            perl -pi -e "s/import com\.gola\.$module\.entity\.(?\!enums\.)([A-Za-z0-9_\*]+);/import com.gola.entity.\$1;/g" "$file"
            perl -pi -e "s/import com\.gola\.$module\.dto\.([A-Za-z0-9_\*]+);/import com.gola.dto.$module.\$1;/g" "$file"
            perl -pi -e "s/import com\.gola\.$module\.exception\.([A-Za-z0-9_\*]+);/import com.gola.exception.\$1;/g" "$file"
            for layer in "controller" "service" "repository" "mapper" "validator"; do
                perl -pi -e "s/import com\.gola\.$module\.$layer\.([A-Za-z0-9_\*]+);/import com.gola.$layer.\$1;/g" "$file"
            done
            modified=1
        fi
    done
    if [ $modified -eq 1 ]; then
        echo "  Updated: $file"
        updated_count=$((updated_count + 1))
    fi
done

echo "  Total files updated: $updated_count"

# ============================================================
# STEP 4: Clean up empty directories
# ============================================================
echo "Cleaning up empty module directories..."
for module in "${MODULES[@]}"; do
    module_path="$BASE_DIR/$module"
    if [ -d "$module_path" ]; then
        # Find and delete empty subdirectories bottom-up
        find "$module_path" -type d -empty -delete 2>/dev/null
        # If directory is completely empty, remove it
        if [ -z "$(ls -A "$module_path" 2>/dev/null)" ]; then
            rm -rf "$module_path"
            echo "  Removed empty folder: $module"
        else
            echo "  Kept (not empty): $module"
        fi
    fi
done

echo "=========================================================="
echo ">>> Refactoring to Layered Architecture completed successfully!"
echo ">>> Next steps:"
echo "    1. Open IntelliJ -> File -> Invalidate Caches / Restart"
echo "    2. Run: ./gradlew clean build"
echo "    3. Fix any remaining compile errors manually"
echo "=========================================================="
