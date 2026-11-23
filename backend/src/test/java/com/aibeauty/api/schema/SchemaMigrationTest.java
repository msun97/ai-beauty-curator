package com.aibeauty.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.TestcontainersConfiguration;

/**
 * Flyway 마이그레이션(V3~V7)이 적용된 스키마에서 ERD의 핵심 규칙이 DB 제약으로 지켜지는지 확인한다.
 * 테스트마다 트랜잭션을 롤백한다. PostgreSQL은 제약 위반 후 트랜잭션이 중단되므로 위반 검증은 각 테스트의 마지막에 둔다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaMigrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("기준 코드(피부 고민 8개, 카테고리 6개)가 들어 있다")
	void referenceCodesSeeded() {
		assertThat(count("skin_concerns")).isEqualTo(8);
		assertThat(count("categories")).isEqualTo(6);
	}

	@Test
	@DisplayName("성분명 부분 검색에 pg_trgm 유사도 검색을 쓸 수 있다")
	void trigramSearch() {
		long ingredientId = insertIngredient("나이아신아마이드");

		Long found = jdbc.queryForObject(
				"SELECT id FROM ingredients WHERE standard_name % ? ORDER BY similarity(standard_name, ?) DESC LIMIT 1",
				Long.class, "나이아신아마이", "나이아신아마이");

		assertThat(found).isEqualTo(ingredientId);
	}

	@Test
	@DisplayName("같은 브랜드에 같은 이름의 상품은 중복 등록할 수 없다 (적재 시 갱신 기준)")
	void productNaturalKey() {
		long brandId = insertBrand("테스트브랜드");
		long sourceId = insertSource("테스트 출처");
		insertProduct(brandId, sourceId, "수분 크림");

		assertThatThrownBy(() -> insertProduct(brandId, sourceId, "수분 크림"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("한 성분을 선호와 기피에 동시에 등록할 수 없다")
	void ingredientPreferenceIsExclusive() {
		long userId = insertUser();
		long ingredientId = insertIngredient("향료");
		jdbc.update("INSERT INTO user_ingredient_preferences VALUES (?, ?, 'AVOIDED', now())", userId, ingredientId);

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO user_ingredient_preferences VALUES (?, ?, 'PREFERRED', now())", userId, ingredientId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("회원을 삭제하면 피부 프로필·북마크는 지워지고 행동 로그는 남는다")
	void userDeletionKeepsAnonymousActions() {
		long userId = insertUser();
		UUID actorKey = jdbc.queryForObject("SELECT actor_key FROM users WHERE id = ?", UUID.class, userId);
		long productId = insertProduct(insertBrand("브랜드A"), insertSource("출처A"), "토너");

		jdbc.update("INSERT INTO user_skin_profiles VALUES (?, 'DRY', true, NULL, NULL, now(), now())", userId);
		jdbc.update("INSERT INTO bookmarks VALUES (?, ?, now())", userId, productId);
		jdbc.update("INSERT INTO product_actions (actor_key, product_id, action_type, occurred_at) VALUES (?, ?, 'VIEW', now())",
				actorKey, productId);

		jdbc.update("DELETE FROM users WHERE id = ?", userId);

		assertThat(count("user_skin_profiles")).isZero();
		assertThat(count("bookmarks")).isZero();
		assertThat(count("product_actions")).isEqualTo(1);
	}

	@Test
	@DisplayName("트렌드 키워드는 상품과 성분 중 정확히 하나에 속해야 한다")
	void trendKeywordNeedsExactlyOneTarget() {
		long productId = insertProduct(insertBrand("브랜드B"), insertSource("출처B"), "세럼");
		long ingredientId = insertIngredient("레티놀");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO trend_keywords (product_id, ingredient_id, group_name, keywords, updated_at)
				VALUES (?, ?, '둘 다 지정', ARRAY['세럼'], now())
				""", productId, ingredientId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("보정용 기준 키워드는 특정 대상 없이 등록한다")
	void anchorKeywordHasNoTarget() {
		jdbc.update("""
				INSERT INTO trend_keywords (group_name, keywords, is_anchor, updated_at)
				VALUES ('기준: 화장품', ARRAY['화장품'], true, now())
				""");
		long productId = insertProduct(insertBrand("브랜드C"), insertSource("출처C"), "선크림");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO trend_keywords (product_id, group_name, keywords, is_anchor, updated_at)
				VALUES (?, '대상 있는 기준', ARRAY['선크림'], true, now())
				""", productId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("상품마다 현재 리뷰 요약은 하나만 둘 수 있다")
	void onlyOneCurrentSummaryPerProduct() {
		long productId = insertProduct(insertBrand("브랜드D"), insertSource("출처D"), "클렌저");
		String sql = """
				INSERT INTO product_review_summaries
				    (product_id, prompt_version, model_name, analyzed_review_count, status, is_current, analyzed_at)
				VALUES (?, ?, 'model', 10, 'SUCCESS', ?, now())
				""";
		jdbc.update(sql, productId, "v1", false);
		jdbc.update(sql, productId, "v2", true);

		assertThatThrownBy(() -> jdbc.update(sql, productId, "v3", true))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("동시에 활성화된 추천 규칙 버전은 하나뿐이다")
	void onlyOneActiveRuleVersion() {
		String sql = "INSERT INTO rule_versions (version, weights, thresholds, is_active, created_at) VALUES (?, '{}', '{}', true, now())";
		jdbc.update(sql, "v1.0");

		assertThatThrownBy(() -> jdbc.update(sql, "v1.1"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- 테스트 데이터 ---------------------------------------------------------

	private long count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private long insertUser() {
		return jdbc.queryForObject("""
				INSERT INTO users (email, password_hash, nickname, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'hash', '테스터', ?, now(), now(), now()) RETURNING id
				""", Long.class, "schema-" + UUID.randomUUID() + "@example.com", UUID.randomUUID());
	}

	private long insertIngredient(String name) {
		return jdbc.queryForObject(
				"INSERT INTO ingredients (standard_name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
				Long.class, name);
	}

	private long insertBrand(String name) {
		return jdbc.queryForObject("INSERT INTO brands (name) VALUES (?) RETURNING id", Long.class, name);
	}

	private long insertSource(String name) {
		return jdbc.queryForObject(
				"INSERT INTO data_sources (name, source_type) VALUES (?, 'MANUAL') RETURNING id", Long.class, name);
	}

	private long insertProduct(long brandId, long sourceId, String name) {
		return jdbc.queryForObject("""
				INSERT INTO products (brand_id, category_id, data_source_id, name, source_collected_at, created_at, updated_at)
				VALUES (?, (SELECT id FROM categories WHERE code = 'CREAM'), ?, ?, current_date, now(), now())
				RETURNING id
				""", Long.class, brandId, sourceId, name);
	}
}
