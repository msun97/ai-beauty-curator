-- 회원·인증 (docs/ERD.md 2장, ADR-004)

CREATE TABLE users (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email               varchar(254) NOT NULL,
    password_hash       varchar(100) NOT NULL,
    nickname            varchar(20)  NOT NULL,
    role                varchar(10)  NOT NULL DEFAULT 'USER',
    actor_key           uuid         NOT NULL,
    failed_login_count  integer      NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    password_changed_at timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_actor_key UNIQUE (actor_key),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    -- 이메일은 애플리케이션에서 소문자로 정규화해 저장한다
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

CREATE TABLE user_consents (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_type  varchar(20) NOT NULL,
    terms_version varchar(20) NOT NULL,
    agreed_at     timestamptz NOT NULL,
    withdrawn_at  timestamptz,
    CONSTRAINT ck_user_consents_type CHECK (consent_type IN ('TERMS', 'PRIVACY', 'SKIN_DATA'))
);
CREATE INDEX ix_user_consents_user ON user_consents (user_id);

CREATE TABLE password_reset_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE refresh_tokens (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     varchar(64) NOT NULL,
    family_id      uuid        NOT NULL,
    expires_at     timestamptz NOT NULL,
    used_at        timestamptz,
    revoked_at     timestamptz,
    revoked_reason varchar(20),
    created_at     timestamptz NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_reason CHECK (revoked_reason IN ('LOGOUT', 'PASSWORD_RESET', 'REUSE_DETECTED'))
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
