package com.aibeauty.api.global.error;

/**
 * 예상 가능한 비즈니스 오류. {@link GlobalExceptionHandler}가 공통 오류 응답으로 변환한다.
 *
 * <p>사용자에게는 오류 코드의 문구({@link ErrorCode#message()})만 보여주고,
 * {@code detail}은 원인 추적용으로 로그에만 남긴다. 둘은 섞지 않는다.
 * <ul>
 *   <li>문구 그대로: {@code new BusinessException(CommonErrorCode.NOT_FOUND)}</li>
 *   <li>문구 그대로 + 로그용 상세: {@code new BusinessException(CommonErrorCode.NOT_FOUND, "productId=42")}</li>
 *   <li>문구의 빈칸을 값으로 채움: {@code BusinessException.withArgs(AuthErrorCode.LOGIN_LOCKED, 5, "10분")}</li>
 * </ul>
 */
public class BusinessException extends RuntimeException {

	private static final Object[] NO_ARGS = {};

	private final ErrorCode errorCode;
	private final Object[] messageArgs;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.message(), NO_ARGS);
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		this(errorCode, detail, NO_ARGS);
	}

	private BusinessException(ErrorCode errorCode, String detail, Object[] messageArgs) {
		super(detail);
		this.errorCode = errorCode;
		this.messageArgs = messageArgs;
	}

	/**
	 * 문구의 빈칸({0}, {1}, …)을 채울 값과 함께 만든다.
	 * 예: {@code BusinessException.withArgs(AuthErrorCode.LOGIN_LOCKED, 5, "10분")}
	 */
	public static BusinessException withArgs(ErrorCode errorCode, Object... messageArgs) {
		Object[] args = messageArgs == null ? NO_ARGS : messageArgs.clone();
		return new BusinessException(errorCode, errorCode.message(args), args);
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	/** 사용자에게 보여줄 문구 (빈칸을 채운 결과) */
	public String userMessage() {
		return errorCode.message(messageArgs);
	}
}
