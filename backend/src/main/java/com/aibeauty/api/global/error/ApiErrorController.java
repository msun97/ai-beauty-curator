package com.aibeauty.api.global.error;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 컨트롤러에 도달하기 전에 발생한 오류(필터 예외, 잘못된 URL 차단 등)를 컨테이너가 /error로 넘길 때도
 * Spring 기본 오류 JSON 대신 공통 오류 형식으로 응답한다.
 */
@RestController
public class ApiErrorController implements ErrorController {

	@RequestMapping("${server.error.path:/error}")
	public ResponseEntity<ResErrorDTO> error(HttpServletRequest request) {
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		CommonErrorCode code = CommonErrorCode.fromStatus(status instanceof Integer value ? value : 500);
		return ResponseEntity.status(code.status()).body(ResErrorDTO.of(code));
	}
}
