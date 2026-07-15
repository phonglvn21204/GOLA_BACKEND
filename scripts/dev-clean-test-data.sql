-- LOCAL/DEV ONLY.
-- Manual cleanup for GOLA local PostgreSQL databases after heavy SePay/VietQR and AI-trip testing.
--
-- This is intentionally NOT a Flyway migration. Do not rename it to V*.sql.
-- It does not drop schemas, tables, extensions, enum types, or Flyway history.
-- It does not contain credentials.
--
-- Safe default:
--   Running this file without psql variable do_delete=1 prints counts and exits before deleting.
--
-- Delete mode:
--   Prefer the guarded PowerShell helper:
--     .\scripts\dev-clean-test-data.ps1
--
--   Or run manually with psql after reviewing this file:
--     psql -h localhost -p 5432 -U <user> -d <database> -v do_delete=1 -f scripts/dev-clean-test-data.sql

\set ON_ERROR_STOP on

\echo ''
\echo 'GOLA LOCAL/DEV cleanup - DRY-RUN counts before any delete'
\echo 'Review these counts before choosing delete mode.'
\echo ''

SELECT *
FROM (
    SELECT 'ai_jobs' AS table_name, COUNT(*)::bigint AS row_count FROM ai_jobs
    UNION ALL SELECT 'ai_quotas', COUNT(*)::bigint FROM ai_quotas
    UNION ALL SELECT 'album_media', COUNT(*)::bigint FROM album_media
    UNION ALL SELECT 'albums', COUNT(*)::bigint FROM albums
    UNION ALL SELECT 'audit_logs', COUNT(*)::bigint FROM audit_logs
    UNION ALL SELECT 'bank_webhook_events', COUNT(*)::bigint FROM bank_webhook_events
    UNION ALL SELECT 'comments', COUNT(*)::bigint FROM comments
    UNION ALL SELECT 'device_tokens', COUNT(*)::bigint FROM device_tokens
    UNION ALL SELECT 'emergency_contacts', COUNT(*)::bigint FROM emergency_contacts
    UNION ALL SELECT 'expenses', COUNT(*)::bigint FROM expenses
    UNION ALL SELECT 'follows', COUNT(*)::bigint FROM follows
    UNION ALL SELECT 'hashtags', COUNT(*)::bigint FROM hashtags
    UNION ALL SELECT 'incidents', COUNT(*)::bigint FROM incidents
    UNION ALL SELECT 'live_locations', COUNT(*)::bigint FROM live_locations
    UNION ALL SELECT 'media', COUNT(*)::bigint FROM media
    UNION ALL SELECT 'notification_preferences', COUNT(*)::bigint FROM notification_preferences
    UNION ALL SELECT 'notifications', COUNT(*)::bigint FROM notifications
    UNION ALL SELECT 'orders', COUNT(*)::bigint FROM orders
    UNION ALL SELECT 'password_reset_audit', COUNT(*)::bigint FROM password_reset_audit
    UNION ALL SELECT 'payment_events', COUNT(*)::bigint FROM payment_events
    UNION ALL SELECT 'place_favorites', COUNT(*)::bigint FROM place_favorites
    UNION ALL SELECT 'post_hashtags', COUNT(*)::bigint FROM post_hashtags
    UNION ALL SELECT 'posts', COUNT(*)::bigint FROM posts
    UNION ALL SELECT 'profiles', COUNT(*)::bigint FROM profiles
    UNION ALL SELECT 'quest_progress', COUNT(*)::bigint FROM quest_progress
    UNION ALL SELECT 'reactions', COUNT(*)::bigint FROM reactions
    UNION ALL SELECT 'redemptions', COUNT(*)::bigint FROM redemptions
    UNION ALL SELECT 'refresh_tokens', COUNT(*)::bigint FROM refresh_tokens
    UNION ALL SELECT 'reports', COUNT(*)::bigint FROM reports
    UNION ALL SELECT 'reviews', COUNT(*)::bigint FROM reviews
    UNION ALL SELECT 'routes_cache', COUNT(*)::bigint FROM routes_cache
    UNION ALL SELECT 'safety_reports', COUNT(*)::bigint FROM safety_reports
    UNION ALL SELECT 'sos_dispatch_log', COUNT(*)::bigint FROM sos_dispatch_log
    UNION ALL SELECT 'sos_events', COUNT(*)::bigint FROM sos_events
    UNION ALL SELECT 'subscriptions', COUNT(*)::bigint FROM subscriptions
    UNION ALL SELECT 'traffic_alerts', COUNT(*)::bigint FROM traffic_alerts
    UNION ALL SELECT 'trip_chat', COUNT(*)::bigint FROM trip_chat
    UNION ALL SELECT 'trip_invitations', COUNT(*)::bigint FROM trip_invitations
    UNION ALL SELECT 'trip_members', COUNT(*)::bigint FROM trip_members
    UNION ALL SELECT 'trip_notes', COUNT(*)::bigint FROM trip_notes
    UNION ALL SELECT 'trip_sessions', COUNT(*)::bigint FROM trip_sessions
    UNION ALL SELECT 'trip_shares', COUNT(*)::bigint FROM trip_shares
    UNION ALL SELECT 'trip_stops', COUNT(*)::bigint FROM trip_stops
    UNION ALL SELECT 'trip_stories', COUNT(*)::bigint FROM trip_stories
    UNION ALL SELECT 'trips', COUNT(*)::bigint FROM trips
    UNION ALL SELECT 'user_badges', COUNT(*)::bigint FROM user_badges
    UNION ALL SELECT 'user_blocks', COUNT(*)::bigint FROM user_blocks
    UNION ALL SELECT 'user_pref_interests', COUNT(*)::bigint FROM user_pref_interests
    UNION ALL SELECT 'user_pref_travel_styles', COUNT(*)::bigint FROM user_pref_travel_styles
    UNION ALL SELECT 'user_preferences', COUNT(*)::bigint FROM user_preferences
    UNION ALL SELECT 'user_roles', COUNT(*)::bigint FROM user_roles
    UNION ALL SELECT 'wallets', COUNT(*)::bigint FROM wallets
) AS cleanup_counts
ORDER BY table_name;

