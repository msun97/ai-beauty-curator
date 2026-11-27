-- 로그인 실패 기록을 login_attempts(이메일 기준)로 옮겼으므로 회원 행의 컬럼을 지운다 (FIX-006)
-- 배포 시점에 남아 있던 잠금(최대 10분)은 이전되지 않는다. 짧은 시간이라 그대로 둔다.
ALTER TABLE users
    DROP COLUMN failed_login_count,
    DROP COLUMN locked_until;
