package com.aibeauty.api.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 규약. 공통 오류는 {@link CommonErrorCode}, 도메인 오류는 각 도메인 패키지의 enum으로 정의한다.
 *
 * <ul>
 *   <li>code: 클라이언트가 분기 처리에 쓰는 고정 문자열 (enum 이름). 한 번 공개한 코드는 바꾸지 않는다.</li>
 *   <li>message: 사용자에게 그대로 보여줄 수 있는 문구. 내부 정보(예외 메시지, SQL 등)를 담지 않는다.
 *       설정값처럼 상황마다 달라지는 값은 {0}, {1} 같은 빈칸으로 두고 오류를 낼 때 채운다
 *       ({@link BusinessException#withArgs}). 숫자를 문구에 직접 쓰면 설정이 바뀔 때 문구만 남아 어긋난다.</li>
 *   <li>field: 특정 입력 항목 때문에 생기는 오류면 그 요청 필드명. 응답의 fieldErrors에 담겨
 *       화면이 해당 입력란 아래에 문구를 붙일 수 있다. 특정 항목과 무관하면 null.</li>
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

	/**
	 * 문구의 빈칸({0}, {1}, …)을 순서대로 채운 문구. 값이 없으면 문구를 그대로 돌려준다.
	 * MessageFormat 대신 단순 치환을 써서 문구 속 작은따옴표 등이 특수문자로 해석되지 않게 한다.
	 */
	default String message(Object... args) {
		String text = message();
		if (args == null) {
			return text;
		}
		for (int i = 0; i < args.length; i++) {
			text = text.replace("{" + i + "}", String.valueOf(args[i]));
		}
		return text;
	}

	/** 오류와 관련된 요청 필드명. 없으면 null (폼 전체 오류로 표시된다). */
	default String field() {
		return null;
	}
}
