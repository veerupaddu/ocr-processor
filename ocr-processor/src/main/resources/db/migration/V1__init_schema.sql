-- V1__init_schema.sql
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(50)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

CREATE TABLE ocr_records (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_name     VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type         VARCHAR(20)  NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    extracted_text    TEXT,
    summary           TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    summary_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    search_vector     TSVECTOR,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- GIN index for full-text search
CREATE INDEX idx_ocr_records_search  ON ocr_records USING GIN (search_vector);
CREATE INDEX idx_ocr_records_user_id ON ocr_records (user_id);

-- Trigger: maintain search_vector on insert/update
CREATE OR REPLACE FUNCTION ocr_records_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.document_name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.extracted_text, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.summary, '')), 'C');
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ocr_records_search_vector_trigger
    BEFORE INSERT OR UPDATE ON ocr_records
    FOR EACH ROW EXECUTE FUNCTION ocr_records_search_vector_update();
