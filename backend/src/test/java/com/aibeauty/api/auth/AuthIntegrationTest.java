package com.aibeauty.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.auth.application.PasswordResetMailSender;
import com.aibeauty.api.global.security.AuthCookies;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

	private static final String PASSWORD = "password123";
	private static final String LOCKED_MESSAGE = "로그인을 5번 잘못 시도해서 10분 동안 로그인이 제한돼요. "
			+ "10분 뒤에 다시 시도하거나, 비밀번호를 재설정하면 바로 로그인할 수 있어요.";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PasswordResetMailSender mailSender;

	@Nested
	@DisplayName("회원가입")
	class Signup {

		@Test
		@DisplayName("가입하면 토큰 쿠키가 발급되고 바로 내 정보를 조회할 수 있다")
		void signupIssuesCookies() throws Exception {
			String email = newEmail();
			MockHttpServletResponse response = signup(email).getResponse();

			assertThat(response.getStatus()).isEqualTo(201);
			Cookie access = response.getCookie(AuthCookies.ACCESS_TOKEN);
			Cookie refresh = response.getCookie(AuthCookies.REFRESH_TOKEN);
			assertThat(access).isNotNull();
			assertThat(access.isHttpOnly()).isTrue();
			assertThat(refresh).isNotNull();
			assertThat(refresh.getPath()).isEqualTo("/api/auth");

			mockMvc.perform(get("/api/users/me").cookie(access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("USER"));
		}

		@Test
		@DisplayName("이메일은 소문자로 저장되고, 대소문자만 다른 이메일은 중복으로 본다")
		void emailIsNormalized() throws Exception {
			String email = newEmail();
			signup(email.toUpperCase());

			mockMvc.perform(get("/api/auth/email-availability").param("email", email))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false));
			mockMvc.perform(json(post("/api/auth/signup"), signupBody(email, PASSWORD, true)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
				// 화면이 이메일 입력란 아래에 표시할 수 있도록 필드 오류로도 내려준다
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("이미 가입된 이메일이에요."));
		}

		@Test
		@DisplayName("필수 약관(피부 정보 포함)에 모두 동의하지 않으면 가입할 수 없다")
		void requiresAllConsents() throws Exception {
			mockMvc.perform(json(post("/api/auth/signup"), signupBody(newEmail(), PASSWORD, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("agreements"));
		}

		@Test
		@DisplayName("비밀번호 규칙을 지키지 않으면 필드 오류를 반환한다")
		void validatesPassword() throws Exception {
			mockMvc.perform(json(post("/api/auth/signup"), signupBody(newEmail(), "onlyletters", true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
		}
	}

	@Nested
	@DisplayName("로그인")
	class Login {

		@Test
		@DisplayName("비밀번호가 틀린 경우와 없는 이메일인 경우 같은 오류를 반환한다")
		void sameErrorForUnknownEmailAndWrongPassword() throws Exception {
			String email = newEmail();
			signup(email);

			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "wrong12345")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
			mockMvc.perform(json(post("/api/auth/login"), loginBody(newEmail(), "wrong12345")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				// 어느 항목이 틀렸는지 알려주면 가입 여부가 드러나므로 필드 오류를 붙이지 않는다
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
		}

		@Test
		@DisplayName("연속 5회 실패하면 올바른 비밀번호로도 일정 시간 로그인할 수 없다")
		void locksAfterFiveFailures() throws Exception {
			String email = newEmail();
			signup(email);

			for (int i = 0; i < 4; i++) {
				mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "wrong12345")))
					.andExpect(status().isUnauthorized());
			}
			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "wrong12345")))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
				// 횟수·시간은 설정값(max-failures: 5, lock-duration: 10m)으로 채워진다
				.andExpect(jsonPath("$.message").value(LOCKED_MESSAGE));
			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, PASSWORD)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(LOCKED_MESSAGE));
		}

		@Test
		@DisplayName("상태를 바꾸는 요청에 CSRF 방지 헤더가 없으면 거부한다")
		void rejectsWithoutCsrfHeader() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content(loginBody(newEmail(), PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
		}

		@Test
		@DisplayName("토큰 없이 보호된 API를 호출하면 401을 반환한다")
		void requiresAuthentication() throws Exception {
			mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}

		@Test
		@DisplayName("Authorization 헤더의 Bearer 토큰으로도 인증할 수 있다 (서버 컴포넌트 호출)")
		void acceptsBearerHeader() throws Exception {
			String accessToken = signup(newEmail()).getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();

			mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("변조된 토큰은 거부한다")
		void rejectsTamperedToken() throws Exception {
			String accessToken = signup(newEmail()).getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();

			mockMvc.perform(get("/api/users/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, accessToken + "x")))
				.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("토큰 재발급·로그아웃")
	class Refresh {

		@Test
		@DisplayName("재발급하면 새 Refresh Token으로 교체되고, 이전 토큰을 다시 쓰면 묶음 전체가 폐기된다")
		void rotatesAndDetectsReuse() throws Exception {
			Cookie first = signup(newEmail()).getResponse().getCookie(AuthCookies.REFRESH_TOKEN);

			MockHttpServletResponse rotated = refresh(first).andExpect(status().isOk()).andReturn().getResponse();
			Cookie second = rotated.getCookie(AuthCookies.REFRESH_TOKEN);
			assertThat(second.getValue()).isNotEqualTo(first.getValue());

			// 이미 사용된 토큰 재사용 → 탈취로 판단
			refresh(first).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
			// 같은 묶음의 최신 토큰도 폐기됨
			refresh(second).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("만료된 Access Token 쿠키가 남아 있어도 재발급 요청은 막히지 않는다")
		void refreshIgnoresAccessTokenCookie() throws Exception {
			Cookie refreshCookie = signup(newEmail()).getResponse().getCookie(AuthCookies.REFRESH_TOKEN);

			mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "fetch")
					.cookie(refreshCookie, new Cookie(AuthCookies.ACCESS_TOKEN, "expired.or.invalid")))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("로그아웃하면 Refresh Token을 더 이상 쓸 수 없다")
		void logoutRevokesRefreshToken() throws Exception {
			Cookie refreshCookie = signup(newEmail()).getResponse().getCookie(AuthCookies.REFRESH_TOKEN);

			mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "fetch").cookie(refreshCookie))
				.andExpect(status().isNoContent());
			refresh(refreshCookie).andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("비밀번호 재설정")
	class PasswordReset {

		@Test
		@DisplayName("링크로 비밀번호를 바꾸면 기존 로그인이 해제되고, 같은 링크는 다시 쓸 수 없다")
		void resetFlow() throws Exception {
			String email = newEmail();
			Cookie oldRefresh = signup(email).getResponse().getCookie(AuthCookies.REFRESH_TOKEN);

			mockMvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted());
			ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
			verify(mailSender).send(eq(email), link.capture());
			String token = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());

			String confirmBody = "{\"token\":\"" + token + "\",\"newPassword\":\"newpass456\"}";
			mockMvc.perform(json(post("/api/auth/password-reset/confirm"), confirmBody))
				.andExpect(status().isNoContent());

			refresh(oldRefresh).andExpect(status().isUnauthorized());
			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, PASSWORD)))
				.andExpect(status().isUnauthorized());
			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "newpass456")))
				.andExpect(status().isOk());
			mockMvc.perform(json(post("/api/auth/password-reset/confirm"), confirmBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
		}

		@Test
		@DisplayName("가입되지 않은 이메일도 같은 응답을 받고, 메일은 보내지 않는다")
		void sameResponseForUnknownEmail() throws Exception {
			mockMvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"" + newEmail() + "\"}"))
				.andExpect(status().isAccepted());
			verify(mailSender, never()).send(any(), any());
		}
	}

	// --- helpers ---

	private org.springframework.test.web.servlet.MvcResult signup(String email) throws Exception {
		return mockMvc.perform(json(post("/api/auth/signup"), signupBody(email, PASSWORD, true)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private org.springframework.test.web.servlet.ResultActions refresh(Cookie refreshCookie) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "fetch").cookie(refreshCookie));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).header("X-Requested-With", "fetch").content(body);
	}

	private static String signupBody(String email, String password, boolean agreeSkinData) {
		return """
				{"email":"%s","password":"%s",
				 "agreements":{"terms":true,"privacy":true,"skinData":%s}}""".formatted(email, password, agreeSkinData);
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}
}
