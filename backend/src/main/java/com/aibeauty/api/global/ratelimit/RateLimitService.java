package com.aibeauty.api.global.ratelimit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.global.security.Hmac;

/**
 * 요청 횟수 제한 (FIX-009). 고정 시간 구간마다 횟수를 센다 (예: 1분 구간이면 매분 0초에 처음부터).
 *
 * "이번 구간에 +1 하고 지금 몇 번째인지"를 쿼리 한 번(INSERT ... ON CONFLICT)으로 처리해, 동시에 들어와도 빠짐없이 센다.
 * 키는 "제한 이름:대상"의 HMAC 값이라 IP·이메일 원문이 DB에 남지 않는다.
 * 한도를 넘은 요청도 횟수에 넣는다 (계속 두드려도 구간이 끝나야 풀림).
 */
@Service
public class RateLimitService {

	private static final String HIT = """
			INSERT INTO rate_limit_counters (counter_key, window_start, count, expires_at)
			VALUES (:key, :windowStart, 1, :expiresAt)
			ON CONFLICT (counter_key) DO UPDATE SET
			    count = CASE WHEN rate_limit_counters.window_start = EXCLUDED.window_start
			                 THEN rate_limit_counters.count + 1 ELSE 1 END,
			    window_start = EXCLUDED.window_start,
			    expires_at = EXCLUDED.expires_at
			RETURNING count
			""";
	/** 같은 비밀키를 쓰는 다른 용도(로그인 실패 기록 키 등)와 섞이지 않게 붙이는 머리말 */
	private static final String KEY_PREFIX = "rate-limit:";

	private final NamedParameterJdbcTemplate jdbc;
	private final RateLimitProperties properties;
	private final SecretKeySpec key;
	private final Clock clock;

	public RateLimitService(NamedParameterJdbcTemplate jdbc, RateLimitProperties properties,
			AuthProperties authProperties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.key = Hmac.key(authProperties.login().emailKeySecret());
		this.clock = clock;
	}

	/**
	 * 1회 요청을 센다. 한도를 넘으면 RateLimitExceededException (429, Retry-After 포함).
	 * 업무 처리(가입 등)가 실패해 롤백돼도 횟수는 남아야 하므로 별도 트랜잭션으로 센다.
	 * @param subject IP 또는 정규화한 이메일
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void check(RateLimitRule rule, String subject) {
		if (!properties.enabled()) {
			return;
		}
		RateLimitProperties.Rule config = properties.rule(rule);
		Instant now = clock.instant();
		long windowSeconds = config.window().toSeconds();
		Instant windowStart = Instant.ofEpochSecond(now.getEpochSecond() / windowSeconds * windowSeconds);
		Instant expiresAt = windowStart.plus(config.window());

		Integer count = jdbc.queryForObject(HIT, new MapSqlParameterSource()
			.addValue("key", Hmac.sha256Hex(key, KEY_PREFIX + rule.name() + ":" + subject))
			.addValue("windowStart", Timestamp.from(windowStart))
			.addValue("expiresAt", Timestamp.from(expiresAt)), Integer.class);
		if (count != null && count > config.limit()) {
			throw new RateLimitExceededException(retryAfterSeconds(now, expiresAt));
		}
	}

	/** 정리: 구간이 끝난 카운터 삭제. @return 지운 행 수 */
	@Transactional
	public int deleteExpired(Instant now) {
		return jdbc.update("DELETE FROM rate_limit_counters WHERE expires_at <= :now",
				new MapSqlParameterSource("now", Timestamp.from(now)));
	}

	private static long retryAfterSeconds(Instant now, Instant expiresAt) {
		long millis = Duration.between(now, expiresAt).toMillis();
		return Math.max(1, (millis + 999) / 1000);
	}
}
