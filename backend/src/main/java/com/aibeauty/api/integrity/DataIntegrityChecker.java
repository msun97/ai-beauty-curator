package com.aibeauty.api.integrity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.ingredient.domain.IngredientNames;
import com.aibeauty.api.integrity.IntegrityReport.CheckResult;
import com.aibeauty.api.integrity.IntegrityReport.Severity;

/**
 * 적재된 상품·성분 데이터의 정합성을 점검한다.
 * DB 제약(FK, CHECK, UNIQUE)으로 막을 수 없는 규칙만 검사한다. 읽기만 하고 아무것도 고치지 않는다.
 */
public class DataIntegrityChecker {

	private static final Logger log = LoggerFactory.getLogger(DataIntegrityChecker.class);
	static final int SAMPLE_LIMIT = 5;
	/** 이 기간보다 오래 전에 확인한 상품 정보는 가격·전성분이 바뀌었을 수 있다 */
	static final int STALE_DAYS = 180;
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/** SQL 하나로 위반 행을 찾는 검사. sql은 위반 행마다 설명 한 줄(label)을 돌려준다 */
	private record SqlCheck(String code, Severity severity, String description, String sql) {
	}

	private static final String PRODUCT_LABEL = "b.name || ' ' || p.name || ' (id=' || p.id || ')'";

