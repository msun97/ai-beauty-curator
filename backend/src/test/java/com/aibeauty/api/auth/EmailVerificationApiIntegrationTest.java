package com.aibeauty.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.mail.AuthMailSender;

/** 가입 인증 코드 API 계약 (FIX-008, 프론트 signupApi.ts와 같아야 함) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	AuthMailSender mailSender;

	@Test
	@DisplayName("코드 요청: 새 이메일과 가입된 이메일 모두 202, 본문 없음 (응답이 완전히 같음)")
	void requestSameForBoth() throws Exception {
		String fresh = newEmail();
		String registered = newEmail();
		insertUser(registered);

		MvcResult a = request(fresh).andExpect(status().isAccepted()).andReturn();
		MvcResult b = request(registered).andExpect(status().isAccepted()).andReturn();
		assertThat(a.getResponse().getContentAsString()).isEqualTo(b.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	@DisplayName("코드 확인: 새 이메일은 {registered:false, verificationToken}, 가입된 이메일은 {registered:true}만")
	void confirmShapes() throws Exception {
		String fresh = newEmail();
		request(fresh);
		confirm(fresh, sentCode(fresh)).andExpect(status().isOk())
			.andExpect(jsonPath("$.registered").value(false))
			.andExpect(jsonPath("$.verificationToken").isString());

		String registered = newEmail();
		insertUser(registered);
		request(registered);
		confirm(registered, sentCode(registered)).andExpect(status().isOk())
			.andExpect(jsonPath("$.registered").value(true))
			.andExpect(jsonPath("$.verificationToken").doesNotExist());
	}

	@Test
	@DisplayName("틀린 코드: 400 INVALID_VERIFICATION_CODE, fieldErrors는 code 입력란")
	void wrongCode() throws Exception {
		String email = newEmail();
		request(email);
		String code = sentCode(email);
		confirm(email, code.equals("000000") ? "111111" : "000000")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
	}

	@Test
	@DisplayName("5번 틀리면 429 VERIFICATION_ATTEMPTS_EXCEEDED")
	void attemptsExceeded() throws Exception {
		String email = newEmail();
		request(email);
		String wrong = sentCode(email).equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 4; i++) confirm(email, wrong).andExpect(status().isBadRequest());
		confirm(email, wrong).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"))
			.andExpect(jsonPath("$.message").value("인증 코드를 5번 잘못 입력했어요. 인증 코드를 다시 받아 주세요."));
	}

	@Test
	@DisplayName("입력값 검사: 이메일 형식, 6자리 숫자가 아닌 코드는 400 (코드 확인 시도로 세지 않음)")
	void validation() throws Exception {
		perform("/api/auth/email-verification/request", "{\"email\":\"not-an-email\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
		confirm(newEmail(), "12ab").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
	}

	private String sentCode(String email) {
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailSender).sendVerificationCode(eq(email), code.capture());
		return code.getValue();
	}

	private void insertUser(String email) {
		jdbc.update("""
				INSERT INTO users (email, password_hash, nickname, role, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'x', 'n', 'USER', gen_random_uuid(), now(), now(), now())
				""", email);
	}

	private ResultActions request(String email) throws Exception {
		return perform("/api/auth/email-verification/request", "{\"email\":\"" + email + "\"}");
	}

	private ResultActions confirm(String email, String code) throws Exception {
		return perform("/api/auth/email-verification/confirm", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
	}

	private ResultActions perform(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.header("X-Requested-With", "XMLHttpRequest")
			.content(body));
	}

	private static String newEmail() {
		return "api-" + UUID.randomUUID() + "@example.com";
	}
}
