package com.aibeauty.api.auth.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.global.security.AuthProperties;

/**
 * 이메일 기준 로그인 실패 기록·잠금 (FIX-006, FR-AUTH-02).
 *
 * 가입 여부와 관계없이 모든 이메일을 똑같이 세고 잠가, 잠금 여부와 응답 시간으로 가입 여부가 드러나지 않게 한다.
 * 키는 이메일 원문이 아니라 {@link LoginEmailKey}의 HMAC 값이다.
 *
 * 실패 기록은 "없으면 만들고 있으면 +1"을 쿼리 한 번(INSERT … ON CONFLICT)으로 처리한다.
 * 같은 이메일로 동시에 실패해도 횟수가 빠지지 않고, 행이 없을 때 동시에 만들다 충돌하지도 않는다.
 */
@Service
public class LoginAttemptService {

	/**
	 * 실패 1회 기록. 기준 횟수에 닿으면 잠그고 횟수를 0으로 되돌린다(잠금이 끝나면 다시 처음부터 센다).
	 * 이미 잠겨 있던 행이면 잠금 시각은 그대로 둔다.
	 * CASE 안의 파라미터는 PostgreSQL이 타입을 추론하지 못해 text로 보므로 CAST로 타입을 정해 준다.
	 */
	private static final String RECORD_FAILURE = """
			INSERT INTO login_attempts (email_key, failed_count, locked_until, updated_at)
			VALUES (:key,
			        CASE WHEN 1 >= CAST(:max AS integer) THEN 0 ELSE 1 END,
			        CASE WHEN 1 >= CAST(:max AS integer) THEN CAST(:lockUntil AS timestamptz) END,
			        :now)
			ON CONFLICT (email_key) DO UPDATE SET
			    failed_count = CASE WHEN login_attempts.failed_count + 1 >= CAST(:max AS integer) THEN 0
			                        ELSE login_attempts.failed_count + 1 END,
			    locked_until = CASE WHEN login_attempts.failed_count + 1 >= CAST(:max AS integer)
			                        THEN CAST(:lockUntil AS timestamptz)
			                        ELSE login_attempts.locked_until END,
			    updated_at = :now
			RETURNING locked_until
			""";

	private final NamedParameterJdbcTemplate jdbc;
	private final AuthProperties properties;

	public LoginAttemptService(NamedParameterJdbcTemplate jdbc, AuthProperties properties) {
		this.jdbc = jdbc;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public boolean isLocked(String emailKey, Instant now) {
		List<Timestamp> lockedUntil = jdbc.queryForList(
				"SELECT locked_until FROM login_attempts WHERE email_key = :key",
				new MapSqlParameterSource("key", emailKey), Timestamp.class);
		return !lockedUntil.isEmpty() && isAfter(lockedUntil.get(0), now);
	}

	/** @return 이번 실패로(또는 이미) 잠긴 상태이면 true */
	@Transactional
	public boolean recordFailure(String emailKey, Instant now) {
		AuthProperties.Login login = properties.login();
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("key", emailKey)
			.addValue("max", login.maxFailures())
			.addValue("lockUntil", Timestamp.from(now.plus(login.lockDuration())))
			.addValue("now", Timestamp.from(now));
		Timestamp lockedUntil = jdbc.queryForObject(RECORD_FAILURE, params, Timestamp.class);
		return isAfter(lockedUntil, now);
	}

	/** 로그인 성공·비밀번호 재설정 시 실패 기록과 잠금을 지운다 */
	@Transactional
	public void reset(String emailKey) {
		jdbc.update("DELETE FROM login_attempts WHERE email_key = :key", new MapSqlParameterSource("key", emailKey));
	}

	/**
	 * 오래된 기록 정리: 잠금이 끝났고(또는 잠긴 적 없고) 마지막 시도가 staleBefore보다 이전인 행.
	 * @return 지운 행 수
	 */
	@Transactional
	public int deleteStale(Instant staleBefore, Instant now) {
		return jdbc.update("""
				DELETE FROM login_attempts
				WHERE updated_at < :staleBefore AND (locked_until IS NULL OR locked_until <= :now)
				""", new MapSqlParameterSource()
			.addValue("staleBefore", Timestamp.from(staleBefore))
			.addValue("now", Timestamp.from(now)));
	}

	private static boolean isAfter(Timestamp lockedUntil, Instant now) {
		return lockedUntil != null && lockedUntil.toInstant().isAfter(now);
	}
}
