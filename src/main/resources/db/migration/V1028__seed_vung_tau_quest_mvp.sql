INSERT INTO quests (
    title, description, destination, type, target_name, target_lat, target_lng,
    radius_m, reward_coins, status, is_active, is_featured, created_at, updated_at
)
SELECT
    'Check-in Tượng Chúa Kitô Vua',
    'Chụp ảnh tại khu vực Tượng Chúa Kitô Vua.',
    'Vũng Tàu',
    'GPS_PHOTO'::quest_type,
    'Tượng Chúa Kitô Vua',
    10.323512,
    107.0847984,
    150,
    20,
    'ACTIVE',
    TRUE,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM quests WHERE title = 'Check-in Tượng Chúa Kitô Vua' AND destination = 'Vũng Tàu'
);

INSERT INTO quests (
    title, description, destination, type, target_name, target_lat, target_lng,
    radius_m, reward_coins, status, is_active, is_featured, created_at, updated_at
)
SELECT
    'Chụp ảnh tại Bãi Sau',
    'Chụp ảnh check-in tại Bãi Sau Vũng Tàu.',
    'Vũng Tàu',
    'GPS_PHOTO'::quest_type,
    'Bãi Sau',
    10.337890831000038,
    107.09215712800005,
    250,
    15,
    'ACTIVE',
    TRUE,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM quests WHERE title = 'Chụp ảnh tại Bãi Sau' AND destination = 'Vũng Tàu'
);

INSERT INTO quests (
    title, description, destination, type, target_name, target_lat, target_lng,
    radius_m, reward_coins, status, is_active, is_featured, created_at, updated_at
)
SELECT
    'Check-in Mũi Nghinh Phong',
    'Chụp ảnh tại Mũi Nghinh Phong và gửi minh chứng.',
    'Vũng Tàu',
    'GPS_PHOTO'::quest_type,
    'Mũi Nghinh Phong',
    10.320789,
    107.081957,
    200,
    20,
    'ACTIVE',
    TRUE,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM quests WHERE title = 'Check-in Mũi Nghinh Phong' AND destination = 'Vũng Tàu'
);
