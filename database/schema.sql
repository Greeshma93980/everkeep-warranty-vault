CREATE TABLE IF NOT EXISTS households (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS assets (
    id BIGSERIAL PRIMARY KEY,
    household_id BIGINT NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    brand TEXT,
    model TEXT,
    serial_number TEXT,
    gstin VARCHAR(15),
    room_tag TEXT,
    purchase_date DATE,
    purchase_price NUMERIC(12, 2),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS warranty_components (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    warranty_type TEXT NOT NULL CHECK (warranty_type IN ('STANDARD', 'PRORATED')),
    duration_months INTEGER NOT NULL CHECK (duration_months >= 0),
    expiry_date DATE,
    replacement_value NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT REFERENCES assets(id) ON DELETE SET NULL,
    file_url TEXT NOT NULL,
    sha256_hash CHAR(64) NOT NULL,
    document_type TEXT NOT NULL DEFAULT 'RECEIPT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_assets_household ON assets(household_id);
CREATE INDEX IF NOT EXISTS idx_components_expiry ON warranty_components(expiry_date);
