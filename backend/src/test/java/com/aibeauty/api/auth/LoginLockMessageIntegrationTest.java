package com.aibeauty.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

import com.aibeauty.api.TestcontainersConfiguration;

/**
 * 잠금 안내 문구의 횟수·시간이 글자로 박혀 있지 않고 설정값을 따라가는지 확인한다 (FR-AUTH-02, FIX-001).
 * 기본값(5번, 10분)과 다른 값으로 설정을 바꿔 띄운다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.auth.login.max-failures=3", "app.auth.login.lock-duration=90s" })
class LoginLockMessageIntegrationTest {

	private static final String PASSWORD = "password123";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("설정을 3번·90초로 바꾸면 3번째 실패에서 잠기고 문구도 3번·1분 30초로 바뀐다")
	void messageFollowsConfiguration() throws Exception {
		String email = "lock-" + UUID.randomUUID() + "@example.com";
		perform("/api/auth/signup", VerifiedEmails.signupBody(VerifiedEmails.issueToken(jdbc, email), PASSWORD))
			.andExpect(status().isCreated());

		String wrong = """
				{"email":"%s","password":"wrong12345"}
				""".formatted(email);
		perform("/api/auth/login", wrong).andExpect(status().isUnauthorized());
		perform("/api/auth/login", wrong).andExpect(status().isUnauthorized());
		perform("/api/auth/login", wrong)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
			.andExpect(jsonPath("$.message").value("로그인을 3번 잘못 시도해서 1분 30초 동안 로그인이 제한돼요. "
					+ "1분 30초 뒤에 다시 시도하거나, 비밀번호를 재설정하면 바로 로그인할 수 있어요."));
	}

	private org.springframework.test.web.servlet.ResultActions perform(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.header("X-Requested-With", "XMLHttpRequest")
			.content(body));
	}
}
