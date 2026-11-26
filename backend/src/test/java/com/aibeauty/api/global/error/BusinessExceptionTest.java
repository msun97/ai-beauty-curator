package com.aibeauty.api.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

	enum TestErrorCode implements ErrorCode {
		LOCKED("{0}번 틀려서 {1} 동안 막혀요. {1} 뒤에 다시 시도해 주세요."),
		QUOTED("'따옴표'가 있어도 {0} 그대로");

		private final String message;

		TestErrorCode(String message) {
			this.message = message;
		}

		@Override
		public HttpStatus status() {
			return HttpStatus.BAD_REQUEST;
		}

		@Override
		public String message() {
			return message;
		}
	}

	@Test
	@DisplayName("빈칸을 순서대로 채우고, 같은 빈칸이 여러 번 나오면 모두 채운다")
	void fillsPlaceholders() {
		BusinessException e = BusinessException.withArgs(TestErrorCode.LOCKED, 3, "1분");
		assertThat(e.userMessage()).isEqualTo("3번 틀려서 1분 동안 막혀요. 1분 뒤에 다시 시도해 주세요.");
	}

	@Test
	@DisplayName("작은따옴표 같은 문자는 특수문자로 해석하지 않는다")
	void keepsQuotes() {
		assertThat(BusinessException.withArgs(TestErrorCode.QUOTED, "값").userMessage())
			.isEqualTo("'따옴표'가 있어도 값 그대로");
	}

	@Test
	@DisplayName("값 없이 만들면 기본 문구 그대로 (기존 사용처 영향 없음)")
	void withoutArgsKeepsMessage() {
		assertThat(new BusinessException(CommonErrorCode.NOT_FOUND).userMessage())
			.isEqualTo(CommonErrorCode.NOT_FOUND.message());
		assertThat(new BusinessException(CommonErrorCode.NOT_FOUND, "productId=42").userMessage())
			.isEqualTo(CommonErrorCode.NOT_FOUND.message());
	}
}
