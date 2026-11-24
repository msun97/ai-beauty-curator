package com.aibeauty.api.product.api.dto;

/** 검색 필터용 브랜드 (FR-SRC-02). productCount는 노출 중인 상품 수 */
public record ResBrandDTO(long id, String name, long productCount) {
}
