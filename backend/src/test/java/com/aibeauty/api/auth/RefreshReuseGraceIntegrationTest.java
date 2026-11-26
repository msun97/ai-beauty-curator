package com.aibeauty.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.security.AuthCookies;

import jakarta.servlet.http.Cookie;

/**
 * 동시 재발급 유예 (FIX-003). 이미 사용된 Refresh Token이라도 사용된 지 유예 시간 이내면
 * 동시 요청으로 보고 정상 재발급하고, 유예 시간이 지나면 지금처럼 탈취로 판단한다.
 * 테스트 시간을 줄이려고 유예를 1초로 둔다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.jwt.refresh-reuse-grace=1s")
class RefreshReuseGraceIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("유예 시간 안에 같은 토큰으로 다시 재발급하면 둘 다 성공하고, 새로 받은 토큰도 계속 쓸 수 있다")
	void reuseWithinGraceSucceeds() throws Exception {
		Cookie first = signupRefreshCookie();

		Cookie second = refresh(first).getCookie(AuthCookies.REFRESH_TOKEN);
		MockHttpServletResponse again = refresh(first);

		assertThat(again.getStatus()).isEqualTo(200);
		Cookie third = again.getCookie(AuthCookies.REFRESH_TOKEN);
		assertThat(third.getValue()).isNotEqualTo(second.getValue());
		// 묶음이 폐기되지 않았으므로 두 응답의 새 토큰 모두 이어서 쓸 수 있다
		assertThat(refresh(second).getStatus()).isEqualTo(200);
		assertThat(refresh(third).getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("같은 토큰으로 동시에 재발급해도 모두 성공한다 (화면 미리 불러오기·여러 탭)")
	void concurrentRefreshSucceeds() throws Exception {
		Cookie token = signupRefreshCookie();
		int requests = 3;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return refresh(token).getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("유예 시간이 지난 뒤 다시 쓰면 지금처럼 탈취로 보고 묶음 전체를 폐기한다")
	void reuseAfterGraceIsRejected() throws Exception {
		Cookie first = signupRefreshCookie();
		Cookie second = refresh(first).getCookie(AuthCookies.REFRESH_TOKEN);

		Thread.sleep(1_500);

		assertThat(refresh(first).getStatus()).isEqualTo(401);
		// 같은 묶음의 최신 토큰도 폐기됨
		assertThat(refresh(second).getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("로그아웃으로 폐기된 토큰은 유예 시간과 관계없이 거절한다")
	void revokedTokenIsRejectedEvenWithinGrace() throws Exception {
		Cookie token = signupRefreshCookie();
		mockMvc.perform(post("/api/auth/logout").header("X-Requested-With", "fetch").cookie(token));

		assertThat(refresh(token).getStatus()).isEqualTo(401);
	}

	private Cookie signupRefreshCookie() throws Exception {
		String body = """
				{"email":"grace-%s@example.com","password":"password123","agreements":{"terms":true,"privacy":true,"skinData":true}}
				""".formatted(UUID.randomUUID());
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.header("X-Requested-With", "fetch")
				.content(body))
			.andReturn()
			.getResponse()
			.getCookie(AuthCookies.REFRESH_TOKEN);
	}

	private MockHttpServletResponse refresh(Cookie refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "fetch").cookie(refreshToken))
			.andReturn()
			.getResponse();
	}
}
