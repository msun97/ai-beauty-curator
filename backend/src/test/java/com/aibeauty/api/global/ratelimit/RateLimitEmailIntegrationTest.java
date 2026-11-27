package com.aibeauty.api.global.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.mail.AuthMailSender;

/** 받는 이메일 기준 요청 횟수 제한 (FIX-009). 이 테스트에서는 이메일당 1시간 2회로 낮춘다 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rate-limit.rules.mail-email.limit=2")
class RateLimitEmailIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	AuthMailSender mailSender;

	@Test
	@DisplayName("같은 이메일로 인증 코드·재설정 요청을 합쳐 2번까지, 3번째는 429 (대소문자가 달라도 같은 이메일)")
	void perEmailAcrossMailTypes() throws Exception {
		String email = newEmail();
		request("/api/auth/email-verification/request", email).andExpect(status().isAccepted());
		request("/api/auth/password-reset/request", email.toUpperCase()).andExpect(status().isAccepted());
		request("/api/auth/email-verification/request", email)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
			.andExpect(header().exists("Retry-After"));
		// 다른 이메일은 영향 없음
		request("/api/auth/email-verification/request", newEmail()).andExpect(status().isAccepted());
	}

	@Test
	@DisplayName("비밀번호 재설정: 가입된 이메일과 없는 이메일 모두 3번째에 같은 429 (가입 여부가 드러나지 않음)")
	void sameForRegisteredAndUnknown() throws Exception {
		String registered = newEmail();
		jdbc.update("""
				INSERT INTO users (email, password_hash, nickname, role, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'x', 'n', 'USER', gen_random_uuid(), now(), now(), now())
				""", registered);
		String unknown = newEmail();
		for (String email : new String[] { registered, unknown }) {
			request("/api/auth/password-reset/request", email).andExpect(status().isAccepted());
			request("/api/auth/password-reset/request", email).andExpect(status().isAccepted());
			request("/api/auth/password-reset/request", email).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		}
	}

	private ResultActions request(String path, String email) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.header("X-Requested-With", "fetch")
			.content("{\"email\":\"" + email + "\"}"));
	}

	private static String newEmail() {
		return "mail-limit-" + UUID.randomUUID() + "@example.com";
	}
}
