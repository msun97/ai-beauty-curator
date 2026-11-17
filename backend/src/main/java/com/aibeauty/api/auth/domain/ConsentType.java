package com.aibeauty.api.auth.domain;

public enum ConsentType {
	/** 서비스 이용약관 */
	TERMS,
	/** 개인정보 수집·이용 */
	PRIVACY,
	/** 피부 정보 수집·이용 (별도 동의 - NFR-PRIV-01) */
	SKIN_DATA
}
