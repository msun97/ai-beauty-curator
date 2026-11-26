package com.aibeauty.api.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.TestcontainersConfiguration;

/**
 * 상품·성분 조회 API. 모두 비로그인 상태로 호출한다.
 * 테스트마다 데이터를 넣고 트랜잭션을 롤백한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	long sourceId;
	long brandId;
	long niacinamide;
	long glycerin;

	@BeforeEach
	void setUp() {
		sourceId = jdbc.queryForObject("""
				INSERT INTO data_sources (name, source_type, url) VALUES ('브랜드 공식몰', 'MANUAL', 'https://brand.example.com')
				RETURNING id""", Long.class);
		brandId = jdbc.queryForObject("INSERT INTO brands (name) VALUES ('테스트브랜드') RETURNING id", Long.class);
		niacinamide = insertIngredient("나이아신아마이드", "Niacinamide", "비타민 B3의 일종이다.");
		glycerin = insertIngredient("글리세린", "Glycerin", null);
		insertAlias(niacinamide, "나이아신아마이드", "나이아신아마이드");
		insertAlias(niacinamide, "Niacinamide", "niacinamide");
		insertAlias(niacinamide, "비타민B3", "비타민b3");
	}

	@Nested
	@DisplayName("상품 목록")
	class ProductList {

		@Test
		@DisplayName("카테고리 목록을 표시 순서대로 준다")
		void categories() throws Exception {
			mockMvc.perform(get("/api/categories"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(6)))
					.andExpect(jsonPath("$[0].code").value("TONER"))
					.andExpect(jsonPath("$[0].name").value("토너"));
		}

		@Test
		@DisplayName("카테고리로 거르고 가격 낮은순으로 정렬하며, 가격 미상은 맨 뒤, 숨김 상품은 빠진다")
		void filterAndSortByPrice() throws Exception {
			insertProduct("SERUM", "세럼 비쌈", 30000, "ACTIVE", "COMPLETE");
			insertProduct("SERUM", "세럼 가격미상", null, "ACTIVE", "COMPLETE");
			insertProduct("SERUM", "세럼 쌈", 10000, "ACTIVE", "COMPLETE");
			insertProduct("SERUM", "세럼 숨김", 5000, "HIDDEN", "COMPLETE");
			insertProduct("CREAM", "크림", 1000, "ACTIVE", "COMPLETE");

			mockMvc.perform(get("/api/products").param("category", "SERUM").param("sort", "PRICE_ASC"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].name").value(contains("세럼 쌈", "세럼 비쌈", "세럼 가격미상")))
					.andExpect(jsonPath("$.items[0].brandName").value("테스트브랜드"))
					.andExpect(jsonPath("$.items[0].categoryCode").value("SERUM"))
					.andExpect(jsonPath("$.hasNext").value(false));
		}

		@Test
		@DisplayName("다음 페이지가 있으면 hasNext가 true다")
		void pagination() throws Exception {
			for (int i = 1; i <= 3; i++) {
				insertProduct("TONER", "토너 " + i, 1000 * i, "ACTIVE", "COMPLETE");
			}

			mockMvc.perform(get("/api/products").param("sort", "PRICE_DESC").param("size", "2"))
					.andExpect(jsonPath("$.items[*].name").value(contains("토너 3", "토너 2")))
					.andExpect(jsonPath("$.hasNext").value(true));
			mockMvc.perform(get("/api/products").param("sort", "PRICE_DESC").param("size", "2").param("page", "1"))
					.andExpect(jsonPath("$.items[*].name").value(contains("토너 1")))
					.andExpect(jsonPath("$.hasNext").value(false));
		}

		@Test
		@DisplayName("인기순은 최근 30일 행동 수가 많은 상품이 먼저 온다")
		void popular() throws Exception {
			long quiet = insertProduct("CREAM", "조용한 크림", 1000, "ACTIVE", "COMPLETE");
			long hot = insertProduct("CREAM", "인기 크림", 1000, "ACTIVE", "COMPLETE");
			long old = insertProduct("CREAM", "예전 인기 크림", 1000, "ACTIVE", "COMPLETE");
			insertAction(hot, "now()");
			insertAction(hot, "now()");
			insertAction(quiet, "now()");
			for (int i = 0; i < 5; i++) {
				insertAction(old, "now() - interval '40 days'");
			}

			mockMvc.perform(get("/api/products").param("category", "CREAM").param("sort", "POPULAR"))
					.andExpect(jsonPath("$.items[*].name").value(contains("인기 크림", "조용한 크림", "예전 인기 크림")));
		}

		@Test
		@DisplayName("전성분이 불완전하거나 매칭 안 된 성분이 있으면 데이터 부족으로 표시한다")
		void dataSufficiency() throws Exception {
			long complete = insertProduct("TONER", "완전", 3000, "ACTIVE", "COMPLETE");
			long unmatched = insertProduct("TONER", "미매칭", 2000, "ACTIVE", "COMPLETE");
			insertProduct("TONER", "전성분 없음", 1000, "ACTIVE", "INCOMPLETE");
			insertLine(complete, 1, glycerin, "글리세린");
			insertLine(unmatched, 1, glycerin, "글리세린");
			insertLine(unmatched, 2, null, "알수없는성분");

			mockMvc.perform(get("/api/products").param("sort", "PRICE_DESC"))
					.andExpect(jsonPath("$.items[*].dataSufficient").value(contains(true, false, false)));
		}

		@Test
		@DisplayName("잘못된 카테고리·정렬·페이지 크기는 입력 오류다")
		void invalidParameters() throws Exception {
			mockMvc.perform(get("/api/products").param("category", "NOPE"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
			mockMvc.perform(get("/api/products").param("sort", "RANDOM"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(get("/api/products").param("size", "51"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("상품 상세")
	class ProductDetail {

		@Test
		@DisplayName("전성분을 표시 순서대로, 매칭된 표준 성분과 함께 준다")
		void detail() throws Exception {
			long productId = insertProduct("SERUM", "나이아신 세럼", 25000, "ACTIVE", "COMPLETE");
			insertLine(productId, 2, niacinamide, "나이아신아마이드");
			insertLine(productId, 1, glycerin, "글리세린");
			jdbc.update("INSERT INTO product_skin_types VALUES (?, 'OILY'), (?, 'DRY')", productId, productId);
			jdbc.update("INSERT INTO product_concern_tags SELECT ?, id FROM skin_concerns WHERE code IN ('PORE', 'DULLNESS')",
					productId);

			mockMvc.perform(get("/api/products/{id}", productId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("나이아신 세럼"))
					.andExpect(jsonPath("$.brand.name").value("테스트브랜드"))
					.andExpect(jsonPath("$.category.code").value("SERUM"))
					.andExpect(jsonPath("$.ingredients[*].rawName").value(contains("글리세린", "나이아신아마이드")))
					.andExpect(jsonPath("$.ingredients[1].ingredientId").value(niacinamide))
					.andExpect(jsonPath("$.ingredients[1].standardName").value("나이아신아마이드"))
					.andExpect(jsonPath("$.skinTypes").value(contains("DRY", "OILY")))
					.andExpect(jsonPath("$.concerns[*].code").value(containsInAnyOrder("PORE", "DULLNESS")))
					.andExpect(jsonPath("$.dataStatus.sufficient").value(true))
					.andExpect(jsonPath("$.dataStatus.issues", hasSize(0)))
					.andExpect(jsonPath("$.source.name").value("브랜드 공식몰"))
					.andExpect(jsonPath("$.source.url").value("https://brand.example.com"));
		}

		@Test
		@DisplayName("데이터가 부족하면 이유를 함께 준다")
		void dataIssues() throws Exception {
			long productId = insertProduct("SERUM", "불완전 세럼", 25000, "ACTIVE", "INCOMPLETE");
			insertLine(productId, 1, null, "미확인성분");

			mockMvc.perform(get("/api/products/{id}", productId))
					.andExpect(jsonPath("$.dataStatus.sufficient").value(false))
					.andExpect(jsonPath("$.dataStatus.issues")
							.value(contains("INGREDIENTS_INCOMPLETE", "UNMATCHED_INGREDIENTS")))
					.andExpect(jsonPath("$.ingredients[0].ingredientId").value(nullValue()));
		}

		@Test
		@DisplayName("숨김 상품과 없는 상품은 404다")
		void notFound() throws Exception {
			long hidden = insertProduct("SERUM", "숨김 세럼", 1000, "HIDDEN", "COMPLETE");

			mockMvc.perform(get("/api/products/{id}", hidden))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("NOT_FOUND"));
			mockMvc.perform(get("/api/products/{id}", 999_999_999L))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("성분")
	class IngredientApi {

		@Test
		@DisplayName("성분 상세는 표준명과 표기만 같은 별칭을 빼고 다른 이름을 준다")
		void detail() throws Exception {
			long productId = insertProduct("SERUM", "세럼", 1000, "ACTIVE", "COMPLETE");
			long hidden = insertProduct("SERUM", "숨김", 1000, "HIDDEN", "COMPLETE");
			insertLine(productId, 1, niacinamide, "나이아신아마이드");
			insertLine(hidden, 1, niacinamide, "나이아신아마이드");

			mockMvc.perform(get("/api/ingredients/{id}", niacinamide))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.standardName").value("나이아신아마이드"))
					.andExpect(jsonPath("$.inciName").value("Niacinamide"))
					.andExpect(jsonPath("$.aliases").value(contains("Niacinamide", "비타민B3")))
					.andExpect(jsonPath("$.description").value("비타민 B3의 일종이다."))
					.andExpect(jsonPath("$.source.name").value("식약처 화장품 원료성분정보"))
					.andExpect(jsonPath("$.productCount").value(1));
		}

		@Test
		@DisplayName("성분이 들어간 노출 상품만 목록으로 준다")
		void products() throws Exception {
			long with = insertProduct("SERUM", "들어감", 1000, "ACTIVE", "COMPLETE");
			long without = insertProduct("SERUM", "안 들어감", 1000, "ACTIVE", "COMPLETE");
			insertLine(with, 1, niacinamide, "나이아신아마이드");
			insertLine(without, 1, glycerin, "글리세린");

			mockMvc.perform(get("/api/ingredients/{id}/products", niacinamide))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].name").value(contains("들어감")))
					.andExpect(jsonPath("$.items[*].name").value(not(hasItem("안 들어감"))));
		}

		@Test
		@DisplayName("표준명이나 별칭으로 표준 성분을 찾고, 띄어쓰기·대소문자는 무시한다")
		void lookup() throws Exception {
			mockMvc.perform(get("/api/ingredients/lookup").param("name", "나이아신아마이드"))
					.andExpect(jsonPath("$.id").value(niacinamide))
					.andExpect(jsonPath("$.matchedBy").value("STANDARD_NAME"));
			mockMvc.perform(get("/api/ingredients/lookup").param("name", "나이아신 아마이드"))
					.andExpect(jsonPath("$.matchedBy").value("STANDARD_NAME"));
			mockMvc.perform(get("/api/ingredients/lookup").param("name", "비타민 b3"))
					.andExpect(jsonPath("$.id").value(niacinamide))
					.andExpect(jsonPath("$.matchedName").value("비타민B3"))
					.andExpect(jsonPath("$.matchedBy").value("ALIAS"));
		}

		@Test
		@DisplayName("찾는 성분이 없으면 404, 이름이 비어 있으면 400이다")
		void lookupFailures() throws Exception {
			mockMvc.perform(get("/api/ingredients/lookup").param("name", "없는성분"))
					.andExpect(status().isNotFound());
			mockMvc.perform(get("/api/ingredients/lookup").param("name", " "))
					.andExpect(status().isBadRequest());
			mockMvc.perform(get("/api/ingredients/{id}", 999_999_999L))
					.andExpect(status().isNotFound());
		}
	}

	// --- 테스트 데이터 ---------------------------------------------------------

	private long insertIngredient(String name, String inci, String description) {
		return jdbc.queryForObject("""
				INSERT INTO ingredients (standard_name, inci_name, description, source_name, created_at, updated_at)
				VALUES (?, ?, ?, '식약처 화장품 원료성분정보', now(), now()) RETURNING id
				""", Long.class, name, inci, description);
	}

	private void insertAlias(long ingredientId, String alias, String normalized) {
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, ?, ?)",
				ingredientId, alias, normalized);
	}

	private long insertProduct(String categoryCode, String name, Integer price, String status,
			String ingredientStatus) {
		return jdbc.queryForObject("""
				INSERT INTO products (brand_id, category_id, data_source_id, name, price, status, ingredient_status,
				                      source_collected_at, created_at, updated_at)
				VALUES (?, (SELECT id FROM categories WHERE code = ?), ?, ?, ?, ?, ?, current_date, now(), now())
				RETURNING id
				""", Long.class, brandId, categoryCode, sourceId, name, price, status, ingredientStatus);
	}

	private void insertLine(long productId, int order, Long ingredientId, String rawName) {
		jdbc.update("INSERT INTO product_ingredients VALUES (?, ?, ?, ?)", productId, order, ingredientId, rawName);
	}

	private void insertAction(long productId, String occurredAt) {
		jdbc.update("INSERT INTO product_actions (actor_key, product_id, action_type, occurred_at) VALUES (?, ?, 'VIEW', "
				+ occurredAt + ")", UUID.randomUUID(), productId);
	}
}
