package com.aibeauty.api.global.error;

/**
 * 예상 가능한 비즈니스 오류. {@link GlobalExceptionHandler}가 공통 오류 응답으로 변환한다.
 *
 * <p>사용자에게는 항상 {@link ErrorCode#message()}만 보여주고, {@code detail}은 원인 추적용으로 로그에만 남긴다.
 * 예: {@code new BusinessException(CommonErrorCode.NOT_FOUND, "productId=42")}
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
