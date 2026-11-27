package com.aibeauty.api.global.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.aibeauty.api.global.ratelimit.RateLimitExceededException;
import com.aibeauty.api.global.error.ResErrorDTO.FieldError;

/**
 * 컨트롤러에서 발생한 예외를 공통 오류 응답으로 변환한다.
 *
 * <p>로그 기준: 사용자 입력·상태로 생기는 4xx는 DEBUG/INFO, 데이터 충돌은 WARN, 예상하지 못한 5xx는 ERROR(스택 포함).
 * 응답에는 예외 메시지나 내부 정보를 담지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ResErrorDTO> handleBusiness(BusinessException e) {
		ErrorCode code = e.errorCode();
		if (code.status().is5xxServerError()) {
			log.error("비즈니스 오류: code={}, detail={}", code.code(), e.getMessage(), e);
		}
		else {
			log.info("비즈니스 오류: code={}, detail={}", code.code(), e.getMessage());
		}
		// 특정 입력 항목 때문에 생긴 오류면 검증 오류와 같은 모양(fieldErrors)으로 내려 화면이 한 가지 방식으로 처리하게 한다
		String message = e.userMessage();
		List<FieldError> fieldErrors = code.field() == null
				? List.of()
				: List.of(new FieldError(code.field(), message));
		ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
		if (e instanceof RateLimitExceededException limited) {
			// 언제 다시 시도할 수 있는지 알려준다 (FIX-009)
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds()));
		}
		return response.body(ResErrorDTO.of(code, message, fieldErrors));
	}

	/** @RequestBody 검증 실패 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ResErrorDTO> handleBodyValidation(MethodArgumentNotValidException e) {
		List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return clientError(CommonErrorCode.INVALID_INPUT, fieldErrors, e);
	}

	/** @RequestParam, @PathVariable 등 메서드 파라미터 검증 실패 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ResErrorDTO> handleParameterValidation(HandlerMethodValidationException e) {
		List<FieldError> fieldErrors = e.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new FieldError(result.getMethodParameter().getParameterName(),
						error.getDefaultMessage())))
			.toList();
		return clientError(CommonErrorCode.INVALID_INPUT, fieldErrors, e);
	}

	/** 숫자 자리에 문자가 오는 등 타입 변환 실패 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ResErrorDTO> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return clientError(CommonErrorCode.INVALID_TYPE,
				List.of(new FieldError(e.getName(), CommonErrorCode.INVALID_TYPE.message())), e);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ResErrorDTO> handleMissingParameter(MissingServletRequestParameterException e) {
		return clientError(CommonErrorCode.INVALID_INPUT,
				List.of(new FieldError(e.getParameterName(), "필수 값이에요.")), e);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ResErrorDTO> handleMissingHeader(MissingRequestHeaderException e) {
		return clientError(CommonErrorCode.INVALID_INPUT, List.of(), e);
	}

	/** JSON 문법 오류, 본문 누락, enum 값 오류 등 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ResErrorDTO> handleUnreadable(HttpMessageNotReadableException e) {
		return clientError(CommonErrorCode.INVALID_INPUT, List.of(), e);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ResErrorDTO> handleMediaType(HttpMediaTypeNotSupportedException e) {
		return clientError(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of(), e);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ResErrorDTO> handleNotFound(NoResourceFoundException e) {
		return clientError(CommonErrorCode.NOT_FOUND, List.of(), e);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ResErrorDTO> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		log.debug("클라이언트 오류: {}", e.getMessage());
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status());
		if (e.getSupportedHttpMethods() != null) {
			builder.header(HttpHeaders.ALLOW, e.getSupportedHttpMethods().stream()
				.map(Object::toString).reduce((a, b) -> a + ", " + b).orElse(""));
		}
		return builder.body(ResErrorDTO.of(CommonErrorCode.METHOD_NOT_ALLOWED));
	}

	/** 동시 수정(낙관적 락) 또는 유니크 제약 위반. 원인 파악을 위해 WARN으로 남긴다. */
	@ExceptionHandler({ OptimisticLockingFailureException.class, DataIntegrityViolationException.class })
	public ResponseEntity<ResErrorDTO> handleConflict(RuntimeException e) {
		log.warn("데이터 충돌: {}", e.getClass().getSimpleName(), e);
		return respond(CommonErrorCode.CONFLICT, List.of());
	}

	/** 메서드 보안(@PreAuthorize 등)에서 발생하는 인증·인가 예외 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ResErrorDTO> handleAccessDenied(AccessDeniedException e) {
		return clientError(CommonErrorCode.FORBIDDEN, List.of(), e);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ResErrorDTO> handleAuthentication(AuthenticationException e) {
		return clientError(CommonErrorCode.UNAUTHORIZED, List.of(), e);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResErrorDTO> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return respond(CommonErrorCode.INTERNAL_ERROR, List.of());
	}

	private ResponseEntity<ResErrorDTO> clientError(ErrorCode code, List<FieldError> fieldErrors, Exception e) {
		log.debug("클라이언트 오류: code={}, exception={}, message={}", code.code(), e.getClass().getSimpleName(),
				e.getMessage());
		return respond(code, fieldErrors);
	}

	private ResponseEntity<ResErrorDTO> respond(ErrorCode code, List<FieldError> fieldErrors) {
		return ResponseEntity.status(code.status()).body(ResErrorDTO.of(code, fieldErrors));
	}
}
