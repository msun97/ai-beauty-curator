package com.aibeauty.api.global.ratelimit;

/**
 * 요청 횟수 제한 종류 (FIX-009). 한도·시간 구간은 app.rate-limit.rules.{propertyName}에서 정한다.
 */
public enum RateLimitRule {

	/** 로그인: IP 기준 (무차별 대입 방지) */
	LOGIN_IP("login-ip"),
	/** 가입, 인증 코드 확인, 비밀번호 재설정 완료: IP 기준 (코드·토큰 대입 방지) */
	AUTH_IP("auth-ip"),
	/** 인증 코드 요청, 비밀번호 재설정 요청: IP 기준 (메일 폭탄 방지) */
	MAIL_IP("mail-ip"),
	/** 인증 코드 요청, 비밀번호 재설정 요청: 받는 이메일 기준 (한 사람에게 메일이 몰리는 것 방지) */
	MAIL_EMAIL("mail-email");

	private final String propertyName;

	RateLimitRule(String propertyName) {
		this.propertyName = propertyName;
	}

	public String propertyName() {
		return propertyName;
	}
}
