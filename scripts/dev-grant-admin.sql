-- LOCAL/DEV ONLY.
--
-- Manually grant the admin role to one existing GOLA user by email.
-- This is not a Flyway migration and must be executed manually.
--
-- Usage with psql:
--   psql -v ON_ERROR_STOP=1 -v user_email='user@example.com' -f scripts/dev-grant-admin.sql

\if :{?user_email}
\else
\echo 'Missing required psql variable: user_email'
\echo 'Example: psql -v ON_ERROR_STOP=1 -v user_email=''user@example.com'' -f scripts/dev-grant-admin.sql'
\quit 1
\endif

\echo ''
\echo 'GOLA local/dev admin grant'
\echo 'Target email: ' :user_email
\echo ''

SELECT
    'schema.user_roles' AS section,
    column_name,
    data_type,
    udt_name,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'user_roles'
ORDER BY ordinal_position;

SELECT
    'schema.app_role' AS section,
    e.enumlabel AS role_name
FROM pg_type t
JOIN pg_enum e ON e.enumtypid = t.oid
WHERE t.typname = 'app_role'
ORDER BY e.enumsortorder;

DO $$
BEGIN
    IF to_regtype('app_role') IS NULL THEN
        RAISE EXCEPTION 'Expected enum type app_role was not found. Refusing to modify roles.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        WHERE t.typname = 'app_role'
          AND e.enumlabel IN ('ADMIN', 'ROLE_ADMIN')
    ) THEN
        ALTER TYPE app_role ADD VALUE 'ADMIN';
        RAISE NOTICE 'Added ADMIN to app_role enum for local/dev admin grant.';
    END IF;
END $$;

DROP TABLE IF EXISTS _gola_admin_role;
CREATE TEMP TABLE _gola_admin_role AS
SELECT e.enumlabel::text AS role_name
FROM pg_type t
JOIN pg_enum e ON e.enumtypid = t.oid
WHERE t.typname = 'app_role'
  AND e.enumlabel IN ('ADMIN', 'ROLE_ADMIN')
ORDER BY CASE e.enumlabel WHEN 'ADMIN' THEN 0 WHEN 'ROLE_ADMIN' THEN 1 ELSE 2 END
LIMIT 1;

SELECT 'selected_admin_role' AS section, role_name FROM _gola_admin_role;

BEGIN;

DROP TABLE IF EXISTS _gola_admin_target_user;
CREATE TEMP TABLE _gola_admin_target_user AS
SELECT id, email, display_name, deleted_at
FROM profiles
WHERE lower(email) = lower(:'user_email');

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM _gola_admin_target_user) = 0 THEN
        RAISE EXCEPTION 'No existing GOLA user found for the requested email.';
    END IF;

    IF (SELECT COUNT(*) FROM _gola_admin_target_user) > 1 THEN
        RAISE EXCEPTION 'More than one user matched the requested email. Refusing to continue.';
    END IF;

    IF EXISTS (SELECT 1 FROM _gola_admin_target_user WHERE deleted_at IS NOT NULL) THEN
        RAISE EXCEPTION 'The requested user is soft-deleted. Refusing to grant admin.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM _gola_admin_role) THEN
        RAISE EXCEPTION 'No ADMIN or ROLE_ADMIN role value is available.';
    END IF;
END $$;

\echo ''
\echo 'Before grant'
SELECT
    'user_before' AS section,
    id,
    email,
    display_name,
    deleted_at
FROM _gola_admin_target_user;

SELECT
    'roles_before' AS section,
    p.email,
    ur.role::text AS role,
    ur.created_at
FROM _gola_admin_target_user p
LEFT JOIN user_roles ur ON ur.user_id = p.id
ORDER BY ur.role::text;

INSERT INTO user_roles (user_id, role)
SELECT u.id, r.role_name::app_role
FROM _gola_admin_target_user u
CROSS JOIN _gola_admin_role r
WHERE NOT EXISTS (
    SELECT 1
    FROM user_roles existing
    WHERE existing.user_id = u.id
      AND existing.role = r.role_name::app_role
);

\echo ''
\echo 'After grant'
SELECT
    'user_after' AS section,
    id,
    email,
    display_name,
    deleted_at
FROM _gola_admin_target_user;

SELECT
    'roles_after' AS section,
    p.email,
    ur.role::text AS role,
    ur.created_at
FROM _gola_admin_target_user p
JOIN user_roles ur ON ur.user_id = p.id
ORDER BY ur.role::text;

SELECT
    'grant_result' AS section,
    p.email,
    r.role_name AS admin_role,
    EXISTS (
        SELECT 1
        FROM user_roles ur
        WHERE ur.user_id = p.id
          AND ur.role = r.role_name::app_role
    ) AS admin_granted
FROM _gola_admin_target_user p
CROSS JOIN _gola_admin_role r;

COMMIT;
