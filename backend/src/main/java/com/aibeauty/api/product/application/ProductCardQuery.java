package com.aibeauty.api.product.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aibeauty.api.global.api.ResPageDTO;

/**
 * 상품 카드 목록 조회. 인기순 집계와 검색 조건 조합이 필요해 JPA 대신 SQL로 조회한다 (ADR-002).
 * 노출 중(ACTIVE)인 상품만 돌려준다.
 */
@Repository
public class ProductCardQuery {

	static final Duration POPULAR_WINDOW = Duration.ofDays(30);

	/** 전성분이 완전하고, 1개 이상이며, 모두 표준 성분에 매칭된 상품 */
	private static final String DATA_SUFFICIENT = """
			(p.ingredient_status = 'COMPLETE'
			 AND EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id)
			 AND NOT EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id AND pi.ingredient_id IS NULL))
			""";

	/**
	 * 목록 조건. 값이 null인 조건은 적용하지 않는다.
	 *
	 * @param categoryId 카테고리
	 * @param ingredientId 이 성분이 들어간 상품만
	 * @param keyword 상품명·브랜드명 부분 일치 (띄어쓰기 무시)
	 * @param keywordIngredientId 검색어가 가리키는 표준 성분. keyword와 OR로 묶여, 이 성분이 든 상품도 결과에 넣는다
	 * @param brandId 브랜드
	 * @param minPrice 최소 가격(포함). 가격 미상 상품은 제외된다
	 * @param maxPrice 최대 가격(포함). 가격 미상 상품은 제외된다
	 * @param avoidingUserId 이 회원의 기피 성분이 든 상품을 뺀다
	 */
	public record Condition(Long categoryId, Long ingredientId, String keyword, Long keywordIngredientId,
			Long brandId, Integer minPrice, Integer maxPrice, Long avoidingUserId) {

		public static Condition all() {
			return new Condition(null, null, null, null, null, null, null, null);
		}

		public Condition withCategory(Long categoryId) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					avoidingUserId);
		}

		public Condition withIngredient(Long ingredientId) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					avoidingUserId);
		}

		public Condition withKeyword(String keyword, Long keywordIngredientId) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					avoidingUserId);
		}

		public Condition withBrand(Long brandId) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					avoidingUserId);
		}

		public Condition withPriceRange(Integer minPrice, Integer maxPrice) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					avoidingUserId);
		}

		public Condition excludingAvoidedOf(Long userId) {
			return new Condition(categoryId, ingredientId, keyword, keywordIngredientId, brandId, minPrice, maxPrice,
					userId);
		}
	}

	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	public ProductCardQuery(NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public ResPageDTO<ProductCard> find(Condition condition, ProductSort sort, int page, int size) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("limit", size + 1)
				.addValue("offset", (long) page * size);

		StringBuilder sql = new StringBuilder("""
				SELECT p.id, p.name, b.name AS brand_name, c.code AS category_code, p.image_url, p.price, p.volume,
				""").append(DATA_SUFFICIENT).append(" AS data_sufficient\n").append("""
				FROM products p
				JOIN brands b ON b.id = p.brand_id
				JOIN categories c ON c.id = p.category_id
				""");
		if (sort == ProductSort.POPULAR) {
			sql.append("""
					LEFT JOIN (SELECT product_id, count(*) AS action_count
					           FROM product_actions
					           WHERE product_id IS NOT NULL AND occurred_at >= :since
					           GROUP BY product_id) pop ON pop.product_id = p.id
					""");
			params.addValue("since", Timestamp.from(clock.instant().minus(POPULAR_WINDOW)));
		}
		sql.append("WHERE p.status = 'ACTIVE'\n");
		appendFilters(sql, params, condition);
		sql.append("ORDER BY ").append(orderBy(sort)).append(", p.id DESC\n");
		sql.append("LIMIT :limit OFFSET :offset");

		List<ProductCard> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> new ProductCard(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("brand_name"),
				rs.getString("category_code"),
				rs.getString("image_url"),
				rs.getObject("price", Integer.class),
				rs.getString("volume"),
				rs.getBoolean("data_sufficient")));

		boolean hasNext = rows.size() > size;
		return new ResPageDTO<>(hasNext ? rows.subList(0, size) : rows, page, size, hasNext);
	}

	private static void appendFilters(StringBuilder sql, MapSqlParameterSource params, Condition condition) {
		if (condition.categoryId() != null) {
			sql.append("AND p.category_id = :categoryId\n");
			params.addValue("categoryId", condition.categoryId());
		}
		if (condition.ingredientId() != null) {
			sql.append("AND EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id AND pi.ingredient_id = :ingredientId)\n");
			params.addValue("ingredientId", condition.ingredientId());
		}
		if (condition.keyword() != null) {
			sql.append("""
					AND (p.name ILIKE :keyword ESCAPE '\\' OR b.name ILIKE :keyword ESCAPE '\\'
					     OR replace(lower(p.name), ' ', '') LIKE :compactKeyword ESCAPE '\\'
					     OR replace(lower(b.name), ' ', '') LIKE :compactKeyword ESCAPE '\\'
					""");
			params.addValue("keyword", "%" + escapeLike(condition.keyword().strip()) + "%");
			params.addValue("compactKeyword", "%" + escapeLike(compact(condition.keyword())) + "%");
			if (condition.keywordIngredientId() != null) {
				sql.append("     OR EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id AND pi.ingredient_id = :keywordIngredientId)\n");
				params.addValue("keywordIngredientId", condition.keywordIngredientId());
			}
			sql.append(")\n");
		}
		if (condition.brandId() != null) {
			sql.append("AND p.brand_id = :brandId\n");
			params.addValue("brandId", condition.brandId());
		}
		if (condition.minPrice() != null) {
			sql.append("AND p.price >= :minPrice\n");
			params.addValue("minPrice", condition.minPrice());
		}
		if (condition.maxPrice() != null) {
			sql.append("AND p.price <= :maxPrice\n");
			params.addValue("maxPrice", condition.maxPrice());
		}
		if (condition.avoidingUserId() != null) {
			sql.append("""
					AND NOT EXISTS (SELECT 1 FROM product_ingredients pi
					                JOIN user_ingredient_preferences up ON up.ingredient_id = pi.ingredient_id
					                WHERE pi.product_id = p.id AND up.user_id = :avoidingUserId
					                  AND up.preference_type = 'AVOIDED')
					""");
			params.addValue("avoidingUserId", condition.avoidingUserId());
		}
	}

	private static String orderBy(ProductSort sort) {
		return switch (sort) {
			case POPULAR -> "COALESCE(pop.action_count, 0) DESC";
			case LATEST -> "p.created_at DESC";
			case PRICE_ASC -> "p.price ASC NULLS LAST";
			case PRICE_DESC -> "p.price DESC NULLS LAST";
		};
	}

	/** 띄어쓰기를 무시한 비교용 검색어. "독도토너"로 "1025 독도 토너"를 찾는다 */
	static String compact(String keyword) {
		return keyword.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	/** LIKE 패턴의 특수문자(%, _, \)를 글자 그대로 찾도록 이스케이프한다 */
	public static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
