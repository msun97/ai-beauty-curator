package com.aibeauty.api.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 규약. 공통 오류는 {@link CommonErrorCode}, 도메인 오류는 각 도메인 패키지의 enum으로 정의한다.
 *
 * <ul>
 *   <li>code: 클라이언트가 분기 처리에 쓰는 고정 문자열 (enum 이름). 한 번 공개한 코드는 바꾸지 않는다.</li>
 *   <li>message: 사용자에게 그대로 보여줄 수 있는 문구. 내부 정보(예외 메시지, SQL 등)를 담지 않는다.</li>
 * </ul>
 */
public interface ErrorCode {

	HttpStatus status();

	String message();

	/** enum이 구현하면 name()이 그대로 코드가 된다. */
	String name();

	default String code() {
		return name();
	}
}
