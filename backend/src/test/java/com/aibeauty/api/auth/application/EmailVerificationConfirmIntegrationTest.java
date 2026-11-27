package com.aibeauty.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.auth.application.EmailVerificationService.Confirmation;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.mail.AuthMailSender;

/** 인증 코드 확인 (FIX-008). 설정: 코드 10분, 토큰 30분, 실패 5회 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationConfirmIntegrationTest {

	@Autowired
	EmailVerificationService service;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	AuthMailSender mailSender;

	private static String newEmail() {
		return "confirm-" + UUID.randomUUID() + "@example.com";
	}

	/** 코드를 요청하고 메일로 보낸 코드를 꺼낸다 */
	private String requestCode(String email) {
		service.requestCode(email);
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailSender).sendVerificationCode(eq(email), code.capture());
		return code.getValue();
	}

	private static String wrong(String code) {
		return code.equals("000000") ? "111111" : "000000";
	}

	private static String errorCode(Throwable e) {
		return ((BusinessException) e).errorCode().name();
	}

	@Test
	@DisplayName("새 이메일: 맞는 코드면 registered=false와 가입용 토큰(해시만 저장)")
	void newEmailGetsToken() {
		String email = newEmail();
		Confirmation result = service.confirmCode(email, requestCode(email));

		assertThat(result.registered()).isFalse();
		assertThat(result.verificationToken()).isNotBlank();
		String storedToken = jdbc.queryForObject("SELECT token_hash FROM email_verifications WHERE email = ?",
				String.class, email);
		assertThat(storedToken).hasSize(64).isNotEqualTo(result.verificationToken());
	}

	@Test
	@DisplayName("가입된 이메일: 맞는 코드면 registered=true, 토큰은 주지 않는다")
	void registeredEmail() {
		String email = newEmail();
		jdbc.update("""
				INSERT INTO users (email, password_hash, nickname, role, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'x', 'n', 'USER', gen_random_uuid(), now(), now(), now())
				""", email);
		Confirmation result = service.confirmCode(email, requestCode(email));
		assertThat(result.registered()).isTrue();
		assertThat(result.verificationToken()).isNull();
	}

	@Test
	@DisplayName("맞힌 코드는 다시 쓸 수 없다")
	void codeIsSingleUse() {
		String email = newEmail();
		String code = requestCode(email);
		service.confirmCode(email, code);
		assertThatThrownBy(() -> service.confirmCode(email, code))
			.satisfies(e -> assertThat(errorCode(e)).isEqualTo("INVALID_VERIFICATION_CODE"));
	}

	@Test
	@DisplayName("틀린 코드는 INVALID_VERIFICATION_CODE(입력란: code), 5번째 실패부터 VERIFICATION_ATTEMPTS_EXCEEDED")
	void wrongCodeAndAttemptLimit() {
		String email = newEmail();
		String code = requestCode(email);
		for (int i = 1; i <= 4; i++) {
			assertThatThrownBy(() -> service.confirmCode(email, wrong(code))).as("%d번째", i)
				.satisfies(e -> assertThat(errorCode(e)).isEqualTo("INVALID_VERIFICATION_CODE"))
				.satisfies(e -> assertThat(((BusinessException) e).errorCode().field()).isEqualTo("code"));
		}
		assertThatThrownBy(() -> service.confirmCode(email, wrong(code)))
			.satisfies(e -> assertThat(errorCode(e)).isEqualTo("VERIFICATION_ATTEMPTS_EXCEEDED"))
			.satisfies(e -> assertThat(((BusinessException) e).userMessage()).contains("5번"));
		// 횟수를 넘긴 뒤에는 맞는 코드도 받지 않는다 (새 코드를 받아야 함)
		assertThatThrownBy(() -> service.confirmCode(email, code))
			.satisfies(e -> assertThat(errorCode(e)).isEqualTo("VERIFICATION_ATTEMPTS_EXCEEDED"));
	}

	@Test
	@DisplayName("새 코드를 받으면 실패 횟수가 초기화되고 이전 코드는 쓸 수 없다")
	void newCodeResets() {
		String email = newEmail();
		String first = requestCode(email);
		for (int i = 0; i < 5; i++) {
			try {
				service.confirmCode(email, wrong(first));
			}
			catch (BusinessException ignored) {
			}
		}
		jdbc.update("UPDATE email_verifications SET sent_at = sent_at - interval '61 seconds' WHERE email = ?", email);
		service.requestCode(email);
		ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
		verify(mailSender, org.mockito.Mockito.times(2)).sendVerificationCode(eq(email), codes.capture());
		String second = codes.getAllValues().get(1);

		if (!second.equals(first)) {
			assertThatThrownBy(() -> service.confirmCode(email, first))
				.satisfies(e -> assertThat(errorCode(e)).isEqualTo("INVALID_VERIFICATION_CODE"));
		}
		assertThat(service.confirmCode(email, second).registered()).isFalse();
	}

	@Test
	@DisplayName("10분이 지난 코드는 만료")
	void expiredCode() {
		String email = newEmail();
		String code = requestCode(email);
		jdbc.update("UPDATE email_verifications SET code_expires_at = now() - interval '1 second' WHERE email = ?", email);
		assertThatThrownBy(() -> service.confirmCode(email, code))
			.satisfies(e -> assertThat(errorCode(e)).isEqualTo("INVALID_VERIFICATION_CODE"));
	}

	@Test
	@DisplayName("코드를 요청한 적 없는 이메일도 같은 INVALID_VERIFICATION_CODE")
	void neverRequested() {
		assertThatThrownBy(() -> service.confirmCode(newEmail(), "123456"))
			.satisfies(e -> assertThat(errorCode(e)).isEqualTo("INVALID_VERIFICATION_CODE"));
	}

	@Test
	@DisplayName("정리: 모두 만료됐거나 토큰을 쓴 오래된 행만 지우고, 쓸 수 있는 토큰·최근 행은 남긴다")
	void deleteStale() {
		String expiredOld = newEmail();
		String usedOld = newEmail();
		String tokenAlive = newEmail();
		String recent = newEmail();
		insertRow(expiredOld, "now() - interval '2 days'", null, null);
		insertRow(usedOld, "now() - interval '2 days'", "now() + interval '10 minutes'", "now() - interval '2 days'");
		insertRow(tokenAlive, "now() - interval '2 days'", "now() + interval '10 minutes'", null);
		insertRow(recent, "now()", null, null);

		service.deleteStale(java.time.Instant.now().minus(java.time.Duration.ofDays(1)), java.time.Instant.now());

		assertThat(count(expiredOld)).isZero();
		assertThat(count(usedOld)).isZero();
		assertThat(count(tokenAlive)).isOne();
		assertThat(count(recent)).isOne();
	}

	/** updatedAt 시각, 토큰 만료 시각, 토큰 사용 시각(SQL 식). 코드는 모두 만료 상태 */
	private void insertRow(String email, String updatedAt, String tokenExpiresAt, String usedAt) {
		jdbc.update("INSERT INTO email_verifications (email, code_hash, code_expires_at, failed_count, sent_at, token_hash, "
				+ "token_expires_at, used_at, created_at, updated_at) VALUES (?, repeat('0', 64), now() - interval '1 second', 0, "
				+ updatedAt + ", " + (tokenExpiresAt == null ? "NULL" : "md5(random()::text) || md5(random()::text)") + ", "
				+ (tokenExpiresAt == null ? "NULL" : tokenExpiresAt) + ", " + (usedAt == null ? "NULL" : usedAt) + ", "
				+ updatedAt + ", " + updatedAt + ")", email);
	}

	private int count(String email) {
		return jdbc.queryForObject("SELECT count(*) FROM email_verifications WHERE email = ?", Integer.class, email);
	}
}
