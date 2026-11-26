package com.aibeauty.api.integrity;

import java.util.List;

/** 데이터 정합성 검사 결과 */
public record IntegrityReport(List<CheckResult> results) {

	public enum Severity {
		/** 화면에 틀린 정보가 나가거나 출처 규정(NFR-LEG-02)을 어기는 상태. 적재를 실패로 본다 */
		ERROR,
		/** 데이터가 부족하거나 손볼 필요가 있지만 틀린 정보는 아닌 상태 */
		WARN
	}

	/**
	 * @param code 검사 식별자
	 * @param description 검사 내용
	 * @param count 위반 건수
	 * @param samples 위반 예시 (최대 {@link DataIntegrityChecker#SAMPLE_LIMIT}개)
	 */
	public record CheckResult(String code, Severity severity, String description, long count, List<String> samples) {

		public boolean passed() {
			return count == 0;
		}
	}

	public long errorCount() {
		return results.stream().filter(r -> r.severity() == Severity.ERROR && !r.passed()).count();
	}

	public long warningCount() {
		return results.stream().filter(r -> r.severity() == Severity.WARN && !r.passed()).count();
	}

	public boolean hasErrors() {
		return errorCount() > 0;
	}

	public CheckResult result(String code) {
		return results.stream().filter(r -> r.code().equals(code)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("없는 검사: " + code));
	}
}
