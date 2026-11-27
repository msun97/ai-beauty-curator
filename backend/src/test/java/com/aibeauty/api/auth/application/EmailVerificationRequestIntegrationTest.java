package com.aibeauty.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.aibeauty.api.global.mail.AuthMailSender;

/** 인증 코드 요청 (FIX-008) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationRequestIntegrationTest {

	@Autowired
	EmailVerificationService service;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	AuthMailSender mailSender;

	private static String newEmail() {
		return "verify-" + UUID.randomUUID() + "@example.com";
	}

	@Test
	@DisplayName("6자리 숫자 코드를 메일로 보내고, DB에는 코드 원문 대신 해시만 남긴다")
	void sendsCode() {
		String email = newEmail();
		service.requestCode(email);

		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailSender).sendVerificationCode(eq(email), code.capture());
		assertThat(code.getValue()).matches("\\d{6}");
		String stored = jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE email = ?", String.class, email);
		assertThat(stored).hasSize(64).doesNotContain(code.getValue());
	}

	@Test
	@DisplayName("대소문자·공백이 달라도 같은 이메일로 보고, 정규화한 주소로 보낸다")
	void normalizesEmail() {
		String email = newEmail();
		service.requestCode("  " + email.toUpperCase() + " ");
		verify(mailSender).sendVerificationCode(eq(email), anyString());
	}

	@Test
	@DisplayName("60초 안에 다시 요청하면 메일을 보내지 않고, 60초가 지나면 새 코드를 보낸다")
	void resendInterval() {
		String email = newEmail();
		service.requestCode(email);
		service.requestCode(email);
		verify(mailSender, times(1)).sendVerificationCode(eq(email), anyString());

		jdbc.update("UPDATE email_verifications SET sent_at = sent_at - interval '61 seconds' WHERE email = ?", email);
		service.requestCode(email);
		verify(mailSender, times(2)).sendVerificationCode(eq(email), anyString());
	}

	@Test
	@DisplayName("코드 요청은 회원 테이블을 보지 않아 가입된 이메일도 똑같이 코드를 받는다")
	void registeredEmailAlsoGetsCode() {
		String email = newEmail();
		jdbc.update("""
				INSERT INTO users (email, password_hash, nickname, role, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'x', 'n', 'USER', gen_random_uuid(), now(), now(), now())
				""", email);
		service.requestCode(email);
		verify(mailSender).sendVerificationCode(eq(email), anyString());
		verify(mailSender, never()).sendPasswordResetLink(anyString(), anyString());
	}

	@Test
	@DisplayName("코드는 항상 6자리(앞자리 0 포함)")
	void codeFormat() {
		for (int i = 0; i < 1000; i++) {
			assertThat(EmailVerificationService.newCode()).matches("\\d{6}");
		}
	}

	@Test
	@DisplayName("같은 이메일로 동시에 5번 요청해도 행은 하나, 메일은 한 통만 보낸다 (충돌·중복 발송 없음)")
	void concurrentRequests() throws Exception {
		String email = newEmail();
		int threads = 5;
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					service.requestCode(email);
					return null;
				}));
			}
			start.countDown();
			for (java.util.concurrent.Future<?> result : results) {
				result.get();
			}
		}
		finally {
			pool.shutdownNow();
		}
		verify(mailSender, times(1)).sendVerificationCode(eq(email), anyString());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM email_verifications WHERE email = ?", Integer.class, email))
			.isOne();
	}
}
