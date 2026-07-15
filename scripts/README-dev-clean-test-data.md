# Local/Dev Data Cleanup

These scripts are for local/dev PostgreSQL databases only. They are not Flyway migrations and must be run manually.

Dry run first:

```powershell
cd E:\EXE_201\GOLA_BACKEND
.\scripts\dev-clean-test-data.ps1 -DryRun
```

Delete local/dev test data:

```powershell
cd E:\EXE_201\GOLA_BACKEND
.\scripts\dev-clean-test-data.ps1
```

Use a custom PostgreSQL client path when `psql.exe` is not in `PATH`:

```powershell
cd E:\EXE_201\GOLA_BACKEND
.\scripts\dev-clean-test-data.ps1 -DryRun -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe"
```

The PowerShell helper reads connection info from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, from app-style `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, or from Spring datasource environment variables. If needed, it prompts for the missing connection fields. It finds `psql.exe` from `-PsqlPath`, then `PATH`, then common Windows PostgreSQL install folders under `C:\Program Files` and `C:\Program Files (x86)`, choosing the highest detected version. It requires typing `CLEAN_LOCAL_DB` before deletion.

The cleanup preserves schema, Flyway history, enum types, products/prices, places/categories, quests/badges/rewards, emergency hotlines, and AI quota limit configuration.
