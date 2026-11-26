package com.aibeauty.api.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.TestcontainersConfiguration;
import com.aibeauty.api.integrity.IntegrityReport.CheckResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataIntegrityCheckerTest {

	/** 검사 기준일 2026-11-26 (KST) */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC);

	@Autowired
	JdbcTemplate jdbc;

	DataIntegrityChecker checker;
	long sourceId;
	long brandId;
	long water;
	long glycerin;

	@BeforeEach
	void setUp() {
		checker = new DataIntegrityChecker(jdbc, CLOCK);
		sourceId = jdbc.queryForObject("""
				INSERT INTO data_sources (name, source_type, url, usage_terms)
				VALUES ('테스트 출처', 'MANUAL', 'https://source.example.com', '사실 정보 인용') RETURNING id
				""", Long.class);
		brandId = jdbc.queryForObject("INSERT INTO brands (name) VALUES ('테스트브랜드') RETURNING id", Long.class);
		water = ingredient("정제수");
		glycerin = ingredient("글리세린");
		alias(water, "정제수", "정제수");
		alias(water, "Water", "water");
		alias(glycerin, "글리세린", "글리세린");
	}

	@Test
	@DisplayName("정상 데이터는 오류가 없다")
	void cleanData() {
		long productId = product("정상 토너", 15000, "COMPLETE", "2026-11-24", "https://example.com/1");
		line(productId, 1, water, "정제수");
		line(productId, 2, glycerin, "글리세린");
		tags(productId);

		IntegrityReport report = checker.check();

		assertThat(report.hasErrors()).isFalse();
		assertThat(report.results()).filteredOn(r -> !r.code().equals("CATEGORY_WITHOUT_PRODUCTS"))
				.allMatch(CheckResult::passed);
	}

	@Test
	@DisplayName("출처 URL 누락, 미래 수집일, 전성분 없는 COMPLETE, 순서 빈틈을 오류로 잡는다")
	void productErrors() {
		jdbc.update("UPDATE data_sources SET url = NULL WHERE id = ?", sourceId);
		long noUrl = product("URL 없음", 1000, "INCOMPLETE", "2026-11-24", null);
		long future = product("미래", 1000, "INCOMPLETE", "2026-12-01", "https://example.com/2");
		long empty = product("전성분 없음", 1000, "COMPLETE", "2026-11-24", "https://example.com/3");
		long gap = product("순서 빈틈", 1000, "COMPLETE", "2026-11-24", "https://example.com/4");
		line(gap, 1, water, "정제수");
		line(gap, 3, glycerin, "글리세린");

		IntegrityReport report = checker.check();

		assertThat(report.hasErrors()).isTrue();
		assertThat(report.result("PRODUCT_WITHOUT_SOURCE_URL").samples()).singleElement()
				.asString().contains("URL 없음").contains("id=" + noUrl);
		assertThat(report.result("PRODUCT_COLLECTED_IN_FUTURE").samples()).singleElement()
				.asString().contains("id=" + future);
		assertThat(report.result("PRODUCT_COMPLETE_WITHOUT_INGREDIENTS").samples()).singleElement()
				.asString().contains("id=" + empty);
		assertThat(report.result("PRODUCT_INGREDIENT_ORDER_GAP").samples()).singleElement()
				.asString().contains("id=" + gap);
	}

	@Test
	@DisplayName("비교용 값이 규칙과 다른 별칭과, 다른 성분의 표준명을 가로챈 별칭을 오류로 잡는다")
	void aliasErrors() {
		long sparkling = ingredient("탄산수");
		alias(sparkling, "AQUA", "AQUA");
		alias(sparkling, "정제 수", "정제수 ");

		jdbc.update("DELETE FROM ingredient_aliases WHERE ingredient_id = ? AND normalized_alias = '정제수'", water);
		alias(sparkling, "정제수", "정제수");

		IntegrityReport report = checker.check();

		assertThat(report.result("ALIAS_NORMALIZATION_MISMATCH").count()).isEqualTo(2);
		assertThat(report.result("ALIAS_SHADOWS_STANDARD_NAME").samples()).singleElement()
				.asString().contains("탄산수").contains("정제수");
	}

	@Test
	@DisplayName("미매칭·중복 성분, 가격·태그 누락, 오래된 정보, 출처 이용 조건 누락은 경고로 보고한다")
	void warnings() {
		jdbc.update("UPDATE data_sources SET usage_terms = NULL WHERE id = ?", sourceId);
		long productId = product("경고 상품", null, "COMPLETE", "2026-01-01", "https://example.com/5");
		line(productId, 1, water, "정제수");
		line(productId, 2, null, "미확인추출물");
		line(productId, 3, water, "Water");
		jdbc.update("INSERT INTO brands (name) VALUES ('빈 브랜드')");
		ingredient("출처없는성분");
		jdbc.update("UPDATE ingredients SET source_name = NULL WHERE standard_name = '출처없는성분'");

		IntegrityReport report = checker.check();

		assertThat(report.hasErrors()).isFalse();
		assertThat(report.result("PRODUCT_UNMATCHED_INGREDIENTS").samples()).singleElement()
				.asString().contains("미확인추출물");
		assertThat(report.result("PRODUCT_DUPLICATE_INGREDIENT").samples()).singleElement()
				.asString().contains("정제수 2회");
		assertThat(report.result("PRODUCT_WITHOUT_PRICE").count()).isEqualTo(1);
		assertThat(report.result("PRODUCT_STALE").count()).isEqualTo(1);
		assertThat(report.result("PRODUCT_WITHOUT_TAGS").count()).isEqualTo(1);
		assertThat(report.result("SOURCE_WITHOUT_USAGE_TERMS").samples()).containsExactly("테스트 출처");
		assertThat(report.result("BRAND_WITHOUT_PRODUCTS").samples()).containsExactly("빈 브랜드");
		assertThat(report.result("INGREDIENT_WITHOUT_SOURCE").samples()).singleElement()
				.asString().contains("출처없는성분");
		assertThat(report.result("CATEGORY_WITHOUT_PRODUCTS").count()).isEqualTo(5);
	}

	// --- 테스트 데이터 ---------------------------------------------------------

	private long ingredient(String name) {
		return jdbc.queryForObject("""
				INSERT INTO ingredients (standard_name, source_name, created_at, updated_at)
				VALUES (?, '식약처 화장품 원료성분정보', now(), now()) RETURNING id
				""", Long.class, name);
	}

	private void alias(long ingredientId, String alias, String normalized) {
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, ?, ?)",
				ingredientId, alias, normalized);
	}

	private long product(String name, Integer price, String ingredientStatus, String collectedAt, String sourceUrl) {
		return jdbc.queryForObject("""
				INSERT INTO products (brand_id, category_id, data_source_id, name, price, ingredient_status, source_url,
				                      source_collected_at, created_at, updated_at)
				VALUES (?, (SELECT id FROM categories WHERE code = 'TONER'), ?, ?, ?, ?, ?, CAST(? AS date), now(), now())
				RETURNING id
				""", Long.class, brandId, sourceId, name, price, ingredientStatus, sourceUrl, collectedAt);
	}

	private void line(long productId, int order, Long ingredientId, String rawName) {
		jdbc.update("INSERT INTO product_ingredients VALUES (?, ?, ?, ?)", productId, order, ingredientId, rawName);
	}

	private void tags(long productId) {
		jdbc.update("INSERT INTO product_skin_types VALUES (?, 'DRY')", productId);
		jdbc.update("INSERT INTO product_concern_tags SELECT ?, id FROM skin_concerns WHERE code = 'DRYNESS'", productId);
	}
}
