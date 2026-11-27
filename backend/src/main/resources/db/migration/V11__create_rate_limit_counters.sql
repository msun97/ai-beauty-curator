-- 요청 횟수 제한 (FIX-009, NFR-SEC)
--
-- 가입·로그인·메일 발송 API를 IP와 받는 이메일 기준으로 일정 시간 안에 몇 번까지만 허용한다.
-- 고정 시간 구간(예: 1분, 1시간) 방식: 구간이 바뀌면 횟수를 처음부터 센다.
--
-- counter_key: "제한 이름:대상(IP 또는 이메일)"을 서버 비밀키로 HMAC-SHA256한 값(16진수 64자).
--              IP·이메일 원문을 남기지 않기 위함이다.
-- expires_at:  이 구간이 끝나는 시각. 지난 행은 정리 작업이 지운다.

CREATE TABLE rate_limit_counters (
    counter_key  varchar(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    count        integer     NOT NULL,
    expires_at   timestamptz NOT NULL,
    CONSTRAINT ck_rate_limit_counters_count CHECK (count >= 0)
);

-- 정리 작업이 구간 종료 시각으로 찾는다
CREATE INDEX ix_rate_limit_counters_expires_at ON rate_limit_counters (expires_at);
