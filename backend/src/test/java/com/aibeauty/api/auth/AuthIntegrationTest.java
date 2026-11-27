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
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.mail.AuthMailSender;
import com.aibeauty.api.global.security.AuthCookies;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

	private static final String PASSWORD = "password123";
	/** 예전 경로 쿠키를 지우는 Set-Cookie인지 (이미 쓴 토큰이 먼저 전송되어 재사용으로 오인되는 것을 막음) */
	private static final Predicate<String> LEGACY_REFRESH_EXPIRY =
			h -> h.startsWith("refresh_token=;") && h.contains("Path=/api/auth;") && h.contains("Max-Age=0");
	private static final String LOCKED_MESSAGE = "로그인을 5번 잘못 시도해서 10분 동안 로그인이 제한돼요. "
			+ "10분 뒤에 다시 시도하거나, 비밀번호를 재설정하면 바로 로그인할 수 있어요.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	AuthMailSender mailSender;

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
			// 액세스 토큰 만료 후 화면 이동 때 Next.js 서버가 재발급할 수 있도록 모든 경로로 보낸다 (FIX-002)
			assertThat(refresh.getPath()).isEqualTo("/");
			assertThat(refresh.isHttpOnly()).isTrue();
			assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(LEGACY_REFRESH_EXPIRY);

			mockMvc.perform(get("/api/users/me").cookie(access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("USER"));
		}

		@Test
		@DisplayName("전체 흐름: 코드 요청 → 메일의 코드 확인 → 토큰으로 가입. 이메일은 소문자로 저장된다 (FIX-008)")
		void fullVerificationFlow() throws Exception {
			String email = newEmail();
			mockMvc.perform(json(post("/api/auth/email-verification/request"),
					"{\"email\":\"" + email.toUpperCase() + "\"}"))
				.andExpect(status().isAccepted());
			ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
			verify(mailSender).sendVerificationCode(eq(email), code.capture());

			String confirmed = mockMvc.perform(json(post("/api/auth/email-verification/confirm"),
					"{\"email\":\"" + email + "\",\"code\":\"" + code.getValue() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registered").value(false))
				.andReturn().getResponse().getContentAsString();
			String token = com.jayway.jsonpath.JsonPath.read(confirmed, "$.verificationToken");

			Cookie access = mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody(token, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getCookie(AuthCookies.ACCESS_TOKEN);
			mockMvc.perform(get("/api/users/me").cookie(access))
				.andExpect(jsonPath("$.email").value(email));
		}

		@Test
		@DisplayName("가입용 토큰은 한 번만 쓸 수 있다")
		void tokenIsSingleUse() throws Exception {
			String token = VerifiedEmails.issueToken(jdbc, newEmail());
			mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody(token, PASSWORD)))
				.andExpect(status().isCreated());
			mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody(token, PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		}

		@Test
		@DisplayName("만료됐거나 없는 토큰이면 INVALID_VERIFICATION_TOKEN, 토큰을 안 보내면 입력값 오류")
		void invalidToken() throws Exception {
			String email = newEmail();
			String token = VerifiedEmails.issueToken(jdbc, email);
			jdbc.update("UPDATE email_verifications SET token_expires_at = now() - interval '1 second' WHERE email = ?", email);
			mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody(token, PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
			mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody("no-such-token", PASSWORD)))
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
			mockMvc.perform(json(post("/api/auth/signup"),
					"{\"password\":\"" + PASSWORD + "\",\"agreements\":{\"terms\":true,\"privacy\":true,\"skinData\":true}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("verificationToken"));
		}

		@Test
		@DisplayName("가입이 실패하면(약관 미동의) 토큰은 쓰이지 않아 다시 가입할 수 있다")
		void failedSignupKeepsToken() throws Exception {
			String token = VerifiedEmails.issueToken(jdbc, newEmail());
			mockMvc.perform(json(post("/api/auth/signup"), """
					{"verificationToken":"%s","password":"%s","agreements":{"terms":true,"privacy":true,"skinData":false}}
					""".formatted(token, PASSWORD)))
				.andExpect(status().isBadRequest());
			mockMvc.perform(json(post("/api/auth/signup"), VerifiedEmails.signupBody(token, PASSWORD)))
				.andExpect(status().isCreated());
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

			MockHttpServletResponse response = mockMvc
				.perform(post("/api/auth/logout").header("X-Requested-With", "fetch").cookie(refreshCookie))
				.andExpect(status().isNoContent())
				.andReturn()
				.getResponse();
			// 새 경로(/)와 예전 경로(/api/auth)의 Refresh Token 쿠키를 모두 지운다
			assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
				.anyMatch(h -> h.startsWith("refresh_token=;") && h.contains("Path=/;") && h.contains("Max-Age=0"))
				.anyMatch(LEGACY_REFRESH_EXPIRY);
			refresh(refreshCookie).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("재발급할 때도 예전 경로(/api/auth)의 Refresh Token 쿠키를 지운다")
		void refreshExpiresLegacyCookie() throws Exception {
			Cookie refreshCookie = signup(newEmail()).getResponse().getCookie(AuthCookies.REFRESH_TOKEN);

			MockHttpServletResponse rotated = refresh(refreshCookie).andExpect(status().isOk()).andReturn().getResponse();
			assertThat(rotated.getCookie(AuthCookies.REFRESH_TOKEN).getPath()).isEqualTo("/");
			assertThat(rotated.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(LEGACY_REFRESH_EXPIRY);
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
			verify(mailSender).sendPasswordResetLink(eq(email), link.capture());
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
		@DisplayName("로그인이 제한된 상태에서 비밀번호를 재설정하면 바로 로그인할 수 있다 (잠금 문구의 약속, FIX-006)")
		void resetUnlocksLogin() throws Exception {
			String email = newEmail();
			signup(email);
			for (int i = 0; i < 5; i++) {
				mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "wrong12345")));
			}
			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, PASSWORD)))
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

			mockMvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted());
			ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
			verify(mailSender).sendPasswordResetLink(eq(email), link.capture());
			String token = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());
			mockMvc.perform(json(post("/api/auth/password-reset/confirm"),
					"{\"token\":\"" + token + "\",\"newPassword\":\"newpass456\"}"))
				.andExpect(status().isNoContent());

			mockMvc.perform(json(post("/api/auth/login"), loginBody(email, "newpass456")))
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("가입되지 않은 이메일도 같은 응답을 받고, 메일은 보내지 않는다")
		void sameResponseForUnknownEmail() throws Exception {
			mockMvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"" + newEmail() + "\"}"))
				.andExpect(status().isAccepted());
			verify(mailSender, never()).sendPasswordResetLink(any(), any());
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

	/** 이메일 인증을 마친 상태로 만들어 가입 본문을 만든다 */
	private String signupBody(String email, String password, boolean agreeSkinData) {
		return """
				{"verificationToken":"%s","password":"%s",
				 "agreements":{"terms":true,"privacy":true,"skinData":%s}}"""
			.formatted(VerifiedEmails.issueToken(jdbc, email), password, agreeSkinData);
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}
}