	private static final List<SqlCheck> SQL_CHECKS = List.of(
			new SqlCheck("PRODUCT_WITHOUT_SOURCE_URL", Severity.ERROR,
					"출처 URL이 없는 상품 (상품과 출처 모두 비어 있음)", """
					SELECT %s AS label FROM products p JOIN brands b ON b.id = p.brand_id
					JOIN data_sources s ON s.id = p.data_source_id
					WHERE coalesce(p.source_url, s.url) IS NULL OR btrim(coalesce(p.source_url, s.url)) = ''
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_COLLECTED_IN_FUTURE", Severity.ERROR,
					"수집일이 오늘 이후인 상품", """
					SELECT %s || ' ' || p.source_collected_at AS label FROM products p JOIN brands b ON b.id = p.brand_id
					WHERE p.source_collected_at > :today
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_COMPLETE_WITHOUT_INGREDIENTS", Severity.ERROR,
					"전성분 완료(COMPLETE)로 표시됐지만 전성분이 없는 상품", """
					SELECT %s AS label FROM products p JOIN brands b ON b.id = p.brand_id
					WHERE p.ingredient_status = 'COMPLETE'
					  AND NOT EXISTS (SELECT 1 FROM product_ingredients pi WHERE pi.product_id = p.id)
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_INGREDIENT_ORDER_GAP", Severity.ERROR,
					"전성분 표시 순서가 1부터 빈틈없이 이어지지 않는 상품", """
					SELECT %s || ' 줄 수 ' || count(*) || ', 마지막 순서 ' || max(pi.display_order) AS label
					FROM product_ingredients pi JOIN products p ON p.id = pi.product_id JOIN brands b ON b.id = p.brand_id
					GROUP BY p.id, b.name, p.name
					HAVING max(pi.display_order) <> count(*)
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_UNMATCHED_INGREDIENTS", Severity.WARN,
					"표준 성분에 매칭되지 않은 전성분 줄 (화면에 데이터 부족으로 표시됨)", """
					SELECT %s || ': ' || pi.raw_name AS label
					FROM product_ingredients pi JOIN products p ON p.id = pi.product_id JOIN brands b ON b.id = p.brand_id
					WHERE pi.ingredient_id IS NULL
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_DUPLICATE_INGREDIENT", Severity.WARN,
					"한 상품의 전성분에 같은 표준 성분이 두 번 이상 매칭됨 (입력 중복 또는 별칭 오류 가능)", """
					SELECT %s || ': ' || i.standard_name || ' ' || count(*) || '회' AS label
					FROM product_ingredients pi JOIN products p ON p.id = pi.product_id JOIN brands b ON b.id = p.brand_id
					JOIN ingredients i ON i.id = pi.ingredient_id
					GROUP BY p.id, b.name, p.name, i.standard_name
					HAVING count(*) > 1
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_WITHOUT_PRICE", Severity.WARN,
					"노출 중이지만 가격이 없는 상품 (가격순 정렬에서 맨 뒤)", """
					SELECT %s AS label FROM products p JOIN brands b ON b.id = p.brand_id
					WHERE p.status = 'ACTIVE' AND p.price IS NULL
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_STALE", Severity.WARN,
					"마지막 확인 후 " + STALE_DAYS + "일이 지난 노출 상품", """
					SELECT %s || ' ' || p.source_collected_at AS label FROM products p JOIN brands b ON b.id = p.brand_id
					WHERE p.status = 'ACTIVE' AND p.source_collected_at < :staleBefore
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("PRODUCT_WITHOUT_TAGS", Severity.WARN,
					"피부 타입이나 피부 고민 태그가 없는 노출 상품 (추천 점수 계산에 쓰임)", """
					SELECT %s AS label FROM products p JOIN brands b ON b.id = p.brand_id
					WHERE p.status = 'ACTIVE'
					  AND (NOT EXISTS (SELECT 1 FROM product_skin_types t WHERE t.product_id = p.id)
					       OR NOT EXISTS (SELECT 1 FROM product_concern_tags t WHERE t.product_id = p.id))
					""".formatted(PRODUCT_LABEL)),
			new SqlCheck("SOURCE_WITHOUT_USAGE_TERMS", Severity.WARN,
					"이용 조건이 기록되지 않은 출처 (NFR-LEG-02)", """
					SELECT s.name AS label FROM data_sources s
					WHERE (s.usage_terms IS NULL OR btrim(s.usage_terms) = '')
					  AND (EXISTS (SELECT 1 FROM products p WHERE p.data_source_id = s.id)
					       OR EXISTS (SELECT 1 FROM reviews r WHERE r.data_source_id = s.id))
					"""),
			new SqlCheck("INGREDIENT_WITHOUT_SOURCE", Severity.WARN,
					"출처가 기록되지 않은 성분", """
					SELECT i.standard_name || ' (id=' || i.id || ')' AS label FROM ingredients i
					WHERE i.source_name IS NULL OR btrim(i.source_name) = ''
					"""),
			new SqlCheck("BRAND_WITHOUT_PRODUCTS", Severity.WARN,
					"상품이 하나도 없는 브랜드", """
					SELECT b.name AS label FROM brands b
					WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.brand_id = b.id)
					"""),
			new SqlCheck("CATEGORY_WITHOUT_PRODUCTS", Severity.WARN,
					"노출 상품이 없는 카테고리 (목록 화면이 비어 보임)", """
					SELECT c.name || ' (' || c.code || ')' AS label FROM categories c
					WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.id AND p.status = 'ACTIVE')
					"""));

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public DataIntegrityChecker(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IntegrityReport check() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), SERVICE_ZONE);
		List<CheckResult> results = new ArrayList<>();
		for (SqlCheck check : SQL_CHECKS) {
			String sql = check.sql()
					.replace(":today", "DATE '" + today + "'")
					.replace(":staleBefore", "DATE '" + today.minusDays(STALE_DAYS) + "'");
			List<String> labels = jdbc.queryForList(sql, String.class);
			results.add(result(check.code(), check.severity(), check.description(), labels));
		}
		results.addAll(aliasChecks());
		IntegrityReport report = new IntegrityReport(List.copyOf(results));
		log(report);
		return report;
	}

	/**
	 * 별칭 규칙 검사. 비교용 값 계산(NFKC 등)이 Java 규칙({@link IngredientNames})이라 DB에서 읽어 Java로 비교한다.
	 */
	private List<CheckResult> aliasChecks() {
		Map<String, Long> standardOwners = new HashMap<>();
		Map<Long, String> standardNames = new HashMap<>();
		Map<String, Integer> standardCounts = new HashMap<>();
		jdbc.query("SELECT id, standard_name FROM ingredients", rs -> {
			String normalized = IngredientNames.normalize(rs.getString("standard_name"));
			standardOwners.put(normalized, rs.getLong("id"));
			standardNames.put(rs.getLong("id"), rs.getString("standard_name"));
			standardCounts.merge(normalized, 1, Integer::sum);
		});

		List<String> mismatched = new ArrayList<>();
		List<String> shadowing = new ArrayList<>();
		jdbc.query("SELECT ingredient_id, alias, normalized_alias FROM ingredient_aliases", rs -> {
			String alias = rs.getString("alias");
			String stored = rs.getString("normalized_alias");
			long ingredientId = rs.getLong("ingredient_id");
			if (!IngredientNames.normalize(alias).equals(stored)) {
				mismatched.add(alias + " → 저장값 " + stored + ", 규칙 적용값 " + IngredientNames.normalize(alias));
			}
			// 표준명이 비교용 값으로 겹치는 성분이 여럿이면 어느 쪽인지 정할 수 없으므로 판단하지 않는다
			Long owner = standardOwners.get(stored);
			if (owner != null && owner != ingredientId && standardCounts.get(stored) == 1) {
				shadowing.add(alias + " → " + standardNames.get(ingredientId) + " (표준명이 같은 성분: "
						+ standardNames.get(owner) + ")");
			}
		});

		return List.of(
				result("ALIAS_NORMALIZATION_MISMATCH", Severity.ERROR,
						"별칭의 비교용 값이 현재 이름 비교 규칙과 다름 (검색·매칭에서 찾지 못함)", mismatched),
				result("ALIAS_SHADOWS_STANDARD_NAME", Severity.ERROR,
						"별칭이 다른 성분의 표준명과 같은 값인데 엉뚱한 성분을 가리킴 (잘못된 매칭)", shadowing));
	}

	private static CheckResult result(String code, Severity severity, String description, List<String> labels) {
		return new CheckResult(code, severity, description, labels.size(),
				labels.stream().limit(SAMPLE_LIMIT).toList());
	}

	private static void log(IntegrityReport report) {
		log.info("데이터 정합성 검사: 오류 {}종, 경고 {}종 (검사 {}개)", report.errorCount(), report.warningCount(),
				report.results().size());
		for (CheckResult r : report.results()) {
			if (r.passed()) {
				log.info("  [통과] {} - {}", r.code(), r.description());
				continue;
			}
			String line = "  [{}] {} - {}: {}건 예) {}";
			if (r.severity() == Severity.ERROR) {
				log.error(line, r.severity(), r.code(), r.description(), r.count(), r.samples());
			} else {
				log.warn(line, r.severity(), r.code(), r.description(), r.count(), r.samples());
			}
		}
	}
}
