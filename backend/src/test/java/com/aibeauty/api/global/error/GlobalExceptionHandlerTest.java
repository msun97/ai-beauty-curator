package com.aibeauty.api.global.error;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.global.trace.TraceIdFilter;

import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

@Import({ TestcontainersConfiguration.class, GlobalExceptionHandlerTest.ErrorTestController.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("비즈니스 예외는 오류 코드와 사용자 문구로 변환되고, detail은 응답에 노출하지 않는다")
	void businessException() throws Exception {
		perform(get("/api/test-errors/business"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value(CommonErrorCode.NOT_FOUND.message()))
			.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	@DisplayName("응답 본문과 헤더에 같은 추적 ID가 담긴다")
	void traceIdInBodyAndHeader() throws Exception {
		perform(get("/api/test-errors/business").header(TraceIdFilter.HEADER, "client-trace-123"))
			.andExpect(header().string(TraceIdFilter.HEADER, "client-trace-123"))
			.andExpect(jsonPath("$.traceId").value("client-trace-123"));
	}

	@Test
	@DisplayName("안전하지 않은 형식의 추적 ID는 버리고 새로 만든다")
	void rejectsUnsafeTraceId() throws Exception {
		perform(get("/api/test-errors/business").header(TraceIdFilter.HEADER, "bad id\n<script>"))
			.andExpect(header().string(TraceIdFilter.HEADER, matchesPattern("^[a-f0-9]{16}$")))
			.andExpect(jsonPath("$.traceId").value(matchesPattern("^[a-f0-9]{16}$")));
	}

	@Test
	@DisplayName("요청 본문 검증 실패는 필드별 오류를 반환한다")
	void bodyValidation() throws Exception {
		perform(post("/api/test-errors/body").contentType(MediaType.APPLICATION_JSON)
				.header("X-Requested-With", "fetch").content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("이름을 입력해 주세요."));
	}

	@Test
	@DisplayName("JSON 문법 오류는 INVALID_INPUT으로 처리한다")
	void unreadableBody() throws Exception {
		perform(post("/api/test-errors/body").contentType(MediaType.APPLICATION_JSON)
				.header("X-Requested-With", "fetch").content("{broken"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("요청 파라미터 검증 실패는 파라미터 이름으로 필드 오류를 반환한다")
	void parameterValidation() throws Exception {
		perform(get("/api/test-errors/param").param("size", "999"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
	}

	@Test
	@DisplayName("필수 파라미터가 없으면 해당 파라미터를 알려준다")
	void missingParameter() throws Exception {
		perform(get("/api/test-errors/param"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
	}

	@Test
	@DisplayName("타입이 맞지 않는 값은 INVALID_TYPE으로 처리한다")
	void typeMismatch() throws Exception {
		perform(get("/api/test-errors/items/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TYPE"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type은 415를 반환한다")
	void unsupportedMediaType() throws Exception {
		perform(post("/api/test-errors/body").contentType(MediaType.TEXT_PLAIN)
				.header("X-Requested-With", "fetch").content("hello"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("지원하지 않는 HTTP 메서드는 405와 Allow 헤더를 반환한다")
	void methodNotAllowed() throws Exception {
		perform(post("/api/test-errors/business").header("X-Requested-With", "fetch"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(header().string("Allow", "GET"))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("없는 경로는 404를 반환한다")
	void notFound() throws Exception {
		perform(get("/api/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("데이터 충돌은 409로 처리한다")
	void conflict() throws Exception {
		perform(get("/api/test-errors/conflict"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500으로 처리하고 내부 메시지를 노출하지 않는다")
	void unexpected() throws Exception {
		perform(get("/api/test-errors/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_ERROR.message()));
	}

	@Test
	@DisplayName("컨트롤러 밖 오류(/error)도 공통 형식으로 응답한다")
	void errorController() throws Exception {
		mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder builder)
			throws Exception {
		return mockMvc.perform(builder.with(jwt()));
	}

	@RestController
	@RequestMapping("/api/test-errors")
	static class ErrorTestController {

		record ReqTestBodyDTO(@NotBlank(message = "이름을 입력해 주세요.") String name) {
		}

		@GetMapping("/business")
		void business() {
			throw new BusinessException(CommonErrorCode.NOT_FOUND, "내부 추적용 정보 productId=42");
		}

		@PostMapping("/body")
		void body(@Valid @RequestBody ReqTestBodyDTO body) {
		}

		@GetMapping("/param")
		void param(@RequestParam @Max(100) int size) {
		}

		@GetMapping("/items/{id}")
		void item(@PathVariable Long id) {
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("DB password=secret");
		}
	}
}