\if :{?do_delete}
\else
\echo ''
\echo 'DRY RUN COMPLETE. No rows were deleted.'
\echo 'To delete local/dev data, use scripts/dev-clean-test-data.ps1 or pass -v do_delete=1 to psql.'
\quit 0
\endif

\if :do_delete
\else
\echo ''
\echo 'do_delete was provided but is not truthy. No rows were deleted.'
\quit 1
\endif

\echo ''
\echo 'DELETE MODE CONFIRMED for LOCAL/DEV database.'
\echo 'Starting transaction and truncating test/user/payment/trip data...'
\echo ''

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

TRUNCATE TABLE
    ai_jobs,
    ai_quotas,
    album_media,
    albums,
    audit_logs,
    bank_webhook_events,
    comments,
    device_tokens,
    emergency_contacts,
    expenses,
    follows,
    hashtags,
    incidents,
    live_locations,
    media,
    notification_preferences,
    notifications,
    orders,
    password_reset_audit,
    payment_events,
    place_favorites,
    post_hashtags,
    posts,
    profiles,
    quest_progress,
    reactions,
    redemptions,
    refresh_tokens,
    reports,
    reviews,
    routes_cache,
    safety_reports,
    sos_dispatch_log,
    sos_events,
    subscriptions,
    traffic_alerts,
    trip_chat,
    trip_invitations,
    trip_members,
    trip_notes,
    trip_sessions,
    trip_shares,
    trip_stops,
    trip_stories,
    trips,
    user_badges,
    user_blocks,
    user_pref_interests,
    user_pref_travel_styles,
    user_preferences,
    user_roles,
    wallets
RESTART IDENTITY CASCADE;

\echo ''
\echo 'Post-cleanup counts before COMMIT'
\echo ''

