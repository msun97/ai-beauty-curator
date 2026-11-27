package com.aibeauty.api.global.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.mail.AuthMailSender;

/**
 * 인증 API의 IP 기준 요청 횟수 제한 (FIX-009).
 * 프록시 1개 뒤에 있는 것으로 설정하고, 요청마다 X-Forwarded-For로 다른 IP를 흉내 낸다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.rate-limit.trusted-proxies=1", "app.rate-limit.rules.login-ip.limit=3",
		"app.rate-limit.rules.auth-ip.limit=3", "app.rate-limit.rules.mail-ip.limit=2" })
class RateLimitFilterIntegrationTest {

	private static final AtomicInteger NEXT = new AtomicInteger((int) (System.nanoTime() % 200));

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthMailSender mailSender;

	/** 테스트마다 겹치지 않는 IP */
	private static String newIp() {
		int n = NEXT.incrementAndGet();
		return "198.18." + (n / 250 % 250) + "." + (n % 250 + 1);
	}

	private static String newEmail() {
		return "limit-" + UUID.randomUUID() + "@example.com";
	}

	@Test
	@DisplayName("로그인: 같은 IP로 3번까지는 평소 응답, 4번째는 429 TOO_MANY_REQUESTS + Retry-After")
	void loginLimit() throws Exception {
		String ip = newIp();
		for (int i = 0; i < 3; i++) {
			login(ip, newEmail()).andExpect(status().isUnauthorized());
		}
		login(ip, newEmail())
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
			.andExpect(jsonPath("$.message").value("요청이 너무 많아요. 잠시 후 다시 시도해 주세요."))
			.andExpect(header().exists("Retry-After"));
		// 다른 IP는 영향 없음
		login(newIp(), newEmail()).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("앞에 꾸며 넣은 X-Forwarded-For 값을 바꿔도 같은 IP로 센다 (우회 불가)")
	void spoofedHeaderDoesNotBypass() throws Exception {
		String ip = newIp();
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(json("/api/auth/login", loginBody(newEmail()))
				.header("X-Forwarded-For", "6.6.6." + i + ", " + ip));
		}
		mockMvc.perform(json("/api/auth/login", loginBody(newEmail())).header("X-Forwarded-For", "7.7.7.7, " + ip))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("메일 요청: 인증 코드와 비밀번호 재설정 요청을 합쳐 같은 IP 2번까지, 가입 여부와 관계없이 같은 429")
	void mailIpLimit() throws Exception {
		String ip = newIp();
		perform(ip, "/api/auth/email-verification/request", "{\"email\":\"" + newEmail() + "\"}")
			.andExpect(status().isAccepted());
		perform(ip, "/api/auth/password-reset/request", "{\"email\":\"" + newEmail() + "\"}")
			.andExpect(status().isAccepted());
		perform(ip, "/api/auth/email-verification/request", "{\"email\":\"" + newEmail() + "\"}")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
	}

	@Test
	@DisplayName("코드 확인: 같은 IP 3번까지 (여러 이메일에 코드를 대입하는 것 방지)")
	void confirmLimit() throws Exception {
		String ip = newIp();
		for (int i = 0; i < 3; i++) {
			perform(ip, "/api/auth/email-verification/confirm",
					"{\"email\":\"" + newEmail() + "\",\"code\":\"123456\"}")
				.andExpect(status().isBadRequest());
		}
		perform(ip, "/api/auth/email-verification/confirm", "{\"email\":\"" + newEmail() + "\",\"code\":\"123456\"}")
			.andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("CSRF 헤더가 없어 거절된 요청은 세지 않는다")
	void csrfRejectedNotCounted() throws Exception {
		String ip = newIp();
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", ip).content(loginBody(newEmail())))
				.andExpect(status().isForbidden());
		}
		login(ip, newEmail()).andExpect(status().isUnauthorized());
	}

	private ResultActions login(String ip, String email) throws Exception {
		return perform(ip, "/api/auth/login", loginBody(email));
	}

	private static String loginBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"wrong12345\"}";
	}

	private ResultActions perform(String ip, String path, String body) throws Exception {
		return mockMvc.perform(json(path, body).header("X-Forwarded-For", ip));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(String path,
			String body) {
		return post(path).contentType(MediaType.APPLICATION_JSON).header("X-Requested-With", "fetch").content(body);
	}
}
