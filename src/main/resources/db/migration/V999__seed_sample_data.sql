-- =============================================================================
-- V999__seed_sample_data.sql
-- Realistic sample data for GOLA backend (PostgreSQL 16).
-- Inserts are ordered by foreign-key dependencies.
-- Re-run safe: only executes once via Flyway.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. PROFILES (15 users) — root entity; id has no DB default
-- -----------------------------------------------------------------------------
INSERT INTO profiles (id, email, display_name, avatar_url, bio, locale, theme, home_city, is_public, onboarded_at, email_verified_at, phone, created_at, updated_at) VALUES
('10000000-0000-4000-8000-000000000001', 'linh.nguyen@example.com',    'Linh Nguyen',    'https://cdn.gola.app/avatars/01.jpg', 'Backpacker exploring Vietnam', 'vi', 'dark',  'Ho Chi Minh City', true,  NOW() - INTERVAL '90 days', NOW() - INTERVAL '89 days', '+84901234001', NOW() - INTERVAL '90 days', NOW()),
('10000000-0000-4000-8000-000000000002', 'minh.tran@example.com',      'Minh Tran',      'https://cdn.gola.app/avatars/02.jpg', 'Food & culture trips',         'vi', 'light', 'Da Nang',          true,  NOW() - INTERVAL '80 days', NOW() - INTERVAL '79 days', '+84901234002', NOW() - INTERVAL '80 days', NOW()),
('10000000-0000-4000-8000-000000000003', 'hana.pham@example.com',      'Hana Pham',      'https://cdn.gola.app/avatars/03.jpg', 'Weekend city breaks',          'en', 'dark',  'Hanoi',            true,  NOW() - INTERVAL '70 days', NOW() - INTERVAL '69 days', '+84901234003', NOW() - INTERVAL '70 days', NOW()),
('10000000-0000-4000-8000-000000000004', 'david.le@example.com',       'David Le',       'https://cdn.gola.app/avatars/04.jpg', 'Coastal road trips',           'en', 'dark',  'Nha Trang',        true,  NOW() - INTERVAL '60 days', NOW() - INTERVAL '59 days', '+84901234004', NOW() - INTERVAL '60 days', NOW()),
('10000000-0000-4000-8000-000000000005', 'mai.vo@example.com',         'Mai Vo',         'https://cdn.gola.app/avatars/05.jpg', 'Family travel planner',        'vi', 'light', 'Can Tho',          true,  NOW() - INTERVAL '55 days', NOW() - INTERVAL '54 days', '+84901234005', NOW() - INTERVAL '55 days', NOW()),
('10000000-0000-4000-8000-000000000006', 'khoa.bui@example.com',       'Khoa Bui',       'https://cdn.gola.app/avatars/06.jpg', 'Mountain trekking',            'vi', 'dark',  'Sapa',             true,  NOW() - INTERVAL '50 days', NOW() - INTERVAL '49 days', '+84901234006', NOW() - INTERVAL '50 days', NOW()),
('10000000-0000-4000-8000-000000000007', 'thu.dang@example.com',       'Thu Dang',       'https://cdn.gola.app/avatars/07.jpg', 'Photography journeys',         'vi', 'dark',  'Hue',              true,  NOW() - INTERVAL '45 days', NOW() - INTERVAL '44 days', '+84901234007', NOW() - INTERVAL '45 days', NOW()),
('10000000-0000-4000-8000-000000000008', 'alex.hoang@example.com',     'Alex Hoang',     'https://cdn.gola.app/avatars/08.jpg', 'Digital nomad',                'en', 'dark',  'Ho Chi Minh City', true,  NOW() - INTERVAL '40 days', NOW() - INTERVAL '39 days', '+84901234008', NOW() - INTERVAL '40 days', NOW()),
('10000000-0000-4000-8000-000000000009', 'yen.nguyen@example.com',     'Yen Nguyen',     'https://cdn.gola.app/avatars/09.jpg', 'Temple & heritage tours',      'vi', 'light', 'Hoi An',           true,  NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days', '+84901234009', NOW() - INTERVAL '35 days', NOW()),
('10000000-0000-4000-8000-000000000010', 'tuan.pham@example.com',      'Tuan Pham',      'https://cdn.gola.app/avatars/10.jpg', 'Motorbike routes',             'vi', 'dark',  'Dalat',            true,  NOW() - INTERVAL '30 days', NOW() - INTERVAL '29 days', '+84901234010', NOW() - INTERVAL '30 days', NOW()),
('10000000-0000-4000-8000-000000000011', 'admin@gola.app',             'Gola Admin',     'https://cdn.gola.app/avatars/admin.jpg', 'Platform administrator',  'en', 'dark',  'Ho Chi Minh City', false, NOW() - INTERVAL '365 days', NOW() - INTERVAL '364 days', NULL, NOW() - INTERVAL '365 days', NOW()),
('10000000-0000-4000-8000-000000000012', 'mod@gola.app',               'Gola Moderator', 'https://cdn.gola.app/avatars/mod.jpg',   'Community moderator',   'en', 'dark',  'Hanoi',            false, NOW() - INTERVAL '200 days', NOW() - INTERVAL '199 days', NULL, NOW() - INTERVAL '200 days', NOW()),
('10000000-0000-4000-8000-000000000013', 'guest1@example.com',         'Guest One',      NULL, 'New explorer',                 'en', 'light', 'Phu Quoc',         true,  NOW() - INTERVAL '10 days', NULL, NULL, NOW() - INTERVAL '10 days', NOW()),
('10000000-0000-4000-8000-000000000014', 'guest2@example.com',         'Guest Two',      NULL, 'Just joined',                  'vi', 'dark',  'Vung Tau',         true,  NOW() - INTERVAL '5 days',  NULL, NULL, NOW() - INTERVAL '5 days',  NOW()),
('10000000-0000-4000-8000-000000000015', 'partner@gola.app',           'Gola Partner',   'https://cdn.gola.app/avatars/partner.jpg', 'Rewards partner', 'en', 'dark', 'Ho Chi Minh City', false, NOW() - INTERVAL '100 days', NOW() - INTERVAL '99 days', NULL, NOW() - INTERVAL '100 days', NOW());

-- -----------------------------------------------------------------------------
-- 2. USER ROLES
-- -----------------------------------------------------------------------------
INSERT INTO user_roles (id, user_id, role, created_at) VALUES
('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'USER',  NOW() - INTERVAL '90 days'),
('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'USER',  NOW() - INTERVAL '80 days'),
('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 'USER',  NOW() - INTERVAL '70 days'),
('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'USER',  NOW() - INTERVAL '60 days'),
('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'USER',  NOW() - INTERVAL '55 days'),
('20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000007', 'USER',  NOW() - INTERVAL '45 days'),
('20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000008', 'USER',  NOW() - INTERVAL '40 days'),
('20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000009', 'USER',  NOW() - INTERVAL '35 days'),
('20000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000010', 'USER',  NOW() - INTERVAL '30 days'),
('20000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000011', 'ADMIN', NOW() - INTERVAL '365 days'),
('20000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000012', 'MODERATOR', NOW() - INTERVAL '200 days'),
('20000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000006', 'USER',  NOW() - INTERVAL '50 days'),
('20000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000013', 'USER',  NOW() - INTERVAL '10 days'),
('20000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000014', 'USER',  NOW() - INTERVAL '5 days'),
('20000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000015', 'USER',  NOW() - INTERVAL '100 days');

-- -----------------------------------------------------------------------------
-- 3. WALLETS
-- -----------------------------------------------------------------------------
INSERT INTO wallets (user_id, gola_coins, updated_at) VALUES
('10000000-0000-4000-8000-000000000001', 1250, NOW()),
('10000000-0000-4000-8000-000000000002',  890, NOW()),
('10000000-0000-4000-8000-000000000003',  540, NOW()),
('10000000-0000-4000-8000-000000000004', 2100, NOW()),
('10000000-0000-4000-8000-000000000005',  320, NOW()),
('10000000-0000-4000-8000-000000000006',  780, NOW()),
('10000000-0000-4000-8000-000000000007',  450, NOW()),
('10000000-0000-4000-8000-000000000008', 3200, NOW()),
('10000000-0000-4000-8000-000000000009',  610, NOW()),
('10000000-0000-4000-8000-000000000010',  920, NOW()),
('10000000-0000-4000-8000-000000000011',    0, NOW()),
('10000000-0000-4000-8000-000000000012',    0, NOW()),
('10000000-0000-4000-8000-000000000013',  100, NOW()),
('10000000-0000-4000-8000-000000000014',   50, NOW()),
('10000000-0000-4000-8000-000000000015', 5000, NOW());

-- -----------------------------------------------------------------------------
-- 4. USER PREFERENCES (V15: id is PK)
-- -----------------------------------------------------------------------------
INSERT INTO user_preferences (id, user_id, travel_style, interests, dietary, budget_band, created_at, updated_at) VALUES
('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '{}', '{}', ARRAY['vegetarian'], 'MID',   NOW() - INTERVAL '89 days', NOW()),
('30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', '{}', '{}', ARRAY['seafood'],     'HIGH',  NOW() - INTERVAL '79 days', NOW()),
('30000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', '{}', '{}', '{}',               'MID',   NOW() - INTERVAL '69 days', NOW()),
('30000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', '{}', '{}', '{}',               'LUXURY',NOW() - INTERVAL '59 days', NOW()),
('30000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', '{}', '{}', ARRAY['halal'],     'MID',   NOW() - INTERVAL '54 days', NOW()),
('30000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', '{}', '{}', '{}',               'LOW',   NOW() - INTERVAL '49 days', NOW()),
('30000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', '{}', '{}', '{}',               'MID',   NOW() - INTERVAL '44 days', NOW()),
('30000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', '{}', '{}', '{}',               'HIGH',  NOW() - INTERVAL '39 days', NOW()),
('30000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', '{}', '{}', ARRAY['vegan'],     'MID',   NOW() - INTERVAL '34 days', NOW()),
('30000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', '{}', '{}', '{}',               'LOW',   NOW() - INTERVAL '29 days', NOW()),
('30000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000011', '{}', '{}', '{}',               'MID',   NOW() - INTERVAL '364 days', NOW()),
('30000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000012', '{}', '{}', '{}',               'MID',   NOW() - INTERVAL '199 days', NOW()),
('30000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000013', '{}', '{}', '{}',               'LOW',   NOW() - INTERVAL '9 days', NOW()),
('30000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000014', '{}', '{}', '{}',               'LOW',   NOW() - INTERVAL '4 days', NOW()),
('30000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000015', '{}', '{}', '{}',               'HIGH',  NOW() - INTERVAL '99 days', NOW());

INSERT INTO user_pref_travel_styles (user_preferences_id, style) VALUES
('30000000-0000-4000-8000-000000000001', 'backpacking'),
('30000000-0000-4000-8000-000000000001', 'adventure'),
('30000000-0000-4000-8000-000000000002', 'foodie'),
('30000000-0000-4000-8000-000000000002', 'culture'),
('30000000-0000-4000-8000-000000000003', 'city_break'),
('30000000-0000-4000-8000-000000000004', 'beach'),
('30000000-0000-4000-8000-000000000005', 'family'),
('30000000-0000-4000-8000-000000000006', 'hiking'),
('30000000-0000-4000-8000-000000000008', 'remote_work'),
('30000000-0000-4000-8000-000000000009', 'heritage');

INSERT INTO user_pref_interests (user_preferences_id, interest) VALUES
('30000000-0000-4000-8000-000000000001', 'hostels'),
('30000000-0000-4000-8000-000000000001', 'street_food'),
('30000000-0000-4000-8000-000000000002', 'local_cuisine'),
('30000000-0000-4000-8000-000000000003', 'museums'),
('30000000-0000-4000-8000-000000000004', 'diving'),
('30000000-0000-4000-8000-000000000006', 'mountains'),
('30000000-0000-4000-8000-000000000007', 'photography'),
('30000000-0000-4000-8000-000000000009', 'temples');

-- -----------------------------------------------------------------------------
-- 5. PLACE CATEGORIES
-- -----------------------------------------------------------------------------
INSERT INTO place_categories (id, name, icon) VALUES
('restaurant', 'Restaurant', 'utensils'),
('cafe',       'Cafe',       'coffee'),
('hotel',      'Hotel',      'bed'),
('museum',     'Museum',     'landmark'),
('park',       'Park',       'tree'),
('beach',      'Beach',      'umbrella-beach'),
('temple',     'Temple',     'place-of-worship'),
('market',     'Market',     'store'),
('viewpoint',  'Viewpoint',  'mountain'),
('airport',    'Airport',    'plane');

-- -----------------------------------------------------------------------------
-- 6. PLACES (20)
-- -----------------------------------------------------------------------------
INSERT INTO places (id, google_place_id, name, category, geom, address, city, country, photos, rating, refreshed_at, created_at) VALUES
('40000000-0000-4000-8000-000000000001', 'gp_ben_thanh',     'Ben Thanh Market',        'market',    ST_SetSRID(ST_MakePoint(106.6981, 10.7720), 4326)::geography, 'Le Loi, District 1',           'Ho Chi Minh City', 'VN', ARRAY['https://cdn.gola.app/places/ben-thanh.jpg'], 4.30, NOW(), NOW() - INTERVAL '60 days'),
('40000000-0000-4000-8000-000000000002', 'gp_notre_dame',    'Notre-Dame Cathedral',    'temple',    ST_SetSRID(ST_MakePoint(106.6990, 10.7798), 4326)::geography, 'Paris Square, District 1',     'Ho Chi Minh City', 'VN', ARRAY['https://cdn.gola.app/places/notre-dame.jpg'], 4.50, NOW(), NOW() - INTERVAL '58 days'),
('40000000-0000-4000-8000-000000000003', 'gp_hoan_kiem',      'Hoan Kiem Lake',          'park',      ST_SetSRID(ST_MakePoint(105.8525, 21.0287), 4326)::geography, 'Hoan Kiem District',           'Hanoi',            'VN', ARRAY['https://cdn.gola.app/places/hoan-kiem.jpg'], 4.70, NOW(), NOW() - INTERVAL '55 days'),
('40000000-0000-4000-8000-000000000004', 'gp_my_khe',         'My Khe Beach',            'beach',     ST_SetSRID(ST_MakePoint(108.2498, 16.0678), 4326)::geography, 'Vo Nguyen Giap',               'Da Nang',          'VN', ARRAY['https://cdn.gola.app/places/my-khe.jpg'], 4.60, NOW(), NOW() - INTERVAL '50 days'),
('40000000-0000-4000-8000-000000000005', 'gp_japanese_bridge','Japanese Covered Bridge', 'temple',    ST_SetSRID(ST_MakePoint(108.3350, 15.8774), 4326)::geography, 'Tran Phu, Hoi An',             'Hoi An',           'VN', ARRAY['https://cdn.gola.app/places/jp-bridge.jpg'], 4.80, NOW(), NOW() - INTERVAL '48 days'),
('40000000-0000-4000-8000-000000000006', 'gp_sapa_fan',       'Fansipan Legend',         'viewpoint', ST_SetSRID(ST_MakePoint(103.8410, 22.3364), 4326)::geography, 'Sun World Fansipan',           'Sapa',             'VN', ARRAY['https://cdn.gola.app/places/fansipan.jpg'], 4.40, NOW(), NOW() - INTERVAL '45 days'),
('40000000-0000-4000-8000-000000000007', 'gp_citadel_hue',    'Hue Imperial Citadel',    'museum',    ST_SetSRID(ST_MakePoint(107.5800, 16.4690), 4326)::geography, '23/8 Street, Hue',             'Hue',              'VN', ARRAY['https://cdn.gola.app/places/citadel.jpg'], 4.50, NOW(), NOW() - INTERVAL '42 days'),
('40000000-0000-4000-8000-000000000008', 'gp_nha_trang_bay',  'Nha Trang Bay',           'beach',     ST_SetSRID(ST_MakePoint(109.1967, 12.2388), 4326)::geography, 'Tran Phu Boulevard',           'Nha Trang',        'VN', ARRAY['https://cdn.gola.app/places/nha-trang.jpg'], 4.20, NOW(), NOW() - INTERVAL '40 days'),
('40000000-0000-4000-8000-000000000009', 'gp_dalat_market',   'Dalat Night Market',      'market',    ST_SetSRID(ST_MakePoint(108.4422, 11.9404), 4326)::geography, 'Nguyen Thi Minh Khai',         'Dalat',            'VN', ARRAY['https://cdn.gola.app/places/dalat-mkt.jpg'], 4.10, NOW(), NOW() - INTERVAL '38 days'),
('40000000-0000-4000-8000-000000000010', 'gp_phu_quoc',       'Sao Beach Phu Quoc',      'beach',     ST_SetSRID(ST_MakePoint(103.9570, 10.2270), 4326)::geography, 'An Thoi, Phu Quoc',            'Phu Quoc',         'VN', ARRAY['https://cdn.gola.app/places/sao-beach.jpg'], 4.90, NOW(), NOW() - INTERVAL '35 days'),
('40000000-0000-4000-8000-000000000011', 'gp_cong_caphe',     'Cong Caphe',              'cafe',      ST_SetSRID(ST_MakePoint(106.7045, 10.7769), 4326)::geography, 'Trieu Viet Vuong',             'Ho Chi Minh City', 'VN', '{}', 4.20, NOW(), NOW() - INTERVAL '30 days'),
('40000000-0000-4000-8000-000000000012', 'gp_pizza_4p',       'Pizza 4P''s',             'restaurant',ST_SetSRID(ST_MakePoint(106.7008, 10.7812), 4326)::geography, '8/15 Le Thanh Ton',            'Ho Chi Minh City', 'VN', '{}', 4.60, NOW(), NOW() - INTERVAL '28 days'),
('40000000-0000-4000-8000-000000000013', 'gp_rex_hotel',      'Rex Hotel Rooftop',       'restaurant',ST_SetSRID(ST_MakePoint(106.7035, 10.7755), 4326)::geography, '141 Nguyen Hue',               'Ho Chi Minh City', 'VN', '{}', 4.30, NOW(), NOW() - INTERVAL '25 days'),
('40000000-0000-4000-8000-000000000014', 'gp_landmark_81',    'Landmark 81 SkyView',     'viewpoint', ST_SetSRID(ST_MakePoint(106.7220, 10.7951), 4326)::geography, 'Vinhomes Central Park',        'Ho Chi Minh City', 'VN', '{}', 4.70, NOW(), NOW() - INTERVAL '22 days'),
('40000000-0000-4000-8000-000000000015', 'gp_tan_son_nhat',   'Tan Son Nhat Airport',    'airport',   ST_SetSRID(ST_MakePoint(106.6520, 10.8188), 4326)::geography, 'Truong Son',                   'Ho Chi Minh City', 'VN', '{}', 3.90, NOW(), NOW() - INTERVAL '20 days'),
('40000000-0000-4000-8000-000000000016', 'gp_cuchi',          'Cu Chi Tunnels',          'museum',    ST_SetSRID(ST_MakePoint(106.4600, 11.1520), 4326)::geography, 'Cu Chi District',              'Ho Chi Minh City', 'VN', '{}', 4.50, NOW(), NOW() - INTERVAL '18 days'),
('40000000-0000-4000-8000-000000000017', 'gp_mekong_delta',   'Mekong Delta Pier',       'viewpoint', ST_SetSRID(ST_MakePoint(105.7850, 10.0450), 4326)::geography, 'Ninh Kieu Wharf',              'Can Tho',          'VN', '{}', 4.00, NOW(), NOW() - INTERVAL '15 days'),
('40000000-0000-4000-8000-000000000018', 'gp_vung_tau',       'Jesus Christ Statue',     'viewpoint', ST_SetSRID(ST_MakePoint(107.0790, 10.3460), 4326)::geography, 'Small Mountain',               'Vung Tau',         'VN', '{}', 4.30, NOW(), NOW() - INTERVAL '12 days'),
('40000000-0000-4000-8000-000000000019', 'gp_water_puppet',   'Thang Long Water Puppet', 'museum',    ST_SetSRID(ST_MakePoint(105.8570, 21.0330), 4326)::geography, '57B Dinh Tien Hoang',          'Hanoi',            'VN', '{}', 4.60, NOW(), NOW() - INTERVAL '10 days'),
('40000000-0000-4000-8000-000000000020', 'gp_egg_coffee',     'Giang Cafe',              'cafe',      ST_SetSRID(ST_MakePoint(105.8485, 21.0295), 4326)::geography, '39 Nguyen Huu Huan',           'Hanoi',            'VN', '{}', 4.50, NOW(), NOW() - INTERVAL '8 days');

-- -----------------------------------------------------------------------------
-- 7. BADGES (10)
-- -----------------------------------------------------------------------------
INSERT INTO badges (id, name, description, icon_url, category, criteria, is_active, created_at, updated_at) VALUES
('50000000-0000-4000-8000-000000000001', 'First Trip',       'Created your first trip',           'https://cdn.gola.app/badges/first-trip.png',   'onboarding', 'Create 1 trip',              true, NOW() - INTERVAL '90 days', NOW()),
('50000000-0000-4000-8000-000000000002', 'Explorer',         'Visited 5 cities',                  'https://cdn.gola.app/badges/explorer.png',     'travel',     'Complete 5 city stops',      true, NOW() - INTERVAL '80 days', NOW()),
('50000000-0000-4000-8000-000000000003', 'Social Butterfly', 'Gained 10 followers',             'https://cdn.gola.app/badges/social.png',       'social',     '10 followers',               true, NOW() - INTERVAL '70 days', NOW()),
('50000000-0000-4000-8000-000000000004', 'Quest Master',     'Completed 3 quests',              'https://cdn.gola.app/badges/quest.png',        'gamification','Complete 3 quests',         true, NOW() - INTERVAL '60 days', NOW()),
('50000000-0000-4000-8000-000000000005', 'Safety Star',      'Resolved an SOS drill',           'https://cdn.gola.app/badges/safety.png',       'safety',     'Resolve 1 SOS event',        true, NOW() - INTERVAL '50 days', NOW()),
('50000000-0000-4000-8000-000000000006', 'Foodie',           'Reviewed 5 restaurants',          'https://cdn.gola.app/badges/foodie.png',       'places',     '5 restaurant reviews',       true, NOW() - INTERVAL '40 days', NOW()),
('50000000-0000-4000-8000-000000000007', 'Photo Pro',        'Uploaded 20 album photos',        'https://cdn.gola.app/badges/photo.png',        'media',      '20 album media items',       true, NOW() - INTERVAL '30 days', NOW()),
('50000000-0000-4000-8000-000000000008', 'Team Player',      'Joined 3 group trips',            'https://cdn.gola.app/badges/team.png',         'travel',     'Member of 3 trips',           true, NOW() - INTERVAL '25 days', NOW()),
('50000000-0000-4000-8000-000000000009', 'AI Pioneer',       'Used AI trip generation',         'https://cdn.gola.app/badges/ai.png',           'ai',         '1 TRIP_GENERATE job',        true, NOW() - INTERVAL '20 days', NOW()),
('50000000-0000-4000-8000-000000000010', 'Premium Member',   'Active subscription',             'https://cdn.gola.app/badges/premium.png',      'payment',    'Active subscription',        true, NOW() - INTERVAL '15 days', NOW());

-- -----------------------------------------------------------------------------
-- 8. HASHTAGS (15)
-- -----------------------------------------------------------------------------
INSERT INTO hashtags (tag, post_count, last_used_at) VALUES
('vietnam',      0, NOW() - INTERVAL '1 day'),
('hochiminh',    0, NOW() - INTERVAL '2 days'),
('hanoi',        0, NOW() - INTERVAL '3 days'),
('danang',       0, NOW() - INTERVAL '4 days'),
('hoian',        0, NOW() - INTERVAL '5 days'),
('sapa',         0, NOW() - INTERVAL '6 days'),
('foodtour',     0, NOW() - INTERVAL '7 days'),
('backpacking',  0, NOW() - INTERVAL '8 days'),
('beachlife',    0, NOW() - INTERVAL '9 days'),
('roadtrip',     0, NOW() - INTERVAL '10 days'),
('solotravel',   0, NOW() - INTERVAL '11 days'),
('golatravel',   0, NOW() - INTERVAL '12 days'),
('weekendtrip',  0, NOW() - INTERVAL '13 days'),
('sunrise',      0, NOW() - INTERVAL '14 days'),
('hiddenGem',    0, NOW() - INTERVAL '15 days');

-- -----------------------------------------------------------------------------
-- 9. PRODUCTS & PRICES
-- -----------------------------------------------------------------------------
INSERT INTO products (id, stripe_product_id, name, description, is_active, created_at) VALUES
('60000000-0000-4000-8000-000000000001', 'prod_gola_free',    'Gola Free',    'Basic travel features',           true, NOW() - INTERVAL '180 days'),
('60000000-0000-4000-8000-000000000002', 'prod_gola_plus',    'Gola Plus',    'AI trips and premium quests',     true, NOW() - INTERVAL '180 days'),
('60000000-0000-4000-8000-000000000003', 'prod_gola_family',  'Gola Family',  'Family sharing up to 5 members',  true, NOW() - INTERVAL '120 days'),
('60000000-0000-4000-8000-000000000004', 'prod_gola_annual',  'Gola Annual',  'Annual Plus subscription',        true, NOW() - INTERVAL '90 days'),
('60000000-0000-4000-8000-000000000005', 'prod_gola_coins',   'Gola Coins',   'In-app currency packs',           true, NOW() - INTERVAL '60 days');

INSERT INTO prices (id, product_id, stripe_price_id, amount, currency, interval_type, interval_count, is_active, created_at) VALUES
('61000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000002', 'price_plus_monthly',  99000,  'vnd', 'month', 1, true, NOW() - INTERVAL '180 days'),
('61000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000002', 'price_plus_yearly',  990000,  'vnd', 'year',  1, true, NOW() - INTERVAL '180 days'),
('61000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000003', 'price_family_monthly',149000, 'vnd', 'month', 1, true, NOW() - INTERVAL '120 days'),
('61000000-0000-4000-8000-000000000004', '60000000-0000-4000-8000-000000000004', 'price_annual',       890000,  'vnd', 'year',  1, true, NOW() - INTERVAL '90 days'),
('61000000-0000-4000-8000-000000000005', '60000000-0000-4000-8000-000000000005', 'price_coins_500',     49000,  'vnd', NULL,    1, true, NOW() - INTERVAL '60 days'),
('61000000-0000-4000-8000-000000000006', '60000000-0000-4000-8000-000000000005', 'price_coins_1000',    89000,  'vnd', NULL,    1, true, NOW() - INTERVAL '60 days');

-- -----------------------------------------------------------------------------
-- 10. REWARDS (10)
-- -----------------------------------------------------------------------------
INSERT INTO rewards (id, name, description, cost_coins, stock, image_url, is_active, created_at, updated_at) VALUES
('62000000-0000-4000-8000-000000000001', 'Coffee Voucher',      'Free cappuccino at Cong Caphe',  200,  100, 'https://cdn.gola.app/rewards/coffee.png',  true, NOW() - INTERVAL '60 days', NOW()),
('62000000-0000-4000-8000-000000000002', 'Grab Discount',       '20% off 3 Grab rides',          350,   50, 'https://cdn.gola.app/rewards/grab.png',    true, NOW() - INTERVAL '55 days', NOW()),
('62000000-0000-4000-8000-000000000003', 'Museum Pass',         'Free entry to 1 museum',        500,   30, 'https://cdn.gola.app/rewards/museum.png',  true, NOW() - INTERVAL '50 days', NOW()),
('62000000-0000-4000-8000-000000000004', 'Hostel Night',        '1 night hostel stay',           800,   20, 'https://cdn.gola.app/rewards/hostel.png',  true, NOW() - INTERVAL '45 days', NOW()),
('62000000-0000-4000-8000-000000000005', 'AI Trip Boost',       'Extra 5 AI generations',        300,  200, 'https://cdn.gola.app/rewards/ai.png',      true, NOW() - INTERVAL '40 days', NOW()),
('62000000-0000-4000-8000-000000000006', 'Pizza 4Ps Meal',      'Margherita pizza combo',        450,   40, 'https://cdn.gola.app/rewards/pizza.png',   true, NOW() - INTERVAL '35 days', NOW()),
('62000000-0000-4000-8000-000000000007', 'Beach Umbrella',      'Half-day umbrella rental',      150,   60, 'https://cdn.gola.app/rewards/beach.png',   true, NOW() - INTERVAL '30 days', NOW()),
('62000000-0000-4000-8000-000000000008', 'Premium Badge Frame', 'Exclusive profile frame',       1000,  10, 'https://cdn.gola.app/rewards/frame.png',   true, NOW() - INTERVAL '25 days', NOW()),
('62000000-0000-4000-8000-000000000009', 'Tour Guide Tip',      'Support local guides fund',     100,  500, 'https://cdn.gola.app/rewards/guide.png',   true, NOW() - INTERVAL '20 days', NOW()),
('62000000-0000-4000-8000-000000000010', 'Luggage Tag',         'Branded Gola luggage tag',       75,  150, 'https://cdn.gola.app/rewards/tag.png',     true, NOW() - INTERVAL '15 days', NOW());

-- -----------------------------------------------------------------------------
-- 11. QUESTS (10)
-- -----------------------------------------------------------------------------
INSERT INTO quests (id, type, title, description, reward_coins, badge_id, geom, radius_m, is_featured, is_active, created_at) VALUES
('70000000-0000-4000-8000-000000000001', 'SOLO',      'Ben Thanh Explorer',    'Check in at Ben Thanh Market',        100, '50000000-0000-4000-8000-000000000002', ST_SetSRID(ST_MakePoint(106.6981, 10.7720), 4326)::geography, 200,  true,  true, NOW() - INTERVAL '50 days'),
('70000000-0000-4000-8000-000000000002', 'SOLO',      'Hanoi Lake Walker',     'Walk around Hoan Kiem Lake',          120, NULL, ST_SetSRID(ST_MakePoint(105.8525, 21.0287), 4326)::geography, 300,  false, true, NOW() - INTERVAL '48 days'),
('70000000-0000-4000-8000-000000000003', 'TEAM',      'Da Nang Beach Day',     'Team photo at My Khe Beach',          200, '50000000-0000-4000-8000-000000000007', ST_SetSRID(ST_MakePoint(108.2498, 16.0678), 4326)::geography, 250,  true,  true, NOW() - INTERVAL '45 days'),
('70000000-0000-4000-8000-000000000004', 'COMMUNITY', 'Hoi An Heritage',       'Visit Japanese Bridge with friends',  150, NULL, ST_SetSRID(ST_MakePoint(108.3350, 15.8774), 4326)::geography, 150,  true,  true, NOW() - INTERVAL '42 days'),
('70000000-0000-4000-8000-000000000005', 'SOLO',      'Sapa Summit Seeker',    'Reach Fansipan viewpoint',            300, '50000000-0000-4000-8000-000000000004', ST_SetSRID(ST_MakePoint(103.8410, 22.3364), 4326)::geography, 500,  true,  true, NOW() - INTERVAL '40 days'),
('70000000-0000-4000-8000-000000000006', 'SOLO',      'Egg Coffee Quest',      'Try egg coffee at Giang Cafe',         80,  '50000000-0000-4000-8000-000000000006', ST_SetSRID(ST_MakePoint(105.8485, 21.0295), 4326)::geography, 100,  false, true, NOW() - INTERVAL '35 days'),
('70000000-0000-4000-8000-000000000007', 'TEAM',      'Cu Chi History',        'Explore Cu Chi Tunnels',              180, NULL, ST_SetSRID(ST_MakePoint(106.4600, 11.1520), 4326)::geography, 400,  false, true, NOW() - INTERVAL '30 days'),
('70000000-0000-4000-8000-000000000008', 'COMMUNITY', 'Landmark 81 Sky',       'Photo from Landmark 81',              250, NULL, ST_SetSRID(ST_MakePoint(106.7220, 10.7951), 4326)::geography, 200,  true,  true, NOW() - INTERVAL '25 days'),
('70000000-0000-4000-8000-000000000009', 'SOLO',      'Phu Quoc Sunset',       'Sunset at Sao Beach',                 220, NULL, ST_SetSRID(ST_MakePoint(103.9570, 10.2270), 4326)::geography, 300,  false, true, NOW() - INTERVAL '20 days'),
('70000000-0000-4000-8000-000000000010', 'TEAM',      'Mekong River Cruise',   'Board at Ninh Kieu Wharf',            160, NULL, ST_SetSRID(ST_MakePoint(105.7850, 10.0450), 4326)::geography, 350,  false, true, NOW() - INTERVAL '15 days');

-- -----------------------------------------------------------------------------
-- 12. ROUTES CACHE (10)
-- -----------------------------------------------------------------------------
INSERT INTO routes_cache (id, hash, geometry, distance_m, duration_s, mode, provider, created_at) VALUES
('80000000-0000-4000-8000-000000000001', 'hash_hcm_hanoi_01',  'LINESTRING(106.7 10.77, 105.85 21.03)', 1700000, 72000,  'drive', 'google', NOW() - INTERVAL '30 days'),
('80000000-0000-4000-8000-000000000002', 'hash_hcm_danang_01', 'LINESTRING(106.7 10.77, 108.2 16.05)',   950000,  39600,  'drive', 'google', NOW() - INTERVAL '28 days'),
('80000000-0000-4000-8000-000000000003', 'hash_danang_hoian',  'LINESTRING(108.2 16.05, 108.33 15.88)',   30000,   2400,   'drive', 'google', NOW() - INTERVAL '25 days'),
('80000000-0000-4000-8000-000000000004', 'hash_hanoi_sapa_01', 'LINESTRING(105.85 21.03, 103.84 22.34)',  380000,  25200,  'drive', 'google', NOW() - INTERVAL '22 days'),
('80000000-0000-4000-8000-000000000005', 'hash_hcm_nhatrang',  'LINESTRING(106.7 10.77, 109.2 12.24)',   450000,  19800,  'drive', 'google', NOW() - INTERVAL '20 days'),
('80000000-0000-4000-8000-000000000006', 'hash_hcm_dalat_01',  'LINESTRING(106.7 10.77, 108.44 11.94)',  310000,  18000,  'drive', 'google', NOW() - INTERVAL '18 days'),
('80000000-0000-4000-8000-000000000007', 'hash_hcm_cuchi_01',  'LINESTRING(106.7 10.77, 106.46 11.15)',   70000,   5400,   'drive', 'google', NOW() - INTERVAL '15 days'),
('80000000-0000-4000-8000-000000000008', 'hash_hcm_phuquoc',   'LINESTRING(106.7 10.77, 103.96 10.23)',  320000,  54000,  'fly',   'manual', NOW() - INTERVAL '12 days'),
('80000000-0000-4000-8000-000000000009', 'hash_hue_danang_01', 'LINESTRING(107.58 16.47, 108.2 16.05)',  105000,   7200,   'drive', 'google', NOW() - INTERVAL '10 days'),
('80000000-0000-4000-8000-000000000010', 'hash_hcm_vungtau',   'LINESTRING(106.7 10.77, 107.08 10.35)',    95000,   6300,   'drive', 'google', NOW() - INTERVAL '8 days');

-- -----------------------------------------------------------------------------
-- 13. TRIPS (15)
-- -----------------------------------------------------------------------------
INSERT INTO trips (id, owner_id, title, origin, destination, start_date, end_date, status, is_public, cover_url, description, created_at, updated_at) VALUES
('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Saigon Street Food Tour',  'Ho Chi Minh City', 'Ho Chi Minh City', '2025-03-01', '2025-03-03', 'COMPLETED', true,  'https://cdn.gola.app/trips/saigon-food.jpg',  '3-day food adventure', NOW() - INTERVAL '60 days', NOW()),
('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Central Vietnam Coast',    'Da Nang',          'Hoi An',           '2025-04-10', '2025-04-15', 'ACTIVE',    true,  'https://cdn.gola.app/trips/coast.jpg',         'Beach and heritage',   NOW() - INTERVAL '45 days', NOW()),
('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 'Hanoi Winter Escape',      'Hanoi',            'Sapa',             '2025-12-20', '2025-12-25', 'DRAFT',     false, NULL,                                          'Mountain getaway',     NOW() - INTERVAL '30 days', NOW()),
('90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'Nha Trang Diving Trip',    'Nha Trang',        'Nha Trang',        '2025-06-01', '2025-06-05', 'COMPLETED', true,  'https://cdn.gola.app/trips/diving.jpg',        'Underwater adventure', NOW() - INTERVAL '50 days', NOW()),
('90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'Mekong Family Weekend',    'Can Tho',          'Can Tho',          '2025-05-15', '2025-05-17', 'ACTIVE',    false, 'https://cdn.gola.app/trips/mekong.jpg',        'Family river tour',    NOW() - INTERVAL '25 days', NOW()),
('90000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', 'Sapa Trekking',            'Sapa',             'Sapa',             '2025-11-01', '2025-11-04', 'DRAFT',     true,  'https://cdn.gola.app/trips/sapa.jpg',          'Mountain trails',      NOW() - INTERVAL '20 days', NOW()),
('90000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 'Hue Imperial Tour',        'Hue',              'Hue',              '2025-07-20', '2025-07-22', 'COMPLETED', true,  'https://cdn.gola.app/trips/hue.jpg',           'History and culture',  NOW() - INTERVAL '40 days', NOW()),
('90000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'Nomad HCMC Month',         'Ho Chi Minh City', 'Ho Chi Minh City', '2025-08-01', '2025-08-31', 'ACTIVE',    false, NULL,                                          'Remote work base',     NOW() - INTERVAL '35 days', NOW()),
('90000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', 'Hoi An Lantern Festival',  'Hoi An',           'Hoi An',           '2025-02-14', '2025-02-16', 'ARCHIVED',  true,  'https://cdn.gola.app/trips/lantern.jpg',       'Valentine weekend',    NOW() - INTERVAL '90 days', NOW()),
('90000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', 'Dalat Motorbike Loop',     'Dalat',            'Dalat',            '2025-09-10', '2025-09-12', 'DRAFT',     true,  'https://cdn.gola.app/trips/dalat.jpg',         'Highland ride',        NOW() - INTERVAL '15 days', NOW()),
('90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 'Phu Quoc Island Hop',      'Ho Chi Minh City', 'Phu Quoc',         '2025-10-01', '2025-10-05', 'ACTIVE',    true,  'https://cdn.gola.app/trips/phuquoc.jpg',       'Island holiday',       NOW() - INTERVAL '10 days', NOW()),
('90000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000002', 'HCMC to Hanoi Express',    'Ho Chi Minh City', 'Hanoi',            '2025-11-15', '2025-11-20', 'DRAFT',     false, NULL,                                          'Northbound journey',   NOW() - INTERVAL '8 days',  NOW()),
('90000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 'Vung Tau Day Trip',        'Ho Chi Minh City', 'Vung Tau',         '2025-04-20', '2025-04-20', 'COMPLETED', true,  NULL,                                          'Quick beach escape',   NOW() - INTERVAL '35 days', NOW()),
('90000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000008', 'Landmark 81 Experience',   'Ho Chi Minh City', 'Ho Chi Minh City', '2025-05-01', '2025-05-01', 'COMPLETED', false, 'https://cdn.gola.app/trips/landmark.jpg',      'Sky deck visit',       NOW() - INTERVAL '28 days', NOW()),
('90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000003', 'Cu Chi & City Combo',      'Ho Chi Minh City', 'Ho Chi Minh City', '2025-06-15', '2025-06-16', 'ACTIVE',    true,  NULL,                                          'History day trip',     NOW() - INTERVAL '18 days', NOW());

-- -----------------------------------------------------------------------------
-- 14. TRIP STOPS (20)
-- -----------------------------------------------------------------------------
INSERT INTO trip_stops (id, trip_id, place_id, order_idx, name, arrival_at, duration_min, notes, created_at) VALUES
('91000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', 1, 'Ben Thanh Market',     NOW() - INTERVAL '58 days', 120, 'Try banh mi', NOW() - INTERVAL '59 days'),
('91000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000012', 2, 'Pizza 4Ps',            NOW() - INTERVAL '57 days',  90, 'Dinner',      NOW() - INTERVAL '59 days'),
('91000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000004', 1, 'My Khe Beach',         NOW() - INTERVAL '40 days', 180, 'Morning swim',NOW() - INTERVAL '44 days'),
('91000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000005', 2, 'Japanese Bridge',      NOW() - INTERVAL '38 days', 120, 'Old town',    NOW() - INTERVAL '44 days'),
('91000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000003', 1, 'Hoan Kiem Lake',       NULL, 90,  'Planned',     NOW() - INTERVAL '29 days'),
('91000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000006', 2, 'Fansipan',             NULL, 480, 'Cable car',   NOW() - INTERVAL '29 days'),
('91000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000004', '40000000-0000-4000-8000-000000000008', 1, 'Nha Trang Bay',        NOW() - INTERVAL '48 days', 240, 'Diving',      NOW() - INTERVAL '49 days'),
('91000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000005', '40000000-0000-4000-8000-000000000017', 1, 'Mekong Pier',          NOW() - INTERVAL '20 days', 300, 'Boat tour',   NOW() - INTERVAL '24 days'),
('91000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000007', '40000000-0000-4000-8000-000000000007', 1, 'Imperial Citadel',     NOW() - INTERVAL '38 days', 180, 'Guided tour', NOW() - INTERVAL '39 days'),
('91000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000011', '40000000-0000-4000-8000-000000000010', 1, 'Sao Beach',            NOW() - INTERVAL '5 days',  360, 'Beach day',   NOW() - INTERVAL '9 days'),
('91000000-0000-4000-8000-000000000011', '90000000-0000-4000-8000-000000000011', '40000000-0000-4000-8000-000000000015', 2, 'Tan Son Nhat',         NOW() - INTERVAL '4 days',   60, 'Flight home', NOW() - INTERVAL '9 days'),
('91000000-0000-4000-8000-000000000012', '90000000-0000-4000-8000-000000000013', '40000000-0000-4000-8000-000000000018', 1, 'Vung Tau Statue',      NOW() - INTERVAL '33 days', 150, 'Hike up',     NOW() - INTERVAL '34 days'),
('91000000-0000-4000-8000-000000000013', '90000000-0000-4000-8000-000000000014', '40000000-0000-4000-8000-000000000014', 1, 'Landmark 81',          NOW() - INTERVAL '27 days', 120, 'SkyView',     NOW() - INTERVAL '27 days'),
('91000000-0000-4000-8000-000000000014', '90000000-0000-4000-8000-000000000015', '40000000-0000-4000-8000-000000000016', 1, 'Cu Chi Tunnels',       NOW() - INTERVAL '10 days', 240, 'Morning tour',NOW() - INTERVAL '17 days'),
('91000000-0000-4000-8000-000000000015', '90000000-0000-4000-8000-000000000015', '40000000-0000-4000-8000-000000000001', 2, 'Ben Thanh',            NOW() - INTERVAL '9 days',   90, 'Souvenirs',   NOW() - INTERVAL '17 days'),
('91000000-0000-4000-8000-000000000016', '90000000-0000-4000-8000-000000000009', '40000000-0000-4000-8000-000000000005', 1, 'Japanese Bridge',      NOW() - INTERVAL '88 days', 90, 'Night lights',NOW() - INTERVAL '89 days'),
('91000000-0000-4000-8000-000000000017', '90000000-0000-4000-8000-000000000010', '40000000-0000-4000-8000-000000000009', 1, 'Dalat Market',         NULL, 120, 'Night market',NOW() - INTERVAL '14 days'),
('91000000-0000-4000-8000-000000000018', '90000000-0000-4000-8000-000000000012', '40000000-0000-4000-8000-000000000003', 1, 'Hoan Kiem Lake',       NULL, 60,  'Stop 1',      NOW() - INTERVAL '7 days'),
('91000000-0000-4000-8000-000000000019', '90000000-0000-4000-8000-000000000012', '40000000-0000-4000-8000-000000000019', 2, 'Water Puppet',         NULL, 90,  'Stop 2',      NOW() - INTERVAL '7 days'),
('91000000-0000-4000-8000-000000000020', '90000000-0000-4000-8000-000000000008', '40000000-0000-4000-8000-000000000011', 1, 'Cong Caphe',           NOW() - INTERVAL '20 days', 45,  'Work cafe',   NOW() - INTERVAL '34 days');

-- -----------------------------------------------------------------------------
-- 15. TRIP MEMBERS (25)
-- -----------------------------------------------------------------------------
INSERT INTO trip_members (trip_id, user_id, role, joined_at) VALUES
('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'OWNER',  NOW() - INTERVAL '60 days'),
('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'EDITOR', NOW() - INTERVAL '59 days'),
('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'OWNER',  NOW() - INTERVAL '45 days'),
('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'VIEWER', NOW() - INTERVAL '44 days'),
('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000009', 'EDITOR', NOW() - INTERVAL '43 days'),
('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 'OWNER',  NOW() - INTERVAL '30 days'),
('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000006', 'VIEWER', NOW() - INTERVAL '29 days'),
('90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'OWNER',  NOW() - INTERVAL '50 days'),
('90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'OWNER',  NOW() - INTERVAL '25 days'),
('90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'VIEWER', NOW() - INTERVAL '24 days'),
('90000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 'OWNER',  NOW() - INTERVAL '40 days'),
('90000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'OWNER',  NOW() - INTERVAL '35 days'),
('90000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', 'OWNER',  NOW() - INTERVAL '90 days'),
('90000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', 'VIEWER', NOW() - INTERVAL '89 days'),
('90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 'OWNER',  NOW() - INTERVAL '10 days'),
('90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 'EDITOR', NOW() - INTERVAL '9 days'),
('90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000013', 'VIEWER', NOW() - INTERVAL '8 days'),
('90000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 'OWNER',  NOW() - INTERVAL '35 days'),
('90000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000008', 'OWNER',  NOW() - INTERVAL '28 days'),
('90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000003', 'OWNER',  NOW() - INTERVAL '18 days'),
('90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000001', 'EDITOR', NOW() - INTERVAL '17 days'),
('90000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', 'OWNER',  NOW() - INTERVAL '20 days'),
('90000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', 'OWNER',  NOW() - INTERVAL '15 days'),
('90000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000002', 'OWNER',  NOW() - INTERVAL '8 days'),
('90000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000003', 'EDITOR', NOW() - INTERVAL '7 days');

-- -----------------------------------------------------------------------------
-- 16. TRIP INVITATIONS (10)
-- -----------------------------------------------------------------------------
INSERT INTO trip_invitations (id, trip_id, email, phone, user_id, token, role, status, expires_at, created_by, created_at, updated_at) VALUES
('92000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000003', 'guest1@example.com', NULL, '10000000-0000-4000-8000-000000000013', 'inv_token_001', 'VIEWER', 'PENDING',  NOW() + INTERVAL '7 days',  '10000000-0000-4000-8000-000000000003', NOW() - INTERVAL '5 days', NOW()),
('92000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000006', 'khoa.bui@example.com', NULL, '10000000-0000-4000-8000-000000000006', 'inv_token_002', 'EDITOR', 'ACCEPTED', NOW() + INTERVAL '14 days', '10000000-0000-4000-8000-000000000006', NOW() - INTERVAL '18 days', NOW()),
('92000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000010', 'tuan.pham@example.com', NULL, NULL, 'inv_token_003', 'VIEWER', 'PENDING',  NOW() + INTERVAL '5 days',  '10000000-0000-4000-8000-000000000010', NOW() - INTERVAL '3 days', NOW()),
('92000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000012', 'hana.pham@example.com', NULL, '10000000-0000-4000-8000-000000000003', 'inv_token_004', 'EDITOR', 'PENDING',  NOW() + INTERVAL '10 days', '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '6 days', NOW()),
('92000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000002', 'david.le@example.com', NULL, '10000000-0000-4000-8000-000000000004', 'inv_token_005', 'VIEWER', 'ACCEPTED', NOW() + INTERVAL '3 days',  '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '42 days', NOW()),
('92000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000011', 'alex.hoang@example.com', NULL, '10000000-0000-4000-8000-000000000008', 'inv_token_006', 'EDITOR', 'ACCEPTED', NOW() + INTERVAL '7 days',  '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '8 days', NOW()),
('92000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000005', 'linh.nguyen@example.com', NULL, '10000000-0000-4000-8000-000000000001', 'inv_token_007', 'VIEWER', 'DECLINED', NOW() + INTERVAL '2 days',  '10000000-0000-4000-8000-000000000005', NOW() - INTERVAL '22 days', NOW()),
('92000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000008', 'mai.vo@example.com', NULL, '10000000-0000-4000-8000-000000000005', 'inv_token_008', 'VIEWER', 'EXPIRED',  NOW() - INTERVAL '1 day',  '10000000-0000-4000-8000-000000000008', NOW() - INTERVAL '30 days', NOW()),
('92000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000015', 'minh.tran@example.com', NULL, '10000000-0000-4000-8000-000000000002', 'inv_token_009', 'EDITOR', 'PENDING',  NOW() + INTERVAL '6 days',  '10000000-0000-4000-8000-000000000003', NOW() - INTERVAL '2 days', NOW()),
('92000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000001', 'guest2@example.com', NULL, '10000000-0000-4000-8000-000000000014', 'inv_token_010', 'VIEWER', 'PENDING',  NOW() + INTERVAL '14 days', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '1 day', NOW());

-- -----------------------------------------------------------------------------
-- 17. TRIP SHARES (10)
-- -----------------------------------------------------------------------------
INSERT INTO trip_shares (id, trip_id, token, scope, expires_at, created_by, created_at) VALUES
('93000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', 'share_token_001', 'VIEW', NOW() + INTERVAL '30 days', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '55 days'),
('93000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', 'share_token_002', 'JOIN', NOW() + INTERVAL '14 days', '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '40 days'),
('93000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000004', 'share_token_003', 'VIEW', NULL,                         '10000000-0000-4000-8000-000000000004', NOW() - INTERVAL '48 days'),
('93000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000007', 'share_token_004', 'VIEW', NOW() + INTERVAL '7 days',  '10000000-0000-4000-8000-000000000007', NOW() - INTERVAL '35 days'),
('93000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000009', 'share_token_005', 'VIEW', NOW() - INTERVAL '10 days', '10000000-0000-4000-8000-000000000009', NOW() - INTERVAL '85 days'),
('93000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000011', 'share_token_006', 'JOIN', NOW() + INTERVAL '21 days', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '8 days'),
('93000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000013', 'share_token_007', 'VIEW', NOW() + INTERVAL '5 days',  '10000000-0000-4000-8000-000000000004', NOW() - INTERVAL '32 days'),
('93000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000015', 'share_token_008', 'VIEW', NOW() + INTERVAL '10 days', '10000000-0000-4000-8000-000000000003', NOW() - INTERVAL '15 days'),
('93000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000005', 'share_token_009', 'JOIN', NOW() + INTERVAL '3 days',  '10000000-0000-4000-8000-000000000005', NOW() - INTERVAL '20 days'),
('93000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000008', 'share_token_010', 'VIEW', NOW() + INTERVAL '60 days', '10000000-0000-4000-8000-000000000008', NOW() - INTERVAL '30 days');

-- -----------------------------------------------------------------------------
-- 18. TRIP SESSIONS (10)
-- -----------------------------------------------------------------------------
INSERT INTO trip_sessions (id, trip_id, status, started_at, ended_at) VALUES
('94000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', 'ENDED',       NOW() - INTERVAL '55 days', NOW() - INTERVAL '53 days'),
('94000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', 'ACTIVE',      NOW() - INTERVAL '2 days',  NULL),
('94000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000004', 'ENDED',       NOW() - INTERVAL '48 days', NOW() - INTERVAL '45 days'),
('94000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000005', 'ACTIVE',      NOW() - INTERVAL '1 day',   NULL),
('94000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000007', 'ENDED',       NOW() - INTERVAL '38 days', NOW() - INTERVAL '36 days'),
('94000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000011', 'ACTIVE',      NOW() - INTERVAL '3 hours', NULL),
('94000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000013', 'ENDED',       NOW() - INTERVAL '33 days', NOW() - INTERVAL '33 days'),
('94000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000014', 'ENDED',       NOW() - INTERVAL '27 days', NOW() - INTERVAL '27 days'),
('94000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000015', 'ACTIVE',      NOW() - INTERVAL '6 hours', NULL),
('94000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000008', 'AUTO_CLOSED', NOW() - INTERVAL '20 days', NOW() - INTERVAL '19 days');

-- -----------------------------------------------------------------------------
-- 19. MEDIA (15)
-- -----------------------------------------------------------------------------
INSERT INTO media (id, owner_id, storage_path, mime_type, width, height, ai_caption, created_at) VALUES
('95000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '/media/2025/03/linh_ben_thanh.jpg',   'image/jpeg', 1920, 1080, 'Busy market scene with food stalls', NOW() - INTERVAL '58 days'),
('95000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', '/media/2025/04/minh_beach.jpg',       'image/jpeg', 1280,  720, 'Sandy beach at sunset',            NOW() - INTERVAL '40 days'),
('95000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', '/media/2025/12/hana_lake.jpg',        'image/jpeg', 2048, 1536, 'Lake with red bridge',             NOW() - INTERVAL '5 days'),
('95000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', '/media/2025/06/david_dive.jpg',       'image/jpeg', 1600, 1200, 'Scuba diver near coral',           NOW() - INTERVAL '48 days'),
('95000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', '/media/2025/05/mai_boat.jpg',         'image/jpeg', 1920, 1080, 'Wooden boat on river',             NOW() - INTERVAL '20 days'),
('95000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', '/media/2025/11/khoa_mountain.jpg',    'image/jpeg', 1280,  960, 'Foggy mountain trail',             NOW() - INTERVAL '3 days'),
('95000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', '/media/2025/07/thu_citadel.jpg',      'image/jpeg', 1920, 1280, 'Ancient gate entrance',            NOW() - INTERVAL '38 days'),
('95000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', '/media/2025/08/alex_cowork.jpg',      'image/jpeg', 1280,  720, 'Laptop at cafe workspace',         NOW() - INTERVAL '25 days'),
('95000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', '/media/2025/02/yen_lantern.jpg',      'image/jpeg', 1080, 1080, 'Colorful lanterns at night',       NOW() - INTERVAL '88 days'),
('95000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', '/media/2025/09/tuan_road.jpg',        'image/jpeg', 1920, 1080, 'Winding mountain road',            NOW() - INTERVAL '10 days'),
('95000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', '/media/2025/10/linh_island.jpg',      'image/jpeg', 2048, 1365, 'Tropical island shoreline',        NOW() - INTERVAL '5 days'),
('95000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000004', '/media/2025/04/david_vungtau.jpg',    'image/jpeg', 1280,  720, 'Coastal statue viewpoint',         NOW() - INTERVAL '33 days'),
('95000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000008', '/media/2025/05/alex_skyline.jpg',     'image/jpeg', 1920, 1080, 'City skyline from skydeck',        NOW() - INTERVAL '27 days'),
('95000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000003', '/media/2025/06/hana_tunnel.jpg',      'image/jpeg', 1600, 900,  'Historic tunnel entrance',         NOW() - INTERVAL '10 days'),
('95000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000002', '/media/2025/11/minh_coffee.jpg',      'image/jpeg', 1080, 1350, 'Vietnamese egg coffee',            NOW() - INTERVAL '2 days');

-- -----------------------------------------------------------------------------
-- 20. POSTS (15)
-- -----------------------------------------------------------------------------
INSERT INTO posts (id, author_id, body, media_urls, trip_id, is_hidden, created_at, updated_at) VALUES
('96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Best banh mi in District 1! #vietnam #foodtour', ARRAY['https://cdn.gola.app/posts/01.jpg'], '90000000-0000-4000-8000-000000000001', false, NOW() - INTERVAL '57 days', NOW()),
('96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Sunrise at My Khe Beach was incredible', ARRAY['https://cdn.gola.app/posts/02.jpg'], '90000000-0000-4000-8000-000000000002', false, NOW() - INTERVAL '39 days', NOW()),
('96000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 'Planning our Hanoi trip — any tips?', NULL, '90000000-0000-4000-8000-000000000003', false, NOW() - INTERVAL '10 days', NOW()),
('96000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'First dive in Nha Trang!', ARRAY['https://cdn.gola.app/posts/04.jpg'], '90000000-0000-4000-8000-000000000004', false, NOW() - INTERVAL '47 days', NOW()),
('96000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'Kids loved the Mekong boat ride', ARRAY['https://cdn.gola.app/posts/05.jpg'], '90000000-0000-4000-8000-000000000005', false, NOW() - INTERVAL '19 days', NOW()),
('96000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', 'Sapa clouds above the valley', ARRAY['https://cdn.gola.app/posts/06.jpg'], NULL, false, NOW() - INTERVAL '8 days', NOW()),
('96000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 'Hue citadel at golden hour', ARRAY['https://cdn.gola.app/posts/07.jpg'], '90000000-0000-4000-8000-000000000007', false, NOW() - INTERVAL '37 days', NOW()),
('96000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'Nomad life: cafe hopping in Saigon', NULL, '90000000-0000-4000-8000-000000000008', false, NOW() - INTERVAL '24 days', NOW()),
('96000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', 'Lantern night in Hoi An #hoian', ARRAY['https://cdn.gola.app/posts/09.jpg'], '90000000-0000-4000-8000-000000000009', false, NOW() - INTERVAL '87 days', NOW()),
('96000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', 'Dalat pine forest ride', ARRAY['https://cdn.gola.app/posts/10.jpg'], '90000000-0000-4000-8000-000000000010', false, NOW() - INTERVAL '12 days', NOW()),
('96000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 'Phu Quoc crystal water #beachlife', ARRAY['https://cdn.gola.app/posts/11.jpg'], '90000000-0000-4000-8000-000000000011', false, NOW() - INTERVAL '4 days', NOW()),
('96000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000002', 'Egg coffee in Hanoi hits different', ARRAY['https://cdn.gola.app/posts/12.jpg'], NULL, false, NOW() - INTERVAL '2 days', NOW()),
('96000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 'Quick Vung Tau escape', NULL, '90000000-0000-4000-8000-000000000013', false, NOW() - INTERVAL '32 days', NOW()),
('96000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000008', 'Views from Landmark 81', ARRAY['https://cdn.gola.app/posts/14.jpg'], '90000000-0000-4000-8000-000000000014', false, NOW() - INTERVAL '26 days', NOW()),
('96000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000013', 'Just joined Gola — excited to explore!', NULL, NULL, false, NOW() - INTERVAL '3 days', NOW());

-- -----------------------------------------------------------------------------
-- 21. POST HASHTAGS (20)
-- -----------------------------------------------------------------------------
INSERT INTO post_hashtags (post_id, tag) VALUES
('96000000-0000-4000-8000-000000000001', 'vietnam'), ('96000000-0000-4000-8000-000000000001', 'foodtour'),
('96000000-0000-4000-8000-000000000002', 'beachlife'), ('96000000-0000-4000-8000-000000000002', 'danang'),
('96000000-0000-4000-8000-000000000004', 'beachlife'), ('96000000-0000-4000-8000-000000000009', 'hoian'),
('96000000-0000-4000-8000-000000000009', 'vietnam'), ('96000000-0000-4000-8000-000000000010', 'roadtrip'),
('96000000-0000-4000-8000-000000000011', 'beachlife'), ('96000000-0000-4000-8000-000000000011', 'golatravel'),
('96000000-0000-4000-8000-000000000006', 'sapa'), ('96000000-0000-4000-8000-000000000007', 'vietnam'),
('96000000-0000-4000-8000-000000000003', 'hanoi'), ('96000000-0000-4000-8000-000000000012', 'hanoi'),
('96000000-0000-4000-8000-000000000008', 'hochiminh'), ('96000000-0000-4000-8000-000000000001', 'hochiminh'),
('96000000-0000-4000-8000-000000000010', 'backpacking'), ('96000000-0000-4000-8000-000000000015', 'solotravel'),
('96000000-0000-4000-8000-000000000005', 'weekendtrip');

UPDATE hashtags SET post_count = sub.cnt, last_used_at = NOW()
FROM (SELECT tag, COUNT(*) AS cnt FROM post_hashtags GROUP BY tag) sub
WHERE hashtags.tag = sub.tag;

-- -----------------------------------------------------------------------------
-- 22. COMMENTS (15)
-- -----------------------------------------------------------------------------
INSERT INTO comments (id, post_id, author_id, body, parent_id, is_hidden, created_at, updated_at) VALUES
('97000000-0000-4000-8000-000000000001', '96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Which stall did you like best?', NULL, false, NOW() - INTERVAL '56 days', NOW()),
('97000000-0000-4000-8000-000000000002', '96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Stall 12 near the east entrance!', '97000000-0000-4000-8000-000000000001', false, NOW() - INTERVAL '55 days', NOW()),
('97000000-0000-4000-8000-000000000003', '96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'Stunning photo!', NULL, false, NOW() - INTERVAL '38 days', NOW()),
('97000000-0000-4000-8000-000000000004', '96000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000006', 'Bring warm clothes for Sapa!', NULL, false, NOW() - INTERVAL '9 days', NOW()),
('97000000-0000-4000-8000-000000000005', '96000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000008', 'How deep was the dive?', NULL, false, NOW() - INTERVAL '46 days', NOW()),
('97000000-0000-4000-8000-000000000006', '96000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'Sounds like an amazing family trip', NULL, false, NOW() - INTERVAL '18 days', NOW()),
('97000000-0000-4000-8000-000000000007', '96000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', 'Hue is on my bucket list', NULL, false, NOW() - INTERVAL '36 days', NOW()),
('97000000-0000-4000-8000-000000000008', '96000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', 'Beautiful lanterns!', NULL, false, NOW() - INTERVAL '86 days', NOW()),
('97000000-0000-4000-8000-000000000009', '96000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 'Need to visit Phu Quoc soon', NULL, false, NOW() - INTERVAL '3 days', NOW()),
('97000000-0000-4000-8000-000000000010', '96000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000003', 'Giang Cafe is a must!', NULL, false, NOW() - INTERVAL '1 day', NOW()),
('97000000-0000-4000-8000-000000000011', '96000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000010', 'Which cafe is your favorite?', NULL, false, NOW() - INTERVAL '23 days', NOW()),
('97000000-0000-4000-8000-000000000012', '96000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000001', 'Welcome to Gola!', NULL, false, NOW() - INTERVAL '2 days', NOW()),
('97000000-0000-4000-8000-000000000013', '96000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000007', 'Ride safe!', NULL, false, NOW() - INTERVAL '11 days', NOW()),
('97000000-0000-4000-8000-000000000014', '96000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000004', 'Great skyline shot', NULL, false, NOW() - INTERVAL '25 days', NOW()),
('97000000-0000-4000-8000-000000000015', '96000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000010', 'Adding Sapa to my list', NULL, false, NOW() - INTERVAL '7 days', NOW());

-- -----------------------------------------------------------------------------
-- 23. REACTIONS (20)
-- -----------------------------------------------------------------------------
INSERT INTO reactions (id, post_id, user_id, kind, created_at) VALUES
('98000000-0000-4000-8000-000000000001', '96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'LIKE',  NOW() - INTERVAL '56 days'),
('98000000-0000-4000-8000-000000000002', '96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', 'LOVE',  NOW() - INTERVAL '55 days'),
('98000000-0000-4000-8000-000000000003', '96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'LIKE',  NOW() - INTERVAL '38 days'),
('98000000-0000-4000-8000-000000000004', '96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'WOW',   NOW() - INTERVAL '37 days'),
('98000000-0000-4000-8000-000000000005', '96000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'LIKE',  NOW() - INTERVAL '46 days'),
('98000000-0000-4000-8000-000000000006', '96000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000008', 'WOW',   NOW() - INTERVAL '45 days'),
('98000000-0000-4000-8000-000000000007', '96000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', 'LIKE',  NOW() - INTERVAL '36 days'),
('98000000-0000-4000-8000-000000000008', '96000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000001', 'LOVE',  NOW() - INTERVAL '86 days'),
('98000000-0000-4000-8000-000000000009', '96000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000007', 'LIKE',  NOW() - INTERVAL '85 days'),
('98000000-0000-4000-8000-000000000010', '96000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 'LOVE',  NOW() - INTERVAL '3 days'),
('98000000-0000-4000-8000-000000000011', '96000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000004', 'LIKE',  NOW() - INTERVAL '3 days'),
('98000000-0000-4000-8000-000000000012', '96000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000001', 'LIKE',  NOW() - INTERVAL '1 day'),
('98000000-0000-4000-8000-000000000013', '96000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000006', 'LIKE',  NOW() - INTERVAL '17 days'),
('98000000-0000-4000-8000-000000000014', '96000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000001', 'WOW',   NOW() - INTERVAL '25 days'),
('98000000-0000-4000-8000-000000000015', '96000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000010', 'LIKE',  NOW() - INTERVAL '7 days'),
('98000000-0000-4000-8000-000000000016', '96000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 'LIKE',  NOW() - INTERVAL '9 days'),
('98000000-0000-4000-8000-000000000017', '96000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000005', 'LIKE',  NOW() - INTERVAL '23 days'),
('98000000-0000-4000-8000-000000000018', '96000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000007', 'LIKE',  NOW() - INTERVAL '11 days'),
('98000000-0000-4000-8000-000000000019', '96000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000002', 'LOVE',  NOW() - INTERVAL '2 days'),
('98000000-0000-4000-8000-000000000020', '96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000004', 'LIKE',  NOW() - INTERVAL '54 days');

-- -----------------------------------------------------------------------------
-- 24. ALBUMS & ALBUM MEDIA (10 + 15)
-- -----------------------------------------------------------------------------
INSERT INTO albums (id, owner_id, trip_id, title, cover_url, is_public, is_ai_curated, created_at) VALUES
('99000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', 'Saigon Food Highlights', 'https://cdn.gola.app/albums/01-cover.jpg', true,  false, NOW() - INTERVAL '55 days'),
('99000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', 'Coastal Memories',       'https://cdn.gola.app/albums/02-cover.jpg', true,  true,  NOW() - INTERVAL '38 days'),
('99000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000004', 'Dive Log Photos',        'https://cdn.gola.app/albums/03-cover.jpg', false, false, NOW() - INTERVAL '46 days'),
('99000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000007', 'Hue Heritage',           'https://cdn.gola.app/albums/04-cover.jpg', true,  false, NOW() - INTERVAL '36 days'),
('99000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000009', NULL,                                   'Lantern Nights',         'https://cdn.gola.app/albums/05-cover.jpg', true,  false, NOW() - INTERVAL '85 days'),
('99000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000011', 'Phu Quoc 2025',          'https://cdn.gola.app/albums/06-cover.jpg', true,  true,  NOW() - INTERVAL '4 days'),
('99000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000008', 'Nomad Workspace',        NULL, true,  false, NOW() - INTERVAL '22 days'),
('99000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000006', NULL,                                   'Mountain Trails',        'https://cdn.gola.app/albums/08-cover.jpg', true,  false, NOW() - INTERVAL '7 days'),
('99000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000005', 'Mekong Family',          'https://cdn.gola.app/albums/09-cover.jpg', false, false, NOW() - INTERVAL '18 days'),
('99000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000010', 'Dalat Roads',            NULL, false, false, NOW() - INTERVAL '11 days');

INSERT INTO album_media (id, album_id, media_url, caption, order_idx, metadata) VALUES
('99100000-0000-4000-8000-000000000001', '99000000-0000-4000-8000-000000000001', 'https://cdn.gola.app/albums/01-1.jpg', 'Banh mi stall', 0, '{"camera":"iphone"}'),
('99100000-0000-4000-8000-000000000002', '99000000-0000-4000-8000-000000000001', 'https://cdn.gola.app/albums/01-2.jpg', 'Market aisle',  1, NULL),
('99100000-0000-4000-8000-000000000003', '99000000-0000-4000-8000-000000000002', 'https://cdn.gola.app/albums/02-1.jpg', 'Sunrise',       0, NULL),
('99100000-0000-4000-8000-000000000004', '99000000-0000-4000-8000-000000000002', 'https://cdn.gola.app/albums/02-2.jpg', 'Bridge',        1, NULL),
('99100000-0000-4000-8000-000000000005', '99000000-0000-4000-8000-000000000003', 'https://cdn.gola.app/albums/03-1.jpg', 'Underwater',    0, NULL),
('99100000-0000-4000-8000-000000000006', '99000000-0000-4000-8000-000000000004', 'https://cdn.gola.app/albums/04-1.jpg', 'Citadel gate',  0, NULL),
('99100000-0000-4000-8000-000000000007', '99000000-0000-4000-8000-000000000005', 'https://cdn.gola.app/albums/05-1.jpg', 'Lanterns',      0, NULL),
('99100000-0000-4000-8000-000000000008', '99000000-0000-4000-8000-000000000006', 'https://cdn.gola.app/albums/06-1.jpg', 'Beach',         0, NULL),
('99100000-0000-4000-8000-000000000009', '99000000-0000-4000-8000-000000000006', 'https://cdn.gola.app/albums/06-2.jpg', 'Resort',        1, NULL),
('99100000-0000-4000-8000-000000000010', '99000000-0000-4000-8000-000000000007', 'https://cdn.gola.app/albums/07-1.jpg', 'Desk setup',    0, NULL),
('99100000-0000-4000-8000-000000000011', '99000000-0000-4000-8000-000000000008', 'https://cdn.gola.app/albums/08-1.jpg', 'Trail',         0, NULL),
('99100000-0000-4000-8000-000000000012', '99000000-0000-4000-8000-000000000009', 'https://cdn.gola.app/albums/09-1.jpg', 'Boat',          0, NULL),
('99100000-0000-4000-8000-000000000013', '99000000-0000-4000-8000-000000000010', 'https://cdn.gola.app/albums/10-1.jpg', 'Pine road',     0, NULL),
('99100000-0000-4000-8000-000000000014', '99000000-0000-4000-8000-000000000001', 'https://cdn.gola.app/albums/01-3.jpg', 'Street food',   2, NULL),
('99100000-0000-4000-8000-000000000015', '99000000-0000-4000-8000-000000000004', 'https://cdn.gola.app/albums/04-2.jpg', 'Temple',        1, NULL);

-- -----------------------------------------------------------------------------
-- 25. QUEST TASKS (15)
-- -----------------------------------------------------------------------------
INSERT INTO quest_tasks (id, quest_id, idx, description, proof_type, radius_m, created_at, updated_at) VALUES
('a0000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001', 0, 'Take a photo at Ben Thanh entrance', 'PHOTO',   200, NOW() - INTERVAL '50 days', NOW()),
('a0000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000002', 0, 'Complete a lap around Hoan Kiem',    'GPS',     300, NOW() - INTERVAL '48 days', NOW()),
('a0000000-0000-4000-8000-000000000003', '70000000-0000-4000-8000-000000000003', 0, 'Group photo at My Khe Beach',      'PHOTO',   250, NOW() - INTERVAL '45 days', NOW()),
('a0000000-0000-4000-8000-000000000004', '70000000-0000-4000-8000-000000000004', 0, 'Check in at Japanese Bridge',      'CHECKIN', 150, NOW() - INTERVAL '42 days', NOW()),
('a0000000-0000-4000-8000-000000000005', '70000000-0000-4000-8000-000000000005', 0, 'Reach Fansipan viewpoint',         'GPS',     500, NOW() - INTERVAL '40 days', NOW()),
('a0000000-0000-4000-8000-000000000006', '70000000-0000-4000-8000-000000000006', 0, 'Order egg coffee at Giang Cafe',   'CHECKIN', 100, NOW() - INTERVAL '35 days', NOW()),
('a0000000-0000-4000-8000-000000000007', '70000000-0000-4000-8000-000000000007', 0, 'Enter Cu Chi tunnel zone',         'GPS',     400, NOW() - INTERVAL '30 days', NOW()),
('a0000000-0000-4000-8000-000000000008', '70000000-0000-4000-8000-000000000008', 0, 'Photo from Landmark 81 deck',      'PHOTO',   200, NOW() - INTERVAL '25 days', NOW()),
('a0000000-0000-4000-8000-000000000009', '70000000-0000-4000-8000-000000000009', 0, 'Sunset photo at Sao Beach',        'PHOTO',   300, NOW() - INTERVAL '20 days', NOW()),
('a0000000-0000-4000-8000-000000000010', '70000000-0000-4000-8000-000000000010', 0, 'Board Mekong cruise boat',         'CHECKIN', 350, NOW() - INTERVAL '15 days', NOW()),
('a0000000-0000-4000-8000-000000000011', '70000000-0000-4000-8000-000000000001', 1, 'Try 3 street food items',          'PHOTO',   NULL, NOW() - INTERVAL '50 days', NOW()),
('a0000000-0000-4000-8000-000000000012', '70000000-0000-4000-8000-000000000003', 1, 'Share team album',                 'PHOTO',   NULL, NOW() - INTERVAL '45 days', NOW()),
('a0000000-0000-4000-8000-000000000013', '70000000-0000-4000-8000-000000000005', 1, 'Summit selfie',                    'PHOTO',   NULL, NOW() - INTERVAL '40 days', NOW()),
('a0000000-0000-4000-8000-000000000014', '70000000-0000-4000-8000-000000000007', 1, 'Complete tunnel tour',           'CHECKIN', NULL, NOW() - INTERVAL '30 days', NOW()),
('a0000000-0000-4000-8000-000000000015', '70000000-0000-4000-8000-000000000008', 1, 'Post to community feed',           'PHOTO',   NULL, NOW() - INTERVAL '25 days', NOW());

-- -----------------------------------------------------------------------------
-- 26. QUEST PROGRESS (12)
-- -----------------------------------------------------------------------------
INSERT INTO quest_progress (id, quest_id, user_id, task_idx, status, proof_media_id, verified_at, started_at, completed_at, created_at, updated_at) VALUES
('a1000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 1, 'COMPLETED',   '95000000-0000-4000-8000-000000000001', NOW() - INTERVAL '55 days', NOW() - INTERVAL '58 days', NOW() - INTERVAL '55 days', NOW() - INTERVAL '58 days', NOW()),
('a1000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '5 days', NULL, NOW() - INTERVAL '5 days', NOW()),
('a1000000-0000-4000-8000-000000000003', '70000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '38 days', NULL, NOW() - INTERVAL '38 days', NOW()),
('a1000000-0000-4000-8000-000000000004', '70000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000009', 0, 'COMPLETED',   '95000000-0000-4000-8000-000000000009', NOW() - INTERVAL '84 days', NOW() - INTERVAL '86 days', NOW() - INTERVAL '84 days', NOW() - INTERVAL '86 days', NOW()),
('a1000000-0000-4000-8000-000000000005', '70000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000006', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '3 days', NULL, NOW() - INTERVAL '3 days', NOW()),
('a1000000-0000-4000-8000-000000000006', '70000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002', 0, 'COMPLETED',   '95000000-0000-4000-8000-000000000015', NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days', NOW()),
('a1000000-0000-4000-8000-000000000007', '70000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '10 days', NULL, NOW() - INTERVAL '10 days', NOW()),
('a1000000-0000-4000-8000-000000000008', '70000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 0, 'COMPLETED',   '95000000-0000-4000-8000-000000000013', NOW() - INTERVAL '26 days', NOW() - INTERVAL '27 days', NOW() - INTERVAL '26 days', NOW() - INTERVAL '27 days', NOW()),
('a1000000-0000-4000-8000-000000000009', '70000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000001', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '4 days', NULL, NOW() - INTERVAL '4 days', NOW()),
('a1000000-0000-4000-8000-000000000010', '70000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000005', 0, 'FAILED',      NULL, NULL, NOW() - INTERVAL '20 days', NOW() - INTERVAL '18 days', NOW() - INTERVAL '20 days', NOW()),
('a1000000-0000-4000-8000-000000000011', '70000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000004', 0, 'IN_PROGRESS', NULL, NULL, NOW() - INTERVAL '12 days', NULL, NOW() - INTERVAL '12 days', NOW()),
('a1000000-0000-4000-8000-000000000012', '70000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000007', 0, 'COMPLETED',   '95000000-0000-4000-8000-000000000007', NOW() - INTERVAL '36 days', NOW() - INTERVAL '38 days', NOW() - INTERVAL '36 days', NOW() - INTERVAL '38 days', NOW());

-- -----------------------------------------------------------------------------
-- 27. USER BADGES (12)
-- -----------------------------------------------------------------------------
INSERT INTO user_badges (id, user_id, badge_id, source_quest_id, earned_at) VALUES
('a2000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '85 days'),
('a2000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002', '70000000-0000-4000-8000-000000000001', NOW() - INTERVAL '54 days'),
('a2000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000006', NULL, NOW() - INTERVAL '30 days'),
('a2000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', '50000000-0000-4000-8000-000000000004', NULL, NOW() - INTERVAL '40 days'),
('a2000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000008', '50000000-0000-4000-8000-000000000009', '70000000-0000-4000-8000-000000000008', NOW() - INTERVAL '25 days'),
('a2000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000009', '50000000-0000-4000-8000-000000000003', NULL, NOW() - INTERVAL '60 days'),
('a2000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', '50000000-0000-4000-8000-000000000006', NULL, NOW() - INTERVAL '35 days'),
('a2000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000008', NULL, NOW() - INTERVAL '20 days'),
('a2000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000010', '50000000-0000-4000-8000-000000000001', NULL, NOW() - INTERVAL '28 days'),
('a2000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000006', '50000000-0000-4000-8000-000000000002', NULL, NOW() - INTERVAL '15 days'),
('a2000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000007', NULL, NOW() - INTERVAL '10 days'),
('a2000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000005', '50000000-0000-4000-8000-000000000008', NULL, NOW() - INTERVAL '22 days');

-- -----------------------------------------------------------------------------
-- 28. REDEMPTIONS (10)
-- -----------------------------------------------------------------------------
INSERT INTO redemptions (id, user_id, reward_id, code, status, redeemed_at, created_at, updated_at) VALUES
('a3000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000001', 'COFFEE-LINH-001', 'COMPLETED', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', NOW()),
('a3000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', '62000000-0000-4000-8000-000000000002', 'GRAB-MINH-002',   'COMPLETED', NOW() - INTERVAL '25 days', NOW() - INTERVAL '25 days', NOW()),
('a3000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', '62000000-0000-4000-8000-000000000003', 'MUSEUM-DAV-003',  'PENDING',   NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days', NOW()),
('a3000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000008', '62000000-0000-4000-8000-000000000005', 'AI-ALEX-004',     'COMPLETED', NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days', NOW()),
('a3000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', '62000000-0000-4000-8000-000000000006', 'PIZZA-LINH-005',  'PENDING',   NOW() - INTERVAL '5 days',  NOW() - INTERVAL '5 days',  NOW()),
('a3000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000009', '62000000-0000-4000-8000-000000000007', 'BEACH-YEN-006',   'COMPLETED', NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days', NOW()),
('a3000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000010', '62000000-0000-4000-8000-000000000010', 'TAG-TUAN-007',    'COMPLETED', NOW() - INTERVAL '8 days',  NOW() - INTERVAL '8 days',  NOW()),
('a3000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000006', '62000000-0000-4000-8000-000000000009', 'GUIDE-KHOA-008',  'COMPLETED', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', NOW()),
('a3000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', '62000000-0000-4000-8000-000000000004', 'HOSTEL-HANA-009', 'CANCELLED', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days', NOW()),
('a3000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000015', '62000000-0000-4000-8000-000000000008', 'FRAME-PART-010',  'PENDING',   NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days',  NOW());

-- -----------------------------------------------------------------------------
-- 29. FOLLOWS (20)
-- -----------------------------------------------------------------------------
INSERT INTO follows (follower_id, followee_id, created_at) VALUES
('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '70 days'),
('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', NOW() - INTERVAL '65 days'),
('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '68 days'),
('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', NOW() - INTERVAL '50 days'),
('10000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '60 days'),
('10000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '48 days'),
('10000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000008', NOW() - INTERVAL '35 days'),
('10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '40 days'),
('10000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000003', NOW() - INTERVAL '30 days'),
('10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', NOW() - INTERVAL '45 days'),
('10000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '38 days'),
('10000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000004', NOW() - INTERVAL '36 days'),
('10000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000007', NOW() - INTERVAL '42 days'),
('10000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000006', NOW() - INTERVAL '25 days'),
('10000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '5 days'),
('10000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000002', NOW() - INTERVAL '3 days'),
('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000009', NOW() - INTERVAL '55 days'),
('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000010', NOW() - INTERVAL '20 days'),
('10000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000006', NOW() - INTERVAL '28 days'),
('10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000008', NOW() - INTERVAL '32 days');

-- -----------------------------------------------------------------------------
-- 30. USER BLOCKS (5)
-- -----------------------------------------------------------------------------
INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES
('10000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000014', NOW() - INTERVAL '10 days'),
('10000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000014', NOW() - INTERVAL '5 days'),
('10000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000014', NOW() - INTERVAL '3 days'),
('10000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000013', NOW() - INTERVAL '2 days'),
('10000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000013', NOW() - INTERVAL '1 day');

-- -----------------------------------------------------------------------------
-- 31. PLACE FAVORITES (15)
-- -----------------------------------------------------------------------------
INSERT INTO place_favorites (user_id, place_id, created_at) VALUES
('10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', NOW() - INTERVAL '50 days'),
('10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000012', NOW() - INTERVAL '45 days'),
('10000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000004', NOW() - INTERVAL '40 days'),
('10000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000005', NOW() - INTERVAL '38 days'),
('10000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000003', NOW() - INTERVAL '30 days'),
('10000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000020', NOW() - INTERVAL '28 days'),
('10000000-0000-4000-8000-000000000004', '40000000-0000-4000-8000-000000000008', NOW() - INTERVAL '35 days'),
('10000000-0000-4000-8000-000000000005', '40000000-0000-4000-8000-000000000017', NOW() - INTERVAL '20 days'),
('10000000-0000-4000-8000-000000000006', '40000000-0000-4000-8000-000000000006', NOW() - INTERVAL '15 days'),
('10000000-0000-4000-8000-000000000007', '40000000-0000-4000-8000-000000000007', NOW() - INTERVAL '32 days'),
('10000000-0000-4000-8000-000000000008', '40000000-0000-4000-8000-000000000011', NOW() - INTERVAL '25 days'),
('10000000-0000-4000-8000-000000000009', '40000000-0000-4000-8000-000000000005', NOW() - INTERVAL '80 days'),
('10000000-0000-4000-8000-000000000010', '40000000-0000-4000-8000-000000000009', NOW() - INTERVAL '12 days'),
('10000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000010', NOW() - INTERVAL '4 days'),
('10000000-0000-4000-8000-000000000008', '40000000-0000-4000-8000-000000000014', NOW() - INTERVAL '26 days');

-- -----------------------------------------------------------------------------
-- 32. REVIEWS (12)
-- -----------------------------------------------------------------------------
INSERT INTO reviews (id, place_id, user_id, rating, body, is_hidden, created_at) VALUES
('a4000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 5, 'Amazing street food variety', false, NOW() - INTERVAL '48 days'),
('a4000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 5, 'Clean beach and great waves', false, NOW() - INTERVAL '36 days'),
('a4000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000009', 5, 'Magical at night with lanterns', false, NOW() - INTERVAL '82 days'),
('a4000000-0000-4000-8000-000000000004', '40000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000002', 4, 'Great pizza, bit crowded', false, NOW() - INTERVAL '20 days'),
('a4000000-0000-4000-8000-000000000005', '40000000-0000-4000-8000-000000000020', '10000000-0000-4000-8000-000000000003', 5, 'Best egg coffee in Hanoi', false, NOW() - INTERVAL '6 days'),
('a4000000-0000-4000-8000-000000000006', '40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 4, 'Rich history, hire a guide', false, NOW() - INTERVAL '30 days'),
('a4000000-0000-4000-8000-000000000007', '40000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000001', 5, 'Paradise beach', false, NOW() - INTERVAL '3 days'),
('a4000000-0000-4000-8000-000000000008', '40000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000008', 5, 'Breathtaking city views', false, NOW() - INTERVAL '25 days'),
('a4000000-0000-4000-8000-000000000009', '40000000-0000-4000-8000-000000000016', '10000000-0000-4000-8000-000000000003', 4, 'Educational but hot', false, NOW() - INTERVAL '8 days'),
('a4000000-0000-4000-8000-000000000010', '40000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 4, 'Good coffee, nice vibe', false, NOW() - INTERVAL '22 days'),
('a4000000-0000-4000-8000-000000000011', '40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000006', 4, 'Peaceful morning walk', false, NOW() - INTERVAL '12 days'),
('a4000000-0000-4000-8000-000000000012', '40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000006', 5, 'Worth the cable car ride', false, NOW() - INTERVAL '10 days');

-- -----------------------------------------------------------------------------
-- 33. REFRESH TOKENS (10)
-- -----------------------------------------------------------------------------
INSERT INTO refresh_tokens (id, user_id, token_hash, device_info, ip_address, expires_at, created_at) VALUES
('a5000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'hash_rt_linh_001',   'iPhone 15 / iOS 18', '192.168.1.10', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '1 day'),
('a5000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'hash_rt_minh_002',   'Samsung S24 / Android 14', '10.0.0.5', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '2 days'),
('a5000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000008', 'hash_rt_alex_003',   'MacBook Chrome', '172.16.0.8', NOW() + INTERVAL '14 days', NOW() - INTERVAL '3 hours'),
('a5000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000003', 'hash_rt_hana_004',   'iPad Air', '192.168.1.22', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '5 days'),
('a5000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000004', 'hash_rt_david_005',  'Pixel 8', '10.0.0.12', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '4 days'),
('a5000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000011', 'hash_rt_admin_006',  'Admin Workstation', '10.0.1.1', NOW() + INTERVAL '1 day',   NOW() - INTERVAL '1 hour'),
('a5000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', 'hash_rt_yen_007',    'iPhone 14', '192.168.2.5', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '6 days'),
('a5000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000010', 'hash_rt_tuan_008',   'Android moto', '10.0.0.20', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '7 days'),
('a5000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000013', 'hash_rt_guest1_009', 'Chrome mobile', '192.168.3.1', NOW() + INTERVAL '7 days',  NOW() - INTERVAL '2 days'),
('a5000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000001', 'hash_rt_linh_revoked','Old device', '192.168.1.9', NOW() - INTERVAL '1 day', NOW() - INTERVAL '30 days');

UPDATE refresh_tokens SET revoked_at = NOW() - INTERVAL '2 days' WHERE id = 'a5000000-0000-4000-8000-000000000010';

-- -----------------------------------------------------------------------------
-- 34. AUDIT LOGS (15)
-- -----------------------------------------------------------------------------
INSERT INTO audit_logs (user_id, action, resource, resource_id, ip_address, detail, created_at) VALUES
('10000000-0000-4000-8000-000000000001', 'LOGIN',           'auth',   NULL, '192.168.1.10', '{"method":"password"}', NOW() - INTERVAL '1 day'),
('10000000-0000-4000-8000-000000000002', 'LOGIN',           'auth',   NULL, '10.0.0.5',     '{"method":"oauth"}',    NOW() - INTERVAL '2 days'),
('10000000-0000-4000-8000-000000000001', 'TRIP_CREATE',     'trip',   '90000000-0000-4000-8000-000000000011', '192.168.1.10', '{}', NOW() - INTERVAL '10 days'),
('10000000-0000-4000-8000-000000000008', 'SUBSCRIBE',       'payment','61000000-0000-4000-8000-000000000001', '172.16.0.8', '{}', NOW() - INTERVAL '30 days'),
('10000000-0000-4000-8000-000000000011', 'ADMIN_LOGIN',     'auth',   NULL, '10.0.1.1',     '{}', NOW() - INTERVAL '1 hour'),
('10000000-0000-4000-8000-000000000004', 'SOS_TRIGGER',     'sos',    NULL, '10.0.0.12',    '{}', NOW() - INTERVAL '20 days'),
('10000000-0000-4000-8000-000000000003', 'QUEST_START',     'quest',  '70000000-0000-4000-8000-000000000002', '192.168.1.22', '{}', NOW() - INTERVAL '5 days'),
('10000000-0000-4000-8000-000000000009', 'POST_CREATE',     'post',   '96000000-0000-4000-8000-000000000009', '192.168.2.5', '{}', NOW() - INTERVAL '87 days'),
('10000000-0000-4000-8000-000000000012', 'MODERATION',      'post',   '96000000-0000-4000-8000-000000000015', '10.0.1.5',     '{"action":"approve"}', NOW() - INTERVAL '2 days'),
('10000000-0000-4000-8000-000000000002', 'FOLLOW',          'profile','10000000-0000-4000-8000-000000000001', '10.0.0.5', '{}', NOW() - INTERVAL '68 days'),
('10000000-0000-4000-8000-000000000005', 'REWARD_REDEEM',   'reward', '62000000-0000-4000-8000-000000000001', '10.0.0.8', '{}', NOW() - INTERVAL '25 days'),
('10000000-0000-4000-8000-000000000007', 'INCIDENT_REPORT', 'incident',NULL, '192.168.4.1', '{}', NOW() - INTERVAL '15 days'),
('10000000-0000-4000-8000-000000000010', 'LOCATION_PING',   'session','94000000-0000-4000-8000-000000000002', '10.0.0.20', '{}', NOW() - INTERVAL '1 hour'),
('10000000-0000-4000-8000-000000000001', 'LOGOUT',          'auth',   NULL, '192.168.1.10', '{}', NOW() - INTERVAL '12 hours'),
(NULL,                                   'SYSTEM_CLEANUP',  'jobs',   NULL, '127.0.0.1',    '{"deleted":42}', NOW() - INTERVAL '6 hours');

-- -----------------------------------------------------------------------------
-- 35. PASSWORD RESET AUDIT (8)
-- -----------------------------------------------------------------------------
INSERT INTO password_reset_audit (user_id, ip_address, created_at) VALUES
('10000000-0000-4000-8000-000000000013', '192.168.3.1',  NOW() - INTERVAL '8 days'),
('10000000-0000-4000-8000-000000000014', '192.168.3.2',  NOW() - INTERVAL '4 days'),
('10000000-0000-4000-8000-000000000003', '192.168.1.22', NOW() - INTERVAL '60 days'),
('10000000-0000-4000-8000-000000000001', '192.168.1.10', NOW() - INTERVAL '120 days'),
('10000000-0000-4000-8000-000000000008', '172.16.0.8',   NOW() - INTERVAL '90 days'),
(NULL,                                   '45.33.12.8',   NOW() - INTERVAL '2 days'),
('10000000-0000-4000-8000-000000000005', '10.0.0.8',     NOW() - INTERVAL '45 days'),
('10000000-0000-4000-8000-000000000002', '10.0.0.5',     NOW() - INTERVAL '30 days');

-- -----------------------------------------------------------------------------
-- 36. TRIP STORIES (8)
-- -----------------------------------------------------------------------------
INSERT INTO trip_stories (id, trip_id, author_id, body, cover_url, published_at, view_count, created_at, updated_at) VALUES
('a6000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Day 1 food crawl recap', 'https://cdn.gola.app/stories/01.jpg', NOW() - INTERVAL '54 days', 342, NOW() - INTERVAL '55 days', NOW()),
('a6000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Beach sunset highlights', 'https://cdn.gola.app/stories/02.jpg', NOW() - INTERVAL '37 days', 189, NOW() - INTERVAL '38 days', NOW()),
('a6000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 'Hue citadel photo essay', 'https://cdn.gola.app/stories/03.jpg', NOW() - INTERVAL '35 days', 256, NOW() - INTERVAL '36 days', NOW()),
('a6000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', 'Lantern festival memories', 'https://cdn.gola.app/stories/04.jpg', NOW() - INTERVAL '84 days', 1203, NOW() - INTERVAL '85 days', NOW()),
('a6000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 'Phu Quoc island diary', 'https://cdn.gola.app/stories/05.jpg', NOW() - INTERVAL '3 days',  78, NOW() - INTERVAL '4 days', NOW()),
('a6000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'Underwater adventures', 'https://cdn.gola.app/stories/06.jpg', NOW() - INTERVAL '44 days', 445, NOW() - INTERVAL '45 days', NOW()),
('a6000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'Mekong with the kids', NULL, NOW() - INTERVAL '17 days', 92, NOW() - INTERVAL '18 days', NOW()),
('a6000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 'Vung Tau day trip', 'https://cdn.gola.app/stories/08.jpg', NOW() - INTERVAL '32 days', 67, NOW() - INTERVAL '33 days', NOW());

-- -----------------------------------------------------------------------------
-- 37. LIVE LOCATIONS (20) — requires geom + lat/lng
-- -----------------------------------------------------------------------------
INSERT INTO live_locations (session_id, user_id, geom, lat, lng, heading, speed, accuracy, ts) VALUES
('94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', ST_SetSRID(ST_MakePoint(108.2498, 16.0678), 4326)::geography, 16.0678, 108.2498, 45.0,  0.0,  8.5, NOW() - INTERVAL '2 hours'),
('94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', ST_SetSRID(ST_MakePoint(108.2505, 16.0685), 4326)::geography, 16.0685, 108.2505, 90.0,  1.2, 10.0, NOW() - INTERVAL '1 hour 50 minutes'),
('94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000009', ST_SetSRID(ST_MakePoint(108.3350, 15.8774), 4326)::geography, 15.8774, 108.3350, 180.0, 0.5, 12.0, NOW() - INTERVAL '1 hour 40 minutes'),
('94000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000005', ST_SetSRID(ST_MakePoint(105.7850, 10.0450), 4326)::geography, 10.0450, 105.7850, 270.0, 2.0,  9.0, NOW() - INTERVAL '45 minutes'),
('94000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', ST_SetSRID(ST_MakePoint(105.7860, 10.0460), 4326)::geography, 10.0460, 105.7860, 275.0, 2.1,  9.5, NOW() - INTERVAL '40 minutes'),
('94000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', ST_SetSRID(ST_MakePoint(103.9570, 10.2270), 4326)::geography, 10.2270, 103.9570, 0.0,   0.0,  7.0, NOW() - INTERVAL '2 hours'),
('94000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000008', ST_SetSRID(ST_MakePoint(103.9580, 10.2280), 4326)::geography, 10.2280, 103.9580, 15.0,  0.3,  8.0, NOW() - INTERVAL '1 hour 55 minutes'),
('94000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', ST_SetSRID(ST_MakePoint(106.4600, 11.1520), 4326)::geography, 11.1520, 106.4600, 120.0, 1.5, 11.0, NOW() - INTERVAL '5 hours'),
('94000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000001', ST_SetSRID(ST_MakePoint(106.4610, 11.1530), 4326)::geography, 11.1530, 106.4610, 125.0, 1.6, 11.5, NOW() - INTERVAL '4 hours 50 minutes'),
('94000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', ST_SetSRID(ST_MakePoint(106.6981, 10.7720), 4326)::geography, 10.7720, 106.6981, 0.0,   0.0,  5.0, NOW() - INTERVAL '53 days'),
('94000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', ST_SetSRID(ST_MakePoint(106.6990, 10.7730), 4326)::geography, 10.7730, 106.6990, 10.0,  0.8,  6.0, NOW() - INTERVAL '53 days' + INTERVAL '5 minutes'),
('94000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', ST_SetSRID(ST_MakePoint(109.1967, 12.2388), 4326)::geography, 12.2388, 109.1967, 200.0, 0.0,  8.0, NOW() - INTERVAL '46 days'),
('94000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', ST_SetSRID(ST_MakePoint(107.5800, 16.4690), 4326)::geography, 16.4690, 107.5800, 45.0,  0.5,  9.0, NOW() - INTERVAL '37 days'),
('94000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000004', ST_SetSRID(ST_MakePoint(107.0790, 10.3460), 4326)::geography, 10.3460, 107.0790, 90.0,  0.0,  7.5, NOW() - INTERVAL '33 days'),
('94000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', ST_SetSRID(ST_MakePoint(106.7220, 10.7951), 4326)::geography, 10.7951, 106.7220, 0.0,   0.0,  4.0, NOW() - INTERVAL '27 days'),
('94000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000008', ST_SetSRID(ST_MakePoint(106.7045, 10.7769), 4326)::geography, 10.7769, 106.7045, 0.0,   0.0,  6.0, NOW() - INTERVAL '19 days'),
('94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', ST_SetSRID(ST_MakePoint(108.2510, 16.0690), 4326)::geography, 16.0690, 108.2510, 50.0,  1.5,  9.0, NOW() - INTERVAL '1 hour 30 minutes'),
('94000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000013', ST_SetSRID(ST_MakePoint(103.9575, 10.2275), 4326)::geography, 10.2275, 103.9575, 5.0,   0.2,  7.5, NOW() - INTERVAL '1 hour'),
('94000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', ST_SetSRID(ST_MakePoint(106.6985, 10.7725), 4326)::geography, 10.7725, 106.6985, 30.0,  1.0, 10.0, NOW() - INTERVAL '4 hours'),
('94000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000005', ST_SetSRID(ST_MakePoint(105.7855, 10.0455), 4326)::geography, 10.0455, 105.7855, 280.0, 2.2,  8.0, NOW() - INTERVAL '30 minutes');

-- -----------------------------------------------------------------------------
-- 38. TRIP CHAT (15)
-- -----------------------------------------------------------------------------
INSERT INTO trip_chat (id, session_id, user_id, body, ts) VALUES
('a7000000-0000-4000-8000-000000000001', '94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Heading to the beach now', NOW() - INTERVAL '2 hours'),
('a7000000-0000-4000-8000-000000000002', '94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'I will grab coffee first', NOW() - INTERVAL '1 hour 55 minutes'),
('a7000000-0000-4000-8000-000000000003', '94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000009', 'Meet at the bridge in 20 min', NOW() - INTERVAL '1 hour 45 minutes'),
('a7000000-0000-4000-8000-000000000004', '94000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000005', 'Kids are loving the boat ride', NOW() - INTERVAL '40 minutes'),
('a7000000-0000-4000-8000-000000000005', '94000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'Beach looks amazing today', NOW() - INTERVAL '2 hours'),
('a7000000-0000-4000-8000-000000000006', '94000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000008', 'On my way with snacks', NOW() - INTERVAL '1 hour 50 minutes'),
('a7000000-0000-4000-8000-000000000007', '94000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000003', 'Tunnel tour starts at 2pm', NOW() - INTERVAL '5 hours'),
('a7000000-0000-4000-8000-000000000008', '94000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Great food at Ben Thanh!', NOW() - INTERVAL '53 days'),
('a7000000-0000-4000-8000-000000000009', '94000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Try the pho next door', NOW() - INTERVAL '53 days' + INTERVAL '2 minutes'),
('a7000000-0000-4000-8000-000000000010', '94000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'Water is perfect for diving', NOW() - INTERVAL '46 days'),
('a7000000-0000-4000-8000-000000000011', '94000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', 'Citadel tour was incredible', NOW() - INTERVAL '37 days'),
('a7000000-0000-4000-8000-000000000012', '94000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000004', 'Statue view is worth the hike', NOW() - INTERVAL '33 days'),
('a7000000-0000-4000-8000-000000000013', '94000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'Skyline view is insane', NOW() - INTERVAL '27 days'),
('a7000000-0000-4000-8000-000000000014', '94000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000008', 'Working from Cong Caphe today', NOW() - INTERVAL '19 days'),
('a7000000-0000-4000-8000-000000000015', '94000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Traffic on the coast road', NOW() - INTERVAL '1 hour');

-- -----------------------------------------------------------------------------
-- 39. TRAFFIC ALERTS (8)
-- -----------------------------------------------------------------------------
INSERT INTO traffic_alerts (id, session_id, type, geom, message, severity, ts) VALUES
('a8000000-0000-4000-8000-000000000001', '94000000-0000-4000-8000-000000000002', 'CONGESTION', ST_SetSRID(ST_MakePoint(108.2400, 16.0600), 4326)::geography, 'Heavy traffic on coastal road', 'WARNING', NOW() - INTERVAL '1 hour'),
('a8000000-0000-4000-8000-000000000002', '94000000-0000-4000-8000-000000000002', 'ACCIDENT',   ST_SetSRID(ST_MakePoint(108.2550, 16.0700), 4326)::geography, 'Minor accident reported', 'CRITICAL', NOW() - INTERVAL '45 minutes'),
('a8000000-0000-4000-8000-000000000003', '94000000-0000-4000-8000-000000000004', 'FLOOD',      ST_SetSRID(ST_MakePoint(105.7800, 10.0400), 4326)::geography, 'Low flooding near pier', 'WARNING', NOW() - INTERVAL '30 minutes'),
('a8000000-0000-4000-8000-000000000004', '94000000-0000-4000-8000-000000000006', 'INFO',       ST_SetSRID(ST_MakePoint(103.9500, 10.2200), 4326)::geography, 'Ferry delay 30 minutes', 'INFO', NOW() - INTERVAL '2 hours'),
('a8000000-0000-4000-8000-000000000005', '94000000-0000-4000-8000-000000000009', 'ROADWORK',   ST_SetSRID(ST_MakePoint(106.4550, 11.1480), 4326)::geography, 'Road work near tunnels', 'WARNING', NOW() - INTERVAL '4 hours'),
('a8000000-0000-4000-8000-000000000006', '94000000-0000-4000-8000-000000000001', 'INFO',       ST_SetSRID(ST_MakePoint(106.6900, 10.7700), 4326)::geography, 'Market area crowded', 'INFO', NOW() - INTERVAL '53 days'),
('a8000000-0000-4000-8000-000000000007', '94000000-0000-4000-8000-000000000007', 'CONGESTION', ST_SetSRID(ST_MakePoint(107.0750, 10.3400), 4326)::geography, 'Weekend traffic to Vung Tau', 'WARNING', NOW() - INTERVAL '33 days'),
('a8000000-0000-4000-8000-000000000008', '94000000-0000-4000-8000-000000000010', 'INFO',       ST_SetSRID(ST_MakePoint(106.7000, 10.7750), 4326)::geography, 'Cafe street parking full', 'INFO', NOW() - INTERVAL '19 days');

-- -----------------------------------------------------------------------------
-- 40. EMERGENCY CONTACTS (15)
-- -----------------------------------------------------------------------------
INSERT INTO emergency_contacts (id, user_id, name, phone, relation, priority, verified_at, created_at) VALUES
('a9000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Nguyen Van A',  '+84901111001', 'Father',  1, NOW() - INTERVAL '80 days', NOW() - INTERVAL '85 days'),
('a9000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Tran Thi B',    '+84901111002', 'Mother',  2, NOW() - INTERVAL '80 days', NOW() - INTERVAL '85 days'),
('a9000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 'Le Van C',      '+84902222001', 'Brother', 1, NOW() - INTERVAL '70 days', NOW() - INTERVAL '75 days'),
('a9000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000003', 'Pham Thi D',    '+84903333001', 'Spouse',  1, NOW() - INTERVAL '60 days', NOW() - INTERVAL '65 days'),
('a9000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000004', 'Hoang Van E',   '+84904444001', 'Friend',  1, NULL, NOW() - INTERVAL '55 days'),
('a9000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000005', 'Vo Thi F',      '+84905555001', 'Spouse',  1, NOW() - INTERVAL '50 days', NOW() - INTERVAL '52 days'),
('a9000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000006', 'Bui Van G',     '+84906666001', 'Father',  1, NOW() - INTERVAL '45 days', NOW() - INTERVAL '48 days'),
('a9000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000007', 'Dang Thi H',    '+84907777001', 'Sister',  1, NOW() - INTERVAL '40 days', NOW() - INTERVAL '42 days'),
('a9000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000008', 'Hoang Van I',   '+84908888001', 'Partner', 1, NOW() - INTERVAL '35 days', NOW() - INTERVAL '38 days'),
('a9000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000009', 'Nguyen Thi K',  '+84909999001', 'Mother',  1, NOW() - INTERVAL '30 days', NOW() - INTERVAL '33 days'),
('a9000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000010', 'Pham Van L',    '+84910000001', 'Friend',  1, NULL, NOW() - INTERVAL '28 days'),
('a9000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000001', 'Emergency 2',   '+84911111111', 'Friend',  3, NULL, NOW() - INTERVAL '20 days'),
('a9000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 'Coast Guard',   '+84912222222', 'Service', 2, NOW() - INTERVAL '15 days', NOW() - INTERVAL '18 days'),
('a9000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000005', 'Grandma Mai',   '+84913333333', 'Family',  2, NOW() - INTERVAL '10 days', NOW() - INTERVAL '12 days'),
('a9000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000008', 'IT Support',    '+84914444444', 'Colleague',3, NULL, NOW() - INTERVAL '5 days');

-- -----------------------------------------------------------------------------
-- 41. EMERGENCY HOTLINES (additional rows; V8 already seeded 6)
-- -----------------------------------------------------------------------------
INSERT INTO emergency_hotlines (id, country, type, number) VALUES
('aa000000-0000-4000-8000-000000000001', 'VN', 'Tourist',  '18001166'),
('aa000000-0000-4000-8000-000000000002', 'VN', 'Coast Guard','18001199'),
('aa000000-0000-4000-8000-000000000003', 'TH', 'Emergency', '191'),
('aa000000-0000-4000-8000-000000000004', 'SG', 'Emergency', '999');

-- -----------------------------------------------------------------------------
-- 42. SOS EVENTS (8)
-- -----------------------------------------------------------------------------
INSERT INTO sos_events (id, user_id, trip_id, geom, latitude, longitude, status, client_token, resolved_at, resolved_by, created_at) VALUES
('ab000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000004', ST_SetSRID(ST_MakePoint(109.20, 12.24), 4326)::geography, 12.24, 109.20, 'RESOLVED',   'sos_token_001', NOW() - INTERVAL '19 days', '10000000-0000-4000-8000-000000000011', NOW() - INTERVAL '20 days'),
('ab000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000006', NULL, ST_SetSRID(ST_MakePoint(103.84, 22.34), 4326)::geography, 22.34, 103.84, 'FALSE_ALARM','sos_token_002', NOW() - INTERVAL '5 days',  '10000000-0000-4000-8000-000000000012', NOW() - INTERVAL '6 days'),
('ab000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', ST_SetSRID(ST_MakePoint(106.70, 10.77), 4326)::geography, 10.77, 106.70, 'RESOLVED',   'sos_token_003', NOW() - INTERVAL '50 days', '10000000-0000-4000-8000-000000000011', NOW() - INTERVAL '51 days'),
('ab000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000008', ST_SetSRID(ST_MakePoint(106.72, 10.80), 4326)::geography, 10.80, 106.72, 'ACTIVE',     'sos_token_004', NULL, NULL, NOW() - INTERVAL '1 hour'),
('ab000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000005', ST_SetSRID(ST_MakePoint(105.79, 10.05), 4326)::geography, 10.05, 105.79, 'RESOLVED',   'sos_token_005', NOW() - INTERVAL '16 days', '10000000-0000-4000-8000-000000000001', NOW() - INTERVAL '17 days'),
('ab000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000010', ST_SetSRID(ST_MakePoint(108.44, 11.94), 4326)::geography, 11.94, 108.44, 'RESOLVED',   'sos_token_006', NOW() - INTERVAL '10 days', '10000000-0000-4000-8000-000000000006', NOW() - INTERVAL '11 days'),
('ab000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000007', ST_SetSRID(ST_MakePoint(107.58, 16.47), 4326)::geography, 16.47, 107.58, 'RESOLVED',   'sos_token_007', NOW() - INTERVAL '34 days', '10000000-0000-4000-8000-000000000011', NOW() - INTERVAL '35 days'),
('ab000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', ST_SetSRID(ST_MakePoint(108.25, 16.07), 4326)::geography, 16.07, 108.25, 'ACTIVE',     'sos_token_008', NULL, NULL, NOW() - INTERVAL '30 minutes');

-- -----------------------------------------------------------------------------
-- 43. SOS DISPATCH LOG (12)
-- -----------------------------------------------------------------------------
INSERT INTO sos_dispatch_log (id, sos_id, channel, target, status, provider_id, error, sent_at) VALUES
('ac000000-0000-4000-8000-000000000001', 'ab000000-0000-4000-8000-000000000001', 'SMS',      '+84901111001', 'DELIVERED', 'twilio_msg_001', NULL, NOW() - INTERVAL '20 days'),
('ac000000-0000-4000-8000-000000000002', 'ab000000-0000-4000-8000-000000000001', 'PUSH',     'device_004',   'DELIVERED', 'fcm_001', NULL, NOW() - INTERVAL '20 days'),
('ac000000-0000-4000-8000-000000000003', 'ab000000-0000-4000-8000-000000000002', 'EMAIL',    'khoa.bui@example.com', 'DELIVERED', 'resend_001', NULL, NOW() - INTERVAL '6 days'),
('ac000000-0000-4000-8000-000000000004', 'ab000000-0000-4000-8000-000000000003', 'SMS',      '+84901111001', 'DELIVERED', 'twilio_msg_002', NULL, NOW() - INTERVAL '51 days'),
('ac000000-0000-4000-8000-000000000005', 'ab000000-0000-4000-8000-000000000004', 'PUSH',     'device_008',   'PENDING',   NULL, NULL, NOW() - INTERVAL '1 hour'),
('ac000000-0000-4000-8000-000000000006', 'ab000000-0000-4000-8000-000000000004', 'REALTIME', 'ws_channel_008','SENT',      NULL, NULL, NOW() - INTERVAL '59 minutes'),
('ac000000-0000-4000-8000-000000000007', 'ab000000-0000-4000-8000-000000000005', 'SMS',      '+84905555001', 'DELIVERED', 'twilio_msg_003', NULL, NOW() - INTERVAL '17 days'),
('ac000000-0000-4000-8000-000000000008', 'ab000000-0000-4000-8000-000000000008', 'PUSH',     'device_002',   'PENDING',   NULL, NULL, NOW() - INTERVAL '25 minutes'),
('ac000000-0000-4000-8000-000000000009', 'ab000000-0000-4000-8000-000000000008', 'SMS',      '+84902222001', 'FAILED',    'twilio_msg_004', 'Carrier timeout', NOW() - INTERVAL '20 minutes'),
('ac000000-0000-4000-8000-000000000010', 'ab000000-0000-4000-8000-000000000006', 'PUSH',     'device_010',   'DELIVERED', 'fcm_002', NULL, NOW() - INTERVAL '11 days'),
('ac000000-0000-4000-8000-000000000011', 'ab000000-0000-4000-8000-000000000007', 'EMAIL',    'admin@gola.app', 'DELIVERED', 'resend_002', NULL, NOW() - INTERVAL '35 days'),
('ac000000-0000-4000-8000-000000000012', 'ab000000-0000-4000-8000-000000000003', 'REALTIME', 'mod_channel',  'DELIVERED', NULL, NULL, NOW() - INTERVAL '51 days');

-- -----------------------------------------------------------------------------
-- 44. INCIDENTS (10)
-- -----------------------------------------------------------------------------
INSERT INTO incidents (id, user_id, trip_id, type, description, geom, latitude, longitude, status, media_urls, created_at) VALUES
('ad000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000010', 'BREAKDOWN', 'Motorbike flat tire on mountain road', ST_SetSRID(ST_MakePoint(108.44, 11.94), 4326)::geography, 11.94, 108.44, 'OPEN',     ARRAY['https://cdn.gola.app/incidents/01.jpg'], NOW() - INTERVAL '2 days'),
('ad000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000007', 'WEATHER',   'Sudden rain flooded trail', ST_SetSRID(ST_MakePoint(107.58, 16.47), 4326)::geography, 16.47, 107.58, 'RESOLVED', '{}', NOW() - INTERVAL '30 days'),
('ad000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', NULL, 'THEFT',     'Wallet stolen at beach', ST_SetSRID(ST_MakePoint(109.20, 12.24), 4326)::geography, 12.24, 109.20, 'OPEN', ARRAY['https://cdn.gola.app/incidents/03.jpg'], NOW() - INTERVAL '5 days'),
('ad000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', 'OTHER',     'Lost group member at market', ST_SetSRID(ST_MakePoint(106.70, 10.77), 4326)::geography, 10.77, 106.70, 'RESOLVED', '{}', NOW() - INTERVAL '55 days'),
('ad000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000006', NULL, 'INJURY',    'Sprained ankle on trail', ST_SetSRID(ST_MakePoint(103.84, 22.34), 4326)::geography, 22.34, 103.84, 'OPEN', '{}', NOW() - INTERVAL '1 day'),
('ad000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000005', 'WEATHER',   'Boat tour cancelled due to storm', ST_SetSRID(ST_MakePoint(105.79, 10.05), 4326)::geography, 10.05, 105.79, 'RESOLVED', '{}', NOW() - INTERVAL '18 days'),
('ad000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000008', 'OTHER',     'Laptop stolen from cafe', ST_SetSRID(ST_MakePoint(106.70, 10.78), 4326)::geography, 10.78, 106.70, 'OPEN', '{}', NOW() - INTERVAL '3 days'),
('ad000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000002', 'BREAKDOWN', 'Car engine overheated', ST_SetSRID(ST_MakePoint(108.25, 16.07), 4326)::geography, 16.07, 108.25, 'OPEN', '{}', NOW() - INTERVAL '6 hours'),
('ad000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', NULL, 'OTHER',     'Missed bus connection', ST_SetSRID(ST_MakePoint(108.33, 15.88), 4326)::geography, 15.88, 108.33, 'RESOLVED', '{}', NOW() - INTERVAL '40 days'),
('ad000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000015', 'WEATHER',   'Heavy rain at Cu Chi', ST_SetSRID(ST_MakePoint(106.46, 11.15), 4326)::geography, 11.15, 106.46, 'OPEN', '{}', NOW() - INTERVAL '8 hours');

-- -----------------------------------------------------------------------------
-- 45. SAFETY REPORTS (8)
-- -----------------------------------------------------------------------------
INSERT INTO safety_reports (id, reporter_id, target_type, target_id, reason, detail, status, resolved_by, resolved_at, created_at) VALUES
('ae000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000012', 'POST',    '96000000-0000-4000-8000-000000000015', 'SPAM',        'Promotional spam content', 'RESOLVED', '10000000-0000-4000-8000-000000000012', NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 days'),
('ae000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000008', 'USER',    '10000000-0000-4000-8000-000000000014', 'HARASSMENT',  'Repeated unwanted messages', 'OPEN', NULL, NULL, NOW() - INTERVAL '3 days'),
('ae000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'COMMENT', '97000000-0000-4000-8000-000000000005', 'INAPPROPRIATE','Offensive language', 'RESOLVED', '10000000-0000-4000-8000-000000000012', NOW() - INTERVAL '40 days', NOW() - INTERVAL '41 days'),
('ae000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'POST',    '96000000-0000-4000-8000-000000000006', 'MISINFORMATION','False safety claim', 'OPEN', NULL, NULL, NOW() - INTERVAL '5 days'),
('ae000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000011', 'USER',    '10000000-0000-4000-8000-000000000014', 'FAKE_ACCOUNT','Suspected bot account', 'RESOLVED', '10000000-0000-4000-8000-000000000011', NOW() - INTERVAL '2 days', NOW() - INTERVAL '3 days'),
('ae000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000007', 'POST',    '96000000-0000-4000-8000-000000000004', 'COPYRIGHT',   'Unauthorized photo', 'OPEN', NULL, NULL, NOW() - INTERVAL '40 days'),
('ae000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', 'COMMENT', '97000000-0000-4000-8000-000000000008', 'SPAM',        'Link spam', 'RESOLVED', '10000000-0000-4000-8000-000000000012', NOW() - INTERVAL '80 days', NOW() - INTERVAL '82 days'),
('ae000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000003', 'USER',    '10000000-0000-4000-8000-000000000013', 'OTHER',       'Suspicious activity', 'OPEN', NULL, NULL, NOW() - INTERVAL '4 days');

-- -----------------------------------------------------------------------------
-- 46. REPORTS (V15 table, 8 rows)
-- -----------------------------------------------------------------------------
INSERT INTO reports (id, reporter_id, target_type, target_id, reason, status, created_at, updated_at) VALUES
('af000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'POST',    '96000000-0000-4000-8000-000000000008', 'Inappropriate content', 'OPEN',     NOW() - INTERVAL '20 days', NOW()),
('af000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000012', 'USER',    '10000000-0000-4000-8000-000000000014', 'Spam account',          'RESOLVED', NOW() - INTERVAL '3 days',  NOW()),
('af000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000007', 'COMMENT', '97000000-0000-4000-8000-000000000011', 'Harassment',            'OPEN',     NOW() - INTERVAL '18 days', NOW()),
('af000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'TRIP',    '90000000-0000-4000-8000-000000000013', 'Misleading description','CLOSED',   NOW() - INTERVAL '30 days', NOW()),
('af000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000008', 'POST',    '96000000-0000-4000-8000-000000000002', 'Copyright violation',   'OPEN',     NOW() - INTERVAL '35 days', NOW()),
('af000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000005', 'USER',    '10000000-0000-4000-8000-000000000013', 'Fake profile',          'OPEN',     NOW() - INTERVAL '7 days',  NOW()),
('af000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000009', 'PLACE',   '40000000-0000-4000-8000-000000000008', 'Incorrect location',    'RESOLVED', NOW() - INTERVAL '25 days', NOW()),
('af000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000002', 'QUEST',   '70000000-0000-4000-8000-000000000005', 'Impossible requirements', 'OPEN',   NOW() - INTERVAL '12 days', NOW());

-- -----------------------------------------------------------------------------
-- 47. NOTIFICATIONS (20)
-- -----------------------------------------------------------------------------
INSERT INTO notifications (id, user_id, type, title, body, payload, read_at, created_at) VALUES
('b0000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'SOCIAL',  'New follower', 'Minh Tran started following you', '{"followerId":"10000000-0000-4000-8000-000000000002"}', NOW() - INTERVAL '67 days', NOW() - INTERVAL '68 days'),
('b0000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'TRIP',    'Trip invite', 'You were invited to Central Vietnam Coast', '{"tripId":"90000000-0000-4000-8000-000000000002"}', NULL, NOW() - INTERVAL '40 days'),
('b0000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 'QUEST',   'Quest completed', 'You completed Ben Thanh Explorer', '{"questId":"70000000-0000-4000-8000-000000000001"}', NOW() - INTERVAL '54 days', NOW() - INTERVAL '55 days'),
('b0000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'SOS',     'SOS resolved', 'Your SOS event has been resolved', '{"sosId":"ab000000-0000-4000-8000-000000000001"}', NOW() - INTERVAL '19 days', NOW() - INTERVAL '20 days'),
('b0000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000008', 'PAYMENT', 'Subscription active', 'Gola Plus is now active', '{"product":"Gola Plus"}', NOW() - INTERVAL '29 days', NOW() - INTERVAL '30 days'),
('b0000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', 'SYSTEM',  'Welcome to Gola', 'Start planning your first trip!', '{}', NOW() - INTERVAL '89 days', NOW() - INTERVAL '90 days'),
('b0000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 'TRIP',    'Trip reminder', 'Hanoi Winter Escape starts in 30 days', '{"tripId":"90000000-0000-4000-8000-000000000003"}', NULL, NOW() - INTERVAL '5 days'),
('b0000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000009', 'SOCIAL',  'New comment', 'Thu commented on your post', '{"commentId":"97000000-0000-4000-8000-000000000008"}', NOW() - INTERVAL '85 days', NOW() - INTERVAL '86 days'),
('b0000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000010', 'QUEST',   'Quest failed', 'Mekong River Cruise quest failed', '{"questId":"70000000-0000-4000-8000-000000000010"}', NULL, NOW() - INTERVAL '18 days'),
('b0000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000005', 'TRIP',    'Member joined', 'Linh joined your Mekong trip', '{"userId":"10000000-0000-4000-8000-000000000001"}', NOW() - INTERVAL '23 days', NOW() - INTERVAL '24 days'),
('b0000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000013', 'SYSTEM',  'Welcome', 'Complete your profile to get started', '{}', NULL, NOW() - INTERVAL '9 days'),
('b0000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000002', 'SOCIAL',  'New reaction', 'David loved your beach post', '{"postId":"96000000-0000-4000-8000-000000000002"}', NULL, NOW() - INTERVAL '36 days'),
('b0000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000006', 'SOS',     'SOS drill', 'Your SOS drill was marked false alarm', '{}', NOW() - INTERVAL '5 days', NOW() - INTERVAL '6 days'),
('b0000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000007', 'PAYMENT', 'Coins earned', 'You earned 100 Gola coins', '{"amount":100}', NOW() - INTERVAL '10 days', NOW() - INTERVAL '11 days'),
('b0000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000001', 'TRIP',    'Live session', 'Phu Quoc trip session is active', '{"sessionId":"94000000-0000-4000-8000-000000000006"}', NULL, NOW() - INTERVAL '3 hours'),
('b0000000-0000-4000-8000-000000000016', '10000000-0000-4000-8000-000000000008', 'SOS',     'Active SOS nearby', 'SOS event reported near you', '{}', NULL, NOW() - INTERVAL '1 hour'),
('b0000000-0000-4000-8000-000000000017', '10000000-0000-4000-8000-000000000004', 'SYSTEM',  'Reward available', 'New museum pass reward available', '{}', NULL, NOW() - INTERVAL '8 days'),
('b0000000-0000-4000-8000-000000000018', '10000000-0000-4000-8000-000000000011', 'SYSTEM',  'Admin notice', 'Platform maintenance tonight', '{}', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '3 hours'),
('b0000000-0000-4000-8000-000000000019', '10000000-0000-4000-8000-000000000014', 'SOCIAL',  'Welcome', 'Linh Nguyen welcomed you', '{}', NULL, NOW() - INTERVAL '2 days'),
('b0000000-0000-4000-8000-000000000020', '10000000-0000-4000-8000-000000000015', 'PAYMENT', 'Partner payout', 'Monthly partner summary ready', '{}', NULL, NOW() - INTERVAL '1 day');

-- -----------------------------------------------------------------------------
-- 48. DEVICE TOKENS (12)
-- -----------------------------------------------------------------------------
INSERT INTO device_tokens (id, user_id, platform, token, last_seen, last_used_at, created_at, updated_at) VALUES
('b1000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'IOS',     'fcm_token_linh_ios_001',     NOW() - INTERVAL '1 hour',  NOW() - INTERVAL '2 hours', NOW() - INTERVAL '80 days', NOW()),
('b1000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'ANDROID', 'fcm_token_minh_android_001', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '70 days', NOW()),
('b1000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000008', 'WEB',     'fcm_token_alex_web_001',     NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '35 days', NOW()),
('b1000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'IOS',     'fcm_token_david_ios_001',    NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days',  NOW() - INTERVAL '55 days', NOW()),
('b1000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000003', 'IOS',     'fcm_token_hana_ios_001',     NOW() - INTERVAL '1 day',   NOW() - INTERVAL '1 day',   NOW() - INTERVAL '65 days', NOW()),
('b1000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000009', 'ANDROID', 'fcm_token_yen_android_001',  NOW() - INTERVAL '5 days',  NOW() - INTERVAL '5 days',  NOW() - INTERVAL '30 days', NOW()),
('b1000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000010', 'ANDROID', 'fcm_token_tuan_android_001', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '7 hours', NOW() - INTERVAL '25 days', NOW()),
('b1000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000005', 'IOS',     'fcm_token_mai_ios_001',      NOW() - INTERVAL '12 hours',NOW() - INTERVAL '13 hours',NOW() - INTERVAL '50 days', NOW()),
('b1000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000006', 'ANDROID', 'fcm_token_khoa_android_001', NOW() - INTERVAL '2 days',  NOW() - INTERVAL '2 days',  NOW() - INTERVAL '45 days', NOW()),
('b1000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000007', 'IOS',     'fcm_token_thu_ios_001',      NOW() - INTERVAL '3 days',  NOW() - INTERVAL '3 days',  NOW() - INTERVAL '40 days', NOW()),
('b1000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000013', 'WEB',     'fcm_token_guest1_web_001',   NOW() - INTERVAL '1 day',   NOW() - INTERVAL '1 day',   NOW() - INTERVAL '8 days',  NOW()),
('b1000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000011', 'WEB',     'fcm_token_admin_web_001',    NOW() - INTERVAL '1 hour',  NOW() - INTERVAL '1 hour',  NOW() - INTERVAL '300 days',NOW());

-- -----------------------------------------------------------------------------
-- 49. NOTIFICATION PREFERENCES (20) — V15: id PK; unique (user_id, type) per JPA
-- -----------------------------------------------------------------------------
INSERT INTO notification_preferences (id, user_id, channel, type, enabled, is_enabled, created_at, updated_at) VALUES
('b2000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'PUSH', 'TRIP',    true, true, NOW() - INTERVAL '85 days', NOW()),
('b2000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'EMAIL','SOCIAL',  true, true, NOW() - INTERVAL '85 days', NOW()),
('b2000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'PUSH', 'SOS',     true, true, NOW() - INTERVAL '85 days', NOW()),
('b2000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'PUSH', 'TRIP',    true, true, NOW() - INTERVAL '75 days', NOW()),
('b2000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 'PUSH', 'QUEST',   true, true, NOW() - INTERVAL '75 days', NOW()),
('b2000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000003', 'EMAIL','TRIP',    true, true, NOW() - INTERVAL '65 days', NOW()),
('b2000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000004', 'PUSH', 'SOS',     true, true, NOW() - INTERVAL '55 days', NOW()),
('b2000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'EMAIL','PAYMENT', true, true, NOW() - INTERVAL '35 days', NOW()),
('b2000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000008', 'PUSH', 'SYSTEM',  true, true, NOW() - INTERVAL '35 days', NOW()),
('b2000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000009', 'PUSH', 'SOCIAL',  true, true, NOW() - INTERVAL '30 days', NOW()),
('b2000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000005', 'PUSH', 'TRIP',    true, true, NOW() - INTERVAL '50 days', NOW()),
('b2000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000006', 'PUSH', 'SOS',     true, true, NOW() - INTERVAL '45 days', NOW()),
('b2000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000007', 'EMAIL','QUEST',   false,false,NOW() - INTERVAL '40 days', NOW()),
('b2000000-0000-4000-8000-000000000014', '10000000-0000-4000-8000-000000000010', 'PUSH', 'TRIP',    true, true, NOW() - INTERVAL '25 days', NOW()),
('b2000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000011', 'EMAIL','SYSTEM',  true, true, NOW() - INTERVAL '360 days',NOW()),
('b2000000-0000-4000-8000-000000000016', '10000000-0000-4000-8000-000000000012', 'PUSH', 'SYSTEM',  true, true, NOW() - INTERVAL '195 days',NOW()),
('b2000000-0000-4000-8000-000000000017', '10000000-0000-4000-8000-000000000013', 'PUSH', 'SOCIAL',  true, true, NOW() - INTERVAL '8 days',  NOW()),
('b2000000-0000-4000-8000-000000000018', '10000000-0000-4000-8000-000000000014', 'PUSH', 'TRIP',    true, true, NOW() - INTERVAL '4 days',  NOW()),
('b2000000-0000-4000-8000-000000000019', '10000000-0000-4000-8000-000000000015', 'EMAIL','PAYMENT', true, true, NOW() - INTERVAL '95 days', NOW()),
('b2000000-0000-4000-8000-000000000020', '10000000-0000-4000-8000-000000000001', 'IN_APP','PAYMENT',true, true, NOW() - INTERVAL '85 days', NOW());

-- -----------------------------------------------------------------------------
-- 50. SUBSCRIPTIONS (8)
-- -----------------------------------------------------------------------------
INSERT INTO subscriptions (id, user_id, stripe_subscription_id, stripe_customer_id, product_id, price_id, status, current_period_start, current_period_end, created_at, updated_at) VALUES
('b3000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000008', 'sub_alex_plus_001', 'cus_alex_001', '60000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000001', 'ACTIVE',    NOW() - INTERVAL '30 days', NOW() + INTERVAL '30 days', NOW() - INTERVAL '30 days', NOW()),
('b3000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'sub_david_plus_001','cus_david_001','60000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000002', 'ACTIVE',    NOW() - INTERVAL '200 days',NOW() + INTERVAL '165 days',NOW() - INTERVAL '200 days',NOW()),
('b3000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'sub_linh_cancel_01','cus_linh_001', '60000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000001', 'CANCELLED', NOW() - INTERVAL '90 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '90 days', NOW()),
('b3000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000005', 'sub_mai_family_01', 'cus_mai_001',  '60000000-0000-4000-8000-000000000003', '61000000-0000-4000-8000-000000000003', 'ACTIVE',    NOW() - INTERVAL '15 days', NOW() + INTERVAL '15 days', NOW() - INTERVAL '15 days', NOW()),
('b3000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 'sub_minh_past_01',  'cus_minh_001', '60000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000001', 'PAST_DUE',  NOW() - INTERVAL '35 days', NOW() - INTERVAL '5 days',  NOW() - INTERVAL '35 days', NOW()),
('b3000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000015', 'sub_partner_001',   'cus_partner_01','60000000-0000-4000-8000-000000000004', '61000000-0000-4000-8000-000000000004', 'ACTIVE',    NOW() - INTERVAL '365 days',NOW() + INTERVAL '30 days', NOW() - INTERVAL '365 days',NOW()),
('b3000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000013', 'sub_guest_incomp',  'cus_guest_01', '60000000-0000-4000-8000-000000000002', '61000000-0000-4000-8000-000000000001', 'INCOMPLETE',NULL, NULL, NOW() - INTERVAL '8 days', NOW()),
('b3000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000009', 'sub_yen_annual_01', 'cus_yen_001',  '60000000-0000-4000-8000-000000000004', '61000000-0000-4000-8000-000000000004', 'ACTIVE',    NOW() - INTERVAL '60 days', NOW() + INTERVAL '305 days',NOW() - INTERVAL '60 days', NOW());

-- -----------------------------------------------------------------------------
-- 51. ORDERS (10)
-- -----------------------------------------------------------------------------
INSERT INTO orders (id, user_id, stripe_session_id, stripe_payment_intent, amount, currency, status, price_id, created_at, updated_at) VALUES
('b4000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000008', 'cs_alex_001', 'pi_alex_001', 99000,  'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000001', NOW() - INTERVAL '30 days', NOW()),
('b4000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'cs_linh_coins', 'pi_linh_001', 49000,  'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000005', NOW() - INTERVAL '20 days', NOW()),
('b4000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'cs_david_001', 'pi_david_001', 990000, 'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000002', NOW() - INTERVAL '200 days',NOW()),
('b4000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', 'cs_minh_pending','pi_minh_001', 99000,  'vnd', 'PENDING',   '61000000-0000-4000-8000-000000000001', NOW() - INTERVAL '2 days',  NOW()),
('b4000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'cs_mai_001',  'pi_mai_001',  149000, 'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000003', NOW() - INTERVAL '15 days', NOW()),
('b4000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000010', 'cs_tuan_fail','pi_tuan_001', 49000,  'vnd', 'FAILED',    '61000000-0000-4000-8000-000000000005', NOW() - INTERVAL '5 days',  NOW()),
('b4000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 'cs_hana_001', 'pi_hana_001', 89000,  'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000006', NOW() - INTERVAL '10 days', NOW()),
('b4000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', 'cs_linh_refund','pi_linh_002',99000,  'vnd', 'REFUNDED',  '61000000-0000-4000-8000-000000000001', NOW() - INTERVAL '90 days', NOW()),
('b4000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000009', 'cs_yen_001',  'pi_yen_001',  890000, 'vnd', 'SUCCEEDED', '61000000-0000-4000-8000-000000000004', NOW() - INTERVAL '60 days', NOW()),
('b4000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000013', 'cs_guest_001','pi_guest_001',49000,  'vnd', 'CANCELLED', '61000000-0000-4000-8000-000000000005', NOW() - INTERVAL '7 days',  NOW());

-- -----------------------------------------------------------------------------
-- 52. PAYMENT EVENTS (8)
-- -----------------------------------------------------------------------------
INSERT INTO payment_events (id, provider_event_id, type, payload, processed_at) VALUES
('b5000000-0000-4000-8000-000000000001', 'evt_stripe_001', 'checkout.session.completed', '{"session":"cs_alex_001"}', NOW() - INTERVAL '30 days'),
('b5000000-0000-4000-8000-000000000002', 'evt_stripe_002', 'invoice.paid',               '{"subscription":"sub_alex_plus_001"}', NOW() - INTERVAL '30 days'),
('b5000000-0000-4000-8000-000000000003', 'evt_stripe_003', 'payment_intent.succeeded',   '{"pi":"pi_linh_001"}', NOW() - INTERVAL '20 days'),
('b5000000-0000-4000-8000-000000000004', 'evt_stripe_004', 'customer.subscription.deleted','{"sub":"sub_linh_cancel_01"}', NOW() - INTERVAL '60 days'),
('b5000000-0000-4000-8000-000000000005', 'evt_stripe_005', 'payment_intent.failed',      '{"pi":"pi_tuan_001"}', NOW() - INTERVAL '5 days'),
('b5000000-0000-4000-8000-000000000006', 'evt_stripe_006', 'invoice.payment_failed',     '{"sub":"sub_minh_past_01"}', NOW() - INTERVAL '5 days'),
('b5000000-0000-4000-8000-000000000007', 'evt_stripe_007', 'charge.refunded',            '{"pi":"pi_linh_002"}', NOW() - INTERVAL '85 days'),
('b5000000-0000-4000-8000-000000000008', 'evt_stripe_008', 'checkout.session.completed', '{"session":"cs_mai_001"}', NOW() - INTERVAL '15 days');

-- -----------------------------------------------------------------------------
-- 53. AI JOBS (12)
-- -----------------------------------------------------------------------------
INSERT INTO ai_jobs (id, user_id, kind, status, input, output, tokens_in, tokens_out, cost_usd, created_at, completed_at) VALUES
('b6000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'TRIP_GENERATE',   'DONE',   '{"destination":"Phu Quoc"}', '{"days":5}', 1200, 800, 0.012000, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days' + INTERVAL '30 seconds'),
('b6000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000008', 'TRIP_GENERATE',   'DONE',   '{"destination":"HCMC"}', '{"days":30}', 2500, 1800, 0.028000, NOW() - INTERVAL '35 days', NOW() - INTERVAL '35 days' + INTERVAL '1 minute'),
('b6000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 'ALBUM_GENERATE',  'DONE',   '{"albumId":"99000000-0000-4000-8000-000000000002"}', '{"curated":4}', 800, 400, 0.006000, NOW() - INTERVAL '37 days', NOW() - INTERVAL '37 days' + INTERVAL '45 seconds'),
('b6000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000007', 'IMAGE_CLASSIFY',  'DONE',   '{"mediaId":"95000000-0000-4000-8000-000000000007"}', '{"label":"historic_gate"}', 200, 50, 0.001000, NOW() - INTERVAL '38 days', NOW() - INTERVAL '38 days' + INTERVAL '10 seconds'),
('b6000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000012', 'MODERATION',      'DONE',   '{"postId":"96000000-0000-4000-8000-000000000015"}', '{"safe":true}', 150, 30, 0.000500, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '5 seconds'),
('b6000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000003', 'TRIP_GENERATE',   'FAILED', '{"destination":"Sapa"}', NULL, 500, 0, 0.002000, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days' + INTERVAL '20 seconds'),
('b6000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000004', 'IMAGE_CLASSIFY',  'RUNNING','{"mediaId":"95000000-0000-4000-8000-000000000004"}', NULL, NULL, NULL, NULL, NOW() - INTERVAL '1 minute', NULL),
('b6000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000010', 'TRIP_GENERATE',   'QUEUED', '{"destination":"Dalat"}', NULL, NULL, NULL, NULL, NOW() - INTERVAL '30 seconds', NULL),
('b6000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000001', 'ALBUM_GENERATE',  'DONE',   '{"albumId":"99000000-0000-4000-8000-000000000006"}', '{"curated":2}', 600, 300, 0.004500, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days' + INTERVAL '40 seconds'),
('b6000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000009', 'IMAGE_CLASSIFY',  'DONE',   '{"mediaId":"95000000-0000-4000-8000-000000000009"}', '{"label":"lanterns"}', 180, 40, 0.000800, NOW() - INTERVAL '88 days', NOW() - INTERVAL '88 days' + INTERVAL '8 seconds'),
('b6000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000005', 'MODERATION',      'DONE',   '{"commentId":"97000000-0000-4000-8000-000000000006"}', '{"safe":true}', 100, 20, 0.000300, NOW() - INTERVAL '17 days', NOW() - INTERVAL '17 days' + INTERVAL '3 seconds'),
('b6000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-000000000008', 'MODERATION',      'QUEUED', '{"postId":"96000000-0000-4000-8000-000000000008"}', NULL, NULL, NULL, NULL, NOW() - INTERVAL '10 seconds', NULL);

-- -----------------------------------------------------------------------------
-- 54. AI QUOTAS (15)
-- -----------------------------------------------------------------------------
INSERT INTO ai_quotas (user_id, kind, period_start, count) VALUES
('10000000-0000-4000-8000-000000000001', 'TRIP_GENERATE',   DATE_TRUNC('month', CURRENT_DATE)::date, 3),
('10000000-0000-4000-8000-000000000001', 'ALBUM_GENERATE',  DATE_TRUNC('month', CURRENT_DATE)::date, 2),
('10000000-0000-4000-8000-000000000008', 'TRIP_GENERATE',   DATE_TRUNC('month', CURRENT_DATE)::date, 8),
('10000000-0000-4000-8000-000000000008', 'IMAGE_CLASSIFY',  DATE_TRUNC('month', CURRENT_DATE)::date, 15),
('10000000-0000-4000-8000-000000000002', 'ALBUM_GENERATE',  DATE_TRUNC('month', CURRENT_DATE)::date, 1),
('10000000-0000-4000-8000-000000000007', 'IMAGE_CLASSIFY',  DATE_TRUNC('month', CURRENT_DATE)::date, 2),
('10000000-0000-4000-8000-000000000012', 'MODERATION',      DATE_TRUNC('month', CURRENT_DATE)::date, 50),
('10000000-0000-4000-8000-000000000003', 'TRIP_GENERATE',   DATE_TRUNC('month', CURRENT_DATE)::date, 1),
('10000000-0000-4000-8000-000000000004', 'IMAGE_CLASSIFY',  DATE_TRUNC('month', CURRENT_DATE)::date, 5),
('10000000-0000-4000-8000-000000000009', 'IMAGE_CLASSIFY',  DATE_TRUNC('month', CURRENT_DATE)::date, 1),
('10000000-0000-4000-8000-000000000005', 'MODERATION',      DATE_TRUNC('month', CURRENT_DATE)::date, 3),
('10000000-0000-4000-8000-000000000010', 'TRIP_GENERATE',   DATE_TRUNC('month', CURRENT_DATE)::date, 0),
('10000000-0000-4000-8000-000000000001', 'MODERATION',      DATE_TRUNC('month', CURRENT_DATE)::date, 0),
('10000000-0000-4000-8000-000000000008', 'MODERATION',      DATE_TRUNC('month', CURRENT_DATE)::date, 2),
('10000000-0000-4000-8000-000000000002', 'TRIP_GENERATE',   DATE_TRUNC('month', CURRENT_DATE)::date, 0);

-- -----------------------------------------------------------------------------
-- 55. AI QUOTA LIMITS (6) — role_id references user_roles.id
-- -----------------------------------------------------------------------------
INSERT INTO ai_quota_limits (id, role_id, kind, max_uses, created_at, updated_at) VALUES
('b7000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000010', 'TRIP_GENERATION',  100, NOW() - INTERVAL '365 days', NOW()),
('b7000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000010', 'CAPTION',          500, NOW() - INTERVAL '365 days', NOW()),
('b7000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000011', 'TRIP_GENERATION',   50, NOW() - INTERVAL '200 days', NOW()),
('b7000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001', 'TRIP_GENERATION',    5, NOW() - INTERVAL '90 days', NOW()),
('b7000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', 'CAPTION',           10, NOW() - INTERVAL '90 days', NOW()),
('b7000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000007', 'RECOMMENDATION',    20, NOW() - INTERVAL '40 days', NOW());

-- -----------------------------------------------------------------------------
-- 56. EXPENSES (12)
-- -----------------------------------------------------------------------------
INSERT INTO expenses (id, trip_id, payer_id, amount, currency, description, created_at, updated_at) VALUES
('b8000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 250000, 'VND', 'Street food tour', NOW() - INTERVAL '57 days', NOW()),
('b8000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 180000, 'VND', 'Pizza dinner', NOW() - INTERVAL '56 days', NOW()),
('b8000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 850000, 'VND', 'Hotel 2 nights', NOW() - INTERVAL '40 days', NOW()),
('b8000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 120.00, 'USD', 'Diving equipment rental', NOW() - INTERVAL '47 days', NOW()),
('b8000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 450000, 'VND', 'Boat tour family ticket', NOW() - INTERVAL '19 days', NOW()),
('b8000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000007', 200000, 'VND', 'Citadel entrance', NOW() - INTERVAL '37 days', NOW()),
('b8000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 3500000,'VND', 'Phu Quoc resort', NOW() - INTERVAL '4 days', NOW()),
('b8000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 500000, 'VND', 'Airport transfer', NOW() - INTERVAL '3 days', NOW()),
('b8000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000013', '10000000-0000-4000-8000-000000000004', 350000, 'VND', 'Bus to Vung Tau', NOW() - INTERVAL '33 days', NOW()),
('b8000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000003', 300000, 'VND', 'Cu Chi tour tickets', NOW() - INTERVAL '10 days', NOW()),
('b8000000-0000-4000-8000-000000000011', '90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000001', 150000, 'VND', 'Lunch at market', NOW() - INTERVAL '9 days', NOW()),
('b8000000-0000-4000-8000-000000000012', '90000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 75000,  'VND', 'Coworking day pass', NOW() - INTERVAL '20 days', NOW());

-- -----------------------------------------------------------------------------
-- 57. TRIP NOTES (10)
-- -----------------------------------------------------------------------------
INSERT INTO trip_notes (id, trip_id, author_id, content, created_at, updated_at) VALUES
('b9000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Remember to bring cash for street vendors', NOW() - INTERVAL '58 days', NOW()),
('b9000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Stall 12 has the best banh mi', NOW() - INTERVAL '57 days', NOW()),
('b9000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Book Hoi An hotel early for weekend', NOW() - INTERVAL '42 days', NOW()),
('b9000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 'Dive shop opens at 7am', NOW() - INTERVAL '48 days', NOW()),
('b9000000-0000-4000-8000-000000000005', '90000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000005', 'Bring sunscreen for kids', NOW() - INTERVAL '22 days', NOW()),
('b9000000-0000-4000-8000-000000000006', '90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000001', 'Ferry at 8am from Rach Gia', NOW() - INTERVAL '8 days', NOW()),
('b9000000-0000-4000-8000-000000000007', '90000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000008', 'Resort check-in 2pm', NOW() - INTERVAL '7 days', NOW()),
('b9000000-0000-4000-8000-000000000008', '90000000-0000-4000-8000-000000000015', '10000000-0000-4000-8000-000000000003', 'Tunnel tour 2pm booking confirmed', NOW() - INTERVAL '12 days', NOW()),
('b9000000-0000-4000-8000-000000000009', '90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000006', 'Pack warm jacket for Sapa', NOW() - INTERVAL '28 days', NOW()),
('b9000000-0000-4000-8000-000000000010', '90000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000008', 'Monthly coworking pass at Cong Caphe area', NOW() - INTERVAL '30 days', NOW());
