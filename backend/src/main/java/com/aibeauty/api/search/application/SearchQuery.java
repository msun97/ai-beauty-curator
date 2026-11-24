package com.aibeauty.api.search.application;

import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aibeauty.api.ingredient.domain.IngredientNames;
import com.aibeauty.api.product.application.ProductCardQuery;

/**
 * 성분 검색과 자동완성 조회 (FR-SRC-01, FR-SRC-04).
 * 표준명과 별칭의 비교용 값(띄어쓰기·기호·대소문자 무시)으로 부분 일치를 찾는다.
 */
@Repository
public class SearchQuery {

	/**
	 * @param matchedAlias 표준명이 아닌 별칭으로 찾았을 때 그 별칭. 표준명으로 찾았으면 null
	 * @param productCount 이 성분이 들어간 노출 중인 상품 수
	 */
	public record IngredientHit(long id, String standardName, String matchedAlias, long productCount) {
	}

	public record ProductSuggestion(long id, String name, String brandName) {
	}

	public record BrandSuggestion(long id, String name) {
	}

	public record IngredientSuggestion(long id, String standardName) {
	}

	private static final String PRODUCT_COUNT = """
			(SELECT count(DISTINCT pi.product_id) FROM product_ingredients pi
			 JOIN products p ON p.id = pi.product_id
			 WHERE pi.ingredient_id = h.id AND p.status = 'ACTIVE')
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public SearchQuery(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 성분 부분 검색. 정렬: 정확히 가리키는 성분 → 상품에 많이 쓰인 성분 → 표준명이 검색어로 시작 → 짧은 이름.
	 *
	 * @param exactId 검색어가 정확히 가리키는 성분(없으면 null)
	 */
	public List<IngredientHit> ingredients(String keyword, Long exactId, int limit) {
		String normalized = IngredientNames.normalize(keyword);
		if (normalized.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("pattern", "%" + ProductCardQuery.escapeLike(normalized) + "%")
				.addValue("prefix", ProductCardQuery.escapeLike(normalized) + "%")
				.addValue("exactId", exactId == null ? -1L : exactId)
				.addValue("limit", limit);
		String sql = """
				WITH hits AS (
				    SELECT DISTINCT ON (i.id) i.id, i.standard_name, a.alias AS matched_alias,
				           a.normalized_alias LIKE :prefix ESCAPE '\\' AS prefix_match
				    FROM ingredient_aliases a
				    JOIN ingredients i ON i.id = a.ingredient_id
				    WHERE a.normalized_alias LIKE :pattern ESCAPE '\\'
				    ORDER BY i.id, (a.normalized_alias LIKE :prefix ESCAPE '\\') DESC, length(a.alias)
				)
				SELECT h.id, h.standard_name, h.matched_alias, h.prefix_match, %s AS product_count
				FROM hits h
				ORDER BY (h.id = :exactId) DESC, product_count DESC, h.prefix_match DESC,
				         length(h.standard_name), h.standard_name
				LIMIT :limit
				""".formatted(PRODUCT_COUNT);
		return jdbc.query(sql, params, (rs, rowNum) -> {
			String standardName = rs.getString("standard_name");
			// 표준명 자체가 검색어를 포함하면 별칭 안내가 필요 없다
			String matchedAlias = IngredientNames.normalize(standardName).contains(normalized)
					? null
					: rs.getString("matched_alias");
			return new IngredientHit(rs.getLong("id"), standardName, matchedAlias, rs.getLong("product_count"));
		});
	}

	public List<ProductSuggestion> productSuggestions(String keyword, int limit) {
		MapSqlParameterSource params = keywordParams(keyword, limit);
		return jdbc.query("""
				SELECT p.id, p.name, b.name AS brand_name
				FROM products p JOIN brands b ON b.id = p.brand_id
				WHERE p.status = 'ACTIVE'
				  AND (replace(lower(p.name), ' ', '') LIKE :pattern ESCAPE '\\'
				       OR replace(lower(b.name), ' ', '') LIKE :pattern ESCAPE '\\')
				ORDER BY (replace(lower(p.name), ' ', '') LIKE :prefix ESCAPE '\\') DESC, length(p.name), p.id
				LIMIT :limit
				""", params, (rs, rowNum) -> new ProductSuggestion(rs.getLong("id"), rs.getString("name"),
				rs.getString("brand_name")));
	}

	/** 노출 중인 상품이 있는 브랜드만 */
	public List<BrandSuggestion> brandSuggestions(String keyword, int limit) {
		MapSqlParameterSource params = keywordParams(keyword, limit);
		return jdbc.query("""
				SELECT b.id, b.name FROM brands b
				WHERE replace(lower(b.name), ' ', '') LIKE :pattern ESCAPE '\\'
				  AND EXISTS (SELECT 1 FROM products p WHERE p.brand_id = b.id AND p.status = 'ACTIVE')
				ORDER BY (replace(lower(b.name), ' ', '') LIKE :prefix ESCAPE '\\') DESC, length(b.name), b.name
				LIMIT :limit
				""", params, (rs, rowNum) -> new BrandSuggestion(rs.getLong("id"), rs.getString("name")));
	}

	public List<IngredientSuggestion> ingredientSuggestions(String keyword, int limit) {
		return ingredients(keyword, null, limit).stream()
				.map(hit -> new IngredientSuggestion(hit.id(), hit.standardName()))
				.toList();
	}

	private static MapSqlParameterSource keywordParams(String keyword, int limit) {
		String compact = ProductCardQuery.escapeLike(keyword.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
		return new MapSqlParameterSource()
				.addValue("pattern", "%" + compact + "%")
				.addValue("prefix", compact + "%")
				.addValue("limit", limit);
	}
}
