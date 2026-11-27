-- 이메일 인증 코드 가입 (FIX-008, FR-AUTH-01)
--
-- 가입 여부는 인증 코드를 맞힌 사람(= 메일함 주인)에게만 알려준다.
-- 1) 코드 요청: 가입 여부와 관계없이 6자리 코드를 만들어 메일로 보낸다 (code_hash, 10분)
-- 2) 코드 확인: 맞으면 새 이메일에는 가입용 1회용 토큰을 준다 (token_hash, 30분)
-- 3) 가입: 토큰으로 이메일을 찾아 가입하고 토큰을 사용 처리한다 (used_at)
--
-- 이메일당 한 행만 둔다. 코드를 다시 요청하면 같은 행을 새 코드로 덮어쓴다.
-- 코드·토큰은 원문 대신 해시만 저장한다. 오래된 행은 정리 작업이 지운다.

CREATE TABLE email_verifications (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            varchar(254) NOT NULL,
    code_hash        char(64)     NOT NULL,
    code_expires_at  timestamptz  NOT NULL,
    failed_count     integer      NOT NULL DEFAULT 0,
    sent_at          timestamptz  NOT NULL,
    token_hash       char(64),
    token_expires_at timestamptz,
    used_at          timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT uk_email_verifications_email UNIQUE (email),
    CONSTRAINT uk_email_verifications_token UNIQUE (token_hash),
    CONSTRAINT ck_email_verifications_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_email_verifications_failed_count CHECK (failed_count >= 0)
);

-- 정리 작업이 마지막 변경 시각으로 찾는다
CREATE INDEX ix_email_verifications_updated_at ON email_verifications (updated_at);
