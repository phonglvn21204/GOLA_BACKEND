ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(30),
    ADD COLUMN IF NOT EXISTS order_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS transfer_content VARCHAR(160),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_order_code
    ON orders(order_code)
    WHERE order_code IS NOT NULL;
