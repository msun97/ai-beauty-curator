package com.aibeauty.api.global.error;

/**
 * 예상 가능한 비즈니스 오류. {@link GlobalExceptionHandler}가 공통 오류 응답으로 변환한다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