SELECT *
FROM (
    SELECT 'ai_jobs' AS table_name, COUNT(*)::bigint AS row_count FROM ai_jobs
    UNION ALL SELECT 'ai_quotas', COUNT(*)::bigint FROM ai_quotas
    UNION ALL SELECT 'album_media', COUNT(*)::bigint FROM album_media
    UNION ALL SELECT 'albums', COUNT(*)::bigint FROM albums
    UNION ALL SELECT 'audit_logs', COUNT(*)::bigint FROM audit_logs
    UNION ALL SELECT 'bank_webhook_events', COUNT(*)::bigint FROM bank_webhook_events
    UNION ALL SELECT 'comments', COUNT(*)::bigint FROM comments
    UNION ALL SELECT 'device_tokens', COUNT(*)::bigint FROM device_tokens
    UNION ALL SELECT 'emergency_contacts', COUNT(*)::bigint FROM emergency_contacts
    UNION ALL SELECT 'expenses', COUNT(*)::bigint FROM expenses
    UNION ALL SELECT 'follows', COUNT(*)::bigint FROM follows
    UNION ALL SELECT 'hashtags', COUNT(*)::bigint FROM hashtags
    UNION ALL SELECT 'incidents', COUNT(*)::bigint FROM incidents
    UNION ALL SELECT 'live_locations', COUNT(*)::bigint FROM live_locations
    UNION ALL SELECT 'media', COUNT(*)::bigint FROM media
    UNION ALL SELECT 'notification_preferences', COUNT(*)::bigint FROM notification_preferences
    UNION ALL SELECT 'notifications', COUNT(*)::bigint FROM notifications
    UNION ALL SELECT 'orders', COUNT(*)::bigint FROM orders
    UNION ALL SELECT 'password_reset_audit', COUNT(*)::bigint FROM password_reset_audit
    UNION ALL SELECT 'payment_events', COUNT(*)::bigint FROM payment_events
    UNION ALL SELECT 'place_favorites', COUNT(*)::bigint FROM place_favorites
    UNION ALL SELECT 'post_hashtags', COUNT(*)::bigint FROM post_hashtags
    UNION ALL SELECT 'posts', COUNT(*)::bigint FROM posts
    UNION ALL SELECT 'profiles', COUNT(*)::bigint FROM profiles
    UNION ALL SELECT 'quest_progress', COUNT(*)::bigint FROM quest_progress
    UNION ALL SELECT 'reactions', COUNT(*)::bigint FROM reactions
    UNION ALL SELECT 'redemptions', COUNT(*)::bigint FROM redemptions
    UNION ALL SELECT 'refresh_tokens', COUNT(*)::bigint FROM refresh_tokens
    UNION ALL SELECT 'reports', COUNT(*)::bigint FROM reports
    UNION ALL SELECT 'reviews', COUNT(*)::bigint FROM reviews
    UNION ALL SELECT 'routes_cache', COUNT(*)::bigint FROM routes_cache
    UNION ALL SELECT 'safety_reports', COUNT(*)::bigint FROM safety_reports
    UNION ALL SELECT 'sos_dispatch_log', COUNT(*)::bigint FROM sos_dispatch_log
    UNION ALL SELECT 'sos_events', COUNT(*)::bigint FROM sos_events
    UNION ALL SELECT 'subscriptions', COUNT(*)::bigint FROM subscriptions
    UNION ALL SELECT 'traffic_alerts', COUNT(*)::bigint FROM traffic_alerts
    UNION ALL SELECT 'trip_chat', COUNT(*)::bigint FROM trip_chat
    UNION ALL SELECT 'trip_invitations', COUNT(*)::bigint FROM trip_invitations
    UNION ALL SELECT 'trip_members', COUNT(*)::bigint FROM trip_members
    UNION ALL SELECT 'trip_notes', COUNT(*)::bigint FROM trip_notes
    UNION ALL SELECT 'trip_sessions', COUNT(*)::bigint FROM trip_sessions
    UNION ALL SELECT 'trip_shares', COUNT(*)::bigint FROM trip_shares
    UNION ALL SELECT 'trip_stops', COUNT(*)::bigint FROM trip_stops
    UNION ALL SELECT 'trip_stories', COUNT(*)::bigint FROM trip_stories
    UNION ALL SELECT 'trips', COUNT(*)::bigint FROM trips
    UNION ALL SELECT 'user_badges', COUNT(*)::bigint FROM user_badges
    UNION ALL SELECT 'user_blocks', COUNT(*)::bigint FROM user_blocks
    UNION ALL SELECT 'user_pref_interests', COUNT(*)::bigint FROM user_pref_interests
    UNION ALL SELECT 'user_pref_travel_styles', COUNT(*)::bigint FROM user_pref_travel_styles
    UNION ALL SELECT 'user_preferences', COUNT(*)::bigint FROM user_preferences
    UNION ALL SELECT 'user_roles', COUNT(*)::bigint FROM user_roles
    UNION ALL SELECT 'wallets', COUNT(*)::bigint FROM wallets
) AS cleanup_counts
ORDER BY table_name;

COMMIT;

\echo ''
\echo 'GOLA local/dev cleanup committed.'
