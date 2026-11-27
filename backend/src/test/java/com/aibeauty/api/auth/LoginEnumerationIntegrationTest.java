package com.aibeauty.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.aibeauty.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;


/**
 * 로그인 실패 응답으로 가입 여부를 알 수 없는지 확인한다 (FIX-006, FR-AUTH-02).
 * 가입된 이메일(비밀번호 틀림)과 가입하지 않은 이메일이 매번 같은 응답을 받아야 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginEnumerationIntegrationTest {

	private static final String PASSWORD = "password123";
	private static final String WRONG = "wrong12345";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("가입된 이메일과 없는 이메일은 1~6번째 실패까지 매번 상태 코드·오류 코드·문구가 같고, 둘 다 5번째에 잠긴다")
	void sameResponsesUntilLocked() throws Exception {
		String registered = newEmail();
		signup(registered);
		String unknown = newEmail();

		for (int attempt = 1; attempt <= 6; attempt++) {
			MvcResult a = login(registered, WRONG).andReturn();
			MvcResult b = login(unknown, WRONG).andReturn();

			assertThat(a.getResponse().getStatus()).as("%d번째 상태 코드", attempt)
				.isEqualTo(b.getResponse().getStatus())
				.isEqualTo(attempt < 5 ? 401 : 429);
			assertThat(field(a, "code")).as("%d번째 오류 코드", attempt)
				.isEqualTo(field(b, "code"))
				.isEqualTo(attempt < 5 ? "INVALID_CREDENTIALS" : "LOGIN_LOCKED");
			assertThat(field(a, "message")).as("%d번째 문구", attempt).isEqualTo(field(b, "message"));
		}
	}

	@Test
	@DisplayName("로그인에 성공하면 실패 횟수가 처음부터 다시 센다")
	void successResetsCount() throws Exception {
		String email = newEmail();
		signup(email);
		for (int i = 0; i < 4; i++) login(email, WRONG).andExpect(status().isUnauthorized());
		login(email, PASSWORD).andExpect(status().isOk());
		for (int i = 0; i < 4; i++) {
			login(email, WRONG).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}
	}

	@Test
	@DisplayName("대소문자·공백만 다른 이메일은 같은 이메일로 세어 잠금을 피할 수 없다")
	void caseVariantsShareCount() throws Exception {
		String email = newEmail();
		String[] variants = { email, email.toUpperCase(), " " + email, email + " ", email.toUpperCase() };
		for (int i = 0; i < 4; i++) login(variants[i], WRONG).andExpect(status().isUnauthorized());
		login(variants[4], WRONG).andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("실패 기록에는 이메일 원문이 저장되지 않는다")
	void noRawEmailStored() throws Exception {
		String email = newEmail();
		login(email, WRONG);
		Integer containsEmail = jdbc.queryForObject(
				"SELECT count(*) FROM login_attempts WHERE email_key LIKE ?", Integer.class,
				"%" + email.substring(0, email.indexOf('@')) + "%");
		assertThat(containsEmail).isZero();
	}

	private static String field(MvcResult result, String name) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$." + name);
	}

	private void signup(String email) throws Exception {
		perform("/api/auth/signup", """
				{"email":"%s","password":"%s","agreements":{"terms":true,"privacy":true,"skinData":true}}
				""".formatted(email, PASSWORD)).andExpect(status().isCreated());
	}

	private ResultActions login(String email, String password) throws Exception {
		return perform("/api/auth/login", """
				{"email":"%s","password":"%s"}
				""".formatted(email, password));
	}

	private ResultActions perform(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.header("X-Requested-With", "XMLHttpRequest")
			.content(body));
	}

	private static String newEmail() {
		return "enum-" + UUID.randomUUID() + "@example.com";
	}
}
