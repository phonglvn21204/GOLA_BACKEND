-- =============================================================================
-- V1031__production_seed.sql
-- Description: Xóa dữ liệu rác từ V999 (nếu có) và seed dữ liệu demo Tiếng Việt chuẩn.
-- =============================================================================

-- 1. XÓA DỮ LIỆU RÁC TỪ V999 (Đảm bảo sạch sẽ)
TRUNCATE TABLE 
    password_reset_audit, audit_logs,
    device_tokens, refresh_tokens,
    comments, post_hashtags, reactions, trip_stories, album_media, albums, posts,
    trip_members, trip_stops, trip_shares, trip_invitations, trip_notes, expenses,
    sos_events, live_locations,
    trips,
    user_badges, quest_progress, quest_tasks, redemptions,
    notification_preferences, notifications,
    follows, user_blocks,
    user_pref_travel_styles, user_pref_interests, user_preferences, wallets,
    subscriptions, orders,
    user_roles,
    place_favorites, reviews, places,
    profiles
CASCADE;


-- 2. TẠO USERS
-- Admin
INSERT INTO profiles (id, email, password_hash, display_name, avatar_url, bio, locale, theme, is_public, onboarded_at, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000001', 'admin@gola.vn', '$2a$10$hkrJ92x.F824KZtDvS9xEu1p4ONfD3zDteFWJ26ZD4dimtXlY97vK', 'Quản trị viên GOLA', 'https://images.unsplash.com/photo-1560250097-0b93528c311a?w=400&q=80', 'Ban quản trị nền tảng GOLA.', 'vi', 'dark', TRUE, NOW(), NOW(), NOW(), NOW());
INSERT INTO user_roles (user_id, role) VALUES ('10000000-0000-4000-8000-000000000001', 'ADMIN');

-- Demo User 1: Anh Nguyen (Basic)
INSERT INTO profiles (id, email, password_hash, display_name, avatar_url, bio, locale, theme, is_public, onboarded_at, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000002', 'anh.nguyen@example.com', '$2a$10$0MVgrb0KvPf07q3tLxjFqePSXcPKr5npdaxypMEKmscZgpTmdcM4a', 'Nguyễn Minh Anh', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400&q=80', 'Đam mê khám phá tự nhiên và du lịch một mình.', 'vi', 'light', TRUE, NOW(), NOW(), NOW(), NOW());
INSERT INTO user_roles (user_id, role) VALUES ('10000000-0000-4000-8000-000000000002', 'USER');

-- Demo User 2: Huy Tran (Free)
INSERT INTO profiles (id, email, password_hash, display_name, avatar_url, bio, locale, theme, is_public, onboarded_at, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000003', 'huy.tran@example.com', '$2a$10$0PUnimlM2GXI4NW1.cUwwu1y.GOHT7bV/FxYFQIhzXhZlWwh4fcFu', 'Trần Quốc Huy', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&q=80', 'Thích phượt xe máy, cắm trại cuối tuần.', 'vi', 'dark', TRUE, NOW(), NOW(), NOW(), NOW());
INSERT INTO user_roles (user_id, role) VALUES ('10000000-0000-4000-8000-000000000003', 'USER');

-- Demo User 3: Lan Le (Group Pro)
INSERT INTO profiles (id, email, password_hash, display_name, avatar_url, bio, locale, theme, is_public, onboarded_at, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000004', 'lan.le@example.com', '$2a$10$JNb/JO2zdMih5CF2341HD./ZeobIREmPGyRhDWmSPh2OHwJXsqrAa', 'Lê Ngọc Lan', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&q=80', 'Du lịch gia đình, luôn lên kế hoạch chi tiết.', 'vi', 'light', TRUE, NOW(), NOW(), NOW(), NOW());
INSERT INTO user_roles (user_id, role) VALUES ('10000000-0000-4000-8000-000000000004', 'USER');

-- Wallets and Preferences
INSERT INTO wallets (user_id, gola_coins) VALUES
('10000000-0000-4000-8000-000000000001', 9999),
('10000000-0000-4000-8000-000000000002', 100),
('10000000-0000-4000-8000-000000000003', 50),
('10000000-0000-4000-8000-000000000004', 300);

-- Note: V15 added ID column to user_preferences
INSERT INTO user_preferences (id, user_id, budget_band) VALUES
(gen_random_uuid(), '10000000-0000-4000-8000-000000000002', 'MID'),
(gen_random_uuid(), '10000000-0000-4000-8000-000000000003', 'LOW'),
(gen_random_uuid(), '10000000-0000-4000-8000-000000000004', 'HIGH');


-- 3. SUBSCRIPTIONS
-- Assign Anh to Basic
INSERT INTO subscriptions (user_id, product_id, price_id, status, current_period_start, current_period_end)
VALUES (
  '10000000-0000-4000-8000-000000000002',
  '71000000-0000-4000-8000-000000000002',
  '72000000-0000-4000-8000-000000000002',
  'ACTIVE', NOW(), NOW() + INTERVAL '1 month'
);

-- Assign Lan to Group Pro
INSERT INTO subscriptions (user_id, product_id, price_id, status, current_period_start, current_period_end)
VALUES (
  '10000000-0000-4000-8000-000000000004',
  '71000000-0000-4000-8000-000000000003',
  '72000000-0000-4000-8000-000000000003',
  'ACTIVE', NOW(), NOW() + INTERVAL '1 month'
);


-- 4. DEMO TRIPS
-- Trip 1: Vũng Tàu (Huy Tran)
INSERT INTO trips (id, owner_id, title, origin, destination, start_date, end_date, status, is_public, cover_url, description)
VALUES ('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', 'Phượt Vũng Tàu Cuối Tuần', 'Hồ Chí Minh', 'Vũng Tàu', CURRENT_DATE + 2, CURRENT_DATE + 4, 'ACTIVE', TRUE, 'https://images.unsplash.com/photo-1583417657209-d3dd5cc606fb?w=800&q=80', 'Chuyến đi ngẫu hứng xả stress sau tuần làm việc. Tập trung vào hải sản và gió biển.');

-- Trip 2: Đà Lạt (Anh Nguyen)
INSERT INTO trips (id, owner_id, title, origin, destination, start_date, end_date, status, is_public, cover_url, description)
VALUES ('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'Đà Lạt Mộng Mơ 4N3Đ', 'Hồ Chí Minh', 'Đà Lạt', CURRENT_DATE + 10, CURRENT_DATE + 13, 'DRAFT', FALSE, 'https://images.unsplash.com/photo-1582555172866-f73bb12a2ab3?w=800&q=80', 'Chuyến đi săn mây và thưởng thức cà phê yên tĩnh.');

-- Trip 3: Phú Quốc (Lan Le)
INSERT INTO trips (id, owner_id, title, origin, destination, start_date, end_date, status, is_public, cover_url, description)
VALUES ('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'Nghỉ dưỡng gia đình Phú Quốc', 'Hà Nội', 'Phú Quốc', CURRENT_DATE + 30, CURRENT_DATE + 34, 'ACTIVE', TRUE, 'https://images.unsplash.com/photo-1602002418082-a4443e081dd1?w=800&q=80', 'Chuyến du lịch gia đình thư giãn tại resort Nam Đảo.');

-- Trip Members
INSERT INTO trip_members (trip_id, user_id, role) VALUES
('90000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', 'OWNER'),
('90000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000002', 'OWNER'),
('90000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'OWNER');


-- 5. COMMUNITY POSTS & COMMENTS
INSERT INTO posts (id, author_id, body, media_urls, trip_id)
VALUES ('96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', 'Cảnh bình minh ở Vũng Tàu lúc 5h30 sáng nay đẹp tuyệt vời! Ai đi biển mùa này nhớ canh giờ dậy sớm nhé 🌅', '{"https://images.unsplash.com/photo-1542152865-c32abccaece6?w=800&q=80"}', '90000000-0000-4000-8000-000000000001');

INSERT INTO posts (id, author_id, body, media_urls, trip_id)
VALUES ('96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 'Lịch trình Phú Quốc cho nhà có con nhỏ, mình mới soạn xong. GOLA gợi ý khá hay, tiết kiệm được nhiều thời gian xếp lịch.', '{}', '90000000-0000-4000-8000-000000000003');

INSERT INTO comments (post_id, author_id, body) VALUES
('96000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Tuyệt quá, cuối tuần này mình cũng định làm chuyến!'),
('96000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003', 'Nhà chị ở resort nào vậy ạ? Cho em xin review với.');


-- 6. REVIEWS
INSERT INTO places (id, name, address, geom, category, rating)
VALUES ('40000000-0000-4000-8000-000000000001', 'Bánh Khọt Gốc Vú Sữa', '14 Nguyễn Trường Tộ, Phường 2, Vũng Tàu', ST_SetSRID(ST_MakePoint(107.0811, 10.3456), 4326), 'FOOD', 4.5);

INSERT INTO reviews (id, user_id, place_id, rating, body)
VALUES (gen_random_uuid(), '10000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000001', 5, 'Bánh khọt rất giòn và ngon, tôm to tươi. Tuy nhiên cuối tuần khá đông phải đợi lâu.');


-- 7. QUESTS (Vũng Tàu)
INSERT INTO quests (title, description, destination, type, target_name, target_lat, target_lng, radius_m, reward_coins, status, is_active, is_featured)
VALUES (
    'Chinh phục Hải Đăng Vũng Tàu',
    'Leo núi và check-in tại ngọn hải đăng lâu đời nhất Việt Nam.',
    'Vũng Tàu', 'GPS_PHOTO', 'Hải Đăng Vũng Tàu', 10.3275, 107.0754, 200, 30, 'ACTIVE', TRUE, TRUE
);

INSERT INTO quests (title, description, destination, type, target_name, target_lat, target_lng, radius_m, reward_coins, status, is_active, is_featured)
VALUES (
    'Thưởng thức Hải sản Chợ Đêm',
    'Đến chợ đêm Vũng Tàu và chụp ảnh cùng món hải sản yêu thích của bạn.',
    'Vũng Tàu', 'FOOD', 'Chợ đêm Vũng Tàu', 10.3481, 107.0858, 500, 15, 'ACTIVE', TRUE, FALSE
);
