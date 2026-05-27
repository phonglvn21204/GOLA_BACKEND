CREATE TABLE products (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_product_id VARCHAR(100) NOT NULL UNIQUE,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE prices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id),
    stripe_price_id VARCHAR(100) NOT NULL UNIQUE,
    amount          BIGINT NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'usd',
    interval_type   VARCHAR(20),
    interval_count  INTEGER DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    stripe_subscription_id  VARCHAR(100) UNIQUE,
    stripe_customer_id      VARCHAR(100),
    product_id              UUID REFERENCES products(id),
    price_id                UUID REFERENCES prices(id),
    status                  sub_status NOT NULL DEFAULT 'INCOMPLETE',
    current_period_start    TIMESTAMPTZ,
    current_period_end      TIMESTAMPTZ,
    cancel_at_period_end    BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_stripe_id ON subscriptions(stripe_subscription_id);

CREATE TABLE orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES profiles(id),
    stripe_session_id     VARCHAR(100) UNIQUE,
    stripe_payment_intent VARCHAR(100),
    amount                BIGINT,
    currency              VARCHAR(10) NOT NULL DEFAULT 'usd',
    status                payment_status NOT NULL DEFAULT 'PENDING',
    price_id              UUID REFERENCES prices(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE payment_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_event_id   VARCHAR(100) NOT NULL UNIQUE,
    type                VARCHAR(100) NOT NULL,
    payload             JSONB,
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);