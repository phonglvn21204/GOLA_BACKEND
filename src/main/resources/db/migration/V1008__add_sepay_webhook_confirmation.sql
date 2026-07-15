ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS bank_reference_code VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bank_transaction_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS paid_amount BIGINT,
    ADD COLUMN IF NOT EXISTS auto_confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS auto_confirm_provider VARCHAR(40);

CREATE TABLE IF NOT EXISTS bank_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(40) NOT NULL,
    external_reference VARCHAR(160) NOT NULL,
    order_id UUID REFERENCES orders(id),
    amount BIGINT,
    status VARCHAR(40) NOT NULL,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_bank_webhook_provider_reference UNIQUE (provider, external_reference)
);

CREATE INDEX IF NOT EXISTS idx_bank_webhook_events_order_id ON bank_webhook_events(order_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_code ON orders(order_code);
CREATE INDEX IF NOT EXISTS idx_orders_transfer_content ON orders(transfer_content);
