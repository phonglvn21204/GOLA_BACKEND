UPDATE prices SET is_active = false;
UPDATE products SET is_active = false;

ALTER TABLE prices ALTER COLUMN currency SET DEFAULT 'vnd';
ALTER TABLE orders ALTER COLUMN currency SET DEFAULT 'vnd';

INSERT INTO products (id, stripe_product_id, name, description, is_active, created_at)
VALUES
  ('71000000-0000-4000-8000-000000000001', 'FREE', 'Miễn phí', 'Hoàn hảo để thử GOLA', true, NOW()),
  ('71000000-0000-4000-8000-000000000002', 'BASIC_TRIP', 'Cơ bản', 'Cho phượt thủ đơn độc', true, NOW()),
  ('71000000-0000-4000-8000-000000000003', 'GROUP_PRO_TRIP', 'Nhóm Pro', 'Cho nhóm 2-4 người', true, NOW())
ON CONFLICT (stripe_product_id) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  is_active = true;

INSERT INTO prices (id, product_id, stripe_price_id, amount, currency, interval_type, interval_count, is_active, created_at)
VALUES
  ('72000000-0000-4000-8000-000000000001', '71000000-0000-4000-8000-000000000001', 'FREE_FOREVER_VND', 0, 'vnd', 'forever', 1, true, NOW()),
  ('72000000-0000-4000-8000-000000000002', '71000000-0000-4000-8000-000000000002', 'BASIC_TRIP_50000_VND', 50000, 'vnd', 'trip', 1, true, NOW()),
  ('72000000-0000-4000-8000-000000000003', '71000000-0000-4000-8000-000000000003', 'GROUP_PRO_TRIP_100000_VND', 100000, 'vnd', 'trip', 1, true, NOW())
ON CONFLICT (stripe_price_id) DO UPDATE SET
  product_id = EXCLUDED.product_id,
  amount = EXCLUDED.amount,
  currency = EXCLUDED.currency,
  interval_type = EXCLUDED.interval_type,
  interval_count = EXCLUDED.interval_count,
  is_active = true;
