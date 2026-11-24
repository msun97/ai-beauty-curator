package com.aibeauty.api.product.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aibeauty.api.global.api.ResPageDTO;

/**
 * 상품 카드 목록 조회. 인기순은 행동 로그 집계가 필요해 JPA 대신 SQL로 조회한다 (ADR-002).
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
	 * @param categoryId null이면 전체
	 * @param ingredientId null이 아니면 이 성분이 들어간 상품만
	 */
	public record Condition(Long categoryId, Long ingredientId) {

		public static Condition all() {
			return new Condition(null, null);
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
		if (condition.categoryId() != null) {
			sql.append("AND p.category_id = :categoryId\n");
			params.addValue("categoryId", condition.categoryId());
		}
		if (condition.ingredientId() != null) {
			sql.append("AND EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id AND pi.ingredient_id = :ingredientId)\n");
			params.addValue("ingredientId", condition.ingredientId());
		}
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

	private static String orderBy(ProductSort sort) {
		return switch (sort) {
			case POPULAR -> "COALESCE(pop.action_count, 0) DESC";
			case LATEST -> "p.created_at DESC";
			case PRICE_ASC -> "p.price ASC NULLS LAST";
			case PRICE_DESC -> "p.price DESC NULLS LAST";
		};
	}
}
