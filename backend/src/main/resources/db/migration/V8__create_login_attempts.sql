-- 로그인 실패 기록을 회원 행이 아니라 이메일 기준으로 관리한다 (FIX-006, FR-AUTH-02)
--
-- 회원 행(users)에 실패 횟수를 두면 가입하지 않은 이메일은 세지도 잠그지도 못해,
-- "5번 틀리면 잠기는지"와 "실패 기록 저장에 드는 시간"으로 가입 여부가 드러났다.
-- 가입 여부와 관계없이 모든 이메일을 똑같이 기록·잠금한다.
--
-- email_key: 정규화한 이메일을 서버 비밀키로 HMAC-SHA256한 값(16진수 64자).
--            가입하지 않은 사람의 이메일 원문을 남기지 않기 위함이다.
-- 오래된 행(잠금이 끝났고 마지막 시도가 하루 넘게 지난 행)은 정리 작업이 지운다.

CREATE TABLE login_attempts (
    email_key    char(64)    PRIMARY KEY,
    failed_count integer     NOT NULL DEFAULT 0,
    locked_until timestamptz,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT ck_login_attempts_failed_count CHECK (failed_count >= 0)
);

-- 정리 작업이 마지막 시도 시각으로 찾는다
CREATE INDEX ix_login_attempts_updated_at ON login_attempts (updated_at);
