package com.aibeauty.api.product.application;

/** 궁합을 판정할 수 없는 이유 (FR-PRD-03) */
public enum DataIssue {
	/** 전성분이 없거나 일부만 확보됨 */
	INGREDIENTS_INCOMPLETE,
	/** 표준 성분에 매칭되지 않은 전성분이 있음 */
	UNMATCHED_INGREDIENTS
}
