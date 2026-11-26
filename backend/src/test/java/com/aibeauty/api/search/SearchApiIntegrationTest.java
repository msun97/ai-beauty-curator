package com.aibeauty.api.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
import com.aibeauty.api.global.security.AccessTokenProvider;
import com.aibeauty.api.global.security.AuthCookies;
import com.aibeauty.api.user.domain.UserRepository;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SearchApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	AccessTokenProvider accessTokenProvider;

	long sourceId;
	long roundlab;
	long torriden;
	long niacinamide;
	long fragrance;
	long water;

	@BeforeEach
	void setUp() {
		sourceId = jdbc.queryForObject(
				"INSERT INTO data_sources (name, source_type) VALUES ('테스트 출처', 'MANUAL') RETURNING id", Long.class);
		roundlab = brand("라운드랩");
		torriden = brand("토리든");
		niacinamide = ingredient("나이아신아마이드");
		fragrance = ingredient("향료");
		water = ingredient("정제수");
		alias(niacinamide, "나이아신아마이드", "나이아신아마이드");
		alias(niacinamide, "비타민B3", "비타민b3");
		alias(fragrance, "향료", "향료");
		alias(fragrance, "Fragrance", "fragrance");
		alias(water, "정제수", "정제수");
	}

	@Nested
	@DisplayName("통합 검색")
	class Search {

		@Test
		@DisplayName("상품명은 띄어쓰기를 무시하고, 브랜드명으로도 찾는다")
		void byNameAndBrand() throws Exception {
			product(roundlab, "TONER", "1025 독도 토너", 15000);
			product(roundlab, "CREAM", "자작나무 수분 크림", 25000);
			product(torriden, "SERUM", "다이브인 세럼", 20000);

			mockMvc.perform(get("/api/search").param("q", "독도토너"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.query").value("독도토너"))
					.andExpect(jsonPath("$.products.items[*].name").value(contains("1025 독도 토너")));
			mockMvc.perform(get("/api/search").param("q", "라운드랩"))
					.andExpect(jsonPath("$.products.items[*].name")
							.value(containsInAnyOrder("1025 독도 토너", "자작나무 수분 크림")));
		}

		@Test
		@DisplayName("성분 별칭으로 검색하면 성분 결과와 그 성분이 든 상품을 함께 준다")
		void byIngredientAlias() throws Exception {
			long serum = product(torriden, "SERUM", "브라이트 세럼", 20000);
			product(torriden, "TONER", "수분 토너", 15000);
			line(serum, 1, niacinamide);

			mockMvc.perform(get("/api/search").param("q", "비타민 B3"))
					.andExpect(jsonPath("$.ingredients[0].id").value(niacinamide))
					.andExpect(jsonPath("$.ingredients[0].standardName").value("나이아신아마이드"))
					.andExpect(jsonPath("$.ingredients[0].matchedAlias").value("비타민B3"))
					.andExpect(jsonPath("$.ingredients[0].productCount").value(1))
					.andExpect(jsonPath("$.products.items[*].name").value(contains("브라이트 세럼")))
					.andExpect(jsonPath("$.suggestions", hasSize(0)));
		}

		@Test
		@DisplayName("성분 부분 검색은 표준명으로 찾으면 별칭 안내를 붙이지 않는다")
		void ingredientPartialMatch() throws Exception {
			mockMvc.perform(get("/api/search").param("q", "나이아신"))
					.andExpect(jsonPath("$.ingredients[*].standardName").value(contains("나이아신아마이드")))
					.andExpect(jsonPath("$.ingredients[0].matchedAlias").value(nullValue()));
		}

		@Test
		@DisplayName("카테고리·브랜드·가격 범위로 거른다")
		void filters() throws Exception {
			product(roundlab, "TONER", "라운드랩 토너 A", 10000);
			product(roundlab, "TONER", "라운드랩 토너 B", 30000);
			product(roundlab, "CREAM", "라운드랩 크림", 20000);
			product(torriden, "TONER", "토리든 토너", 15000);

			mockMvc.perform(get("/api/search").param("q", "토너").param("category", "TONER")
					.param("brandId", String.valueOf(roundlab)).param("minPrice", "5000").param("maxPrice", "20000"))
					.andExpect(jsonPath("$.products.items[*].name").value(contains("라운드랩 토너 A")));
		}

		@Test
		@DisplayName("결과가 없으면 인기 상품을 제안한다")
		void suggestionsWhenEmpty() throws Exception {
			long popular = product(roundlab, "TONER", "인기 토너", 10000);
			jdbc.update("INSERT INTO product_actions (actor_key, product_id, action_type, occurred_at) VALUES (?, ?, 'VIEW', now())",
					UUID.randomUUID(), popular);

			mockMvc.perform(get("/api/search").param("q", "존재하지않는검색어"))
					.andExpect(jsonPath("$.products.items", hasSize(0)))
					.andExpect(jsonPath("$.ingredients", hasSize(0)))
					.andExpect(jsonPath("$.suggestions[0].name").value("인기 토너"));
		}

		@Test
		@DisplayName("검색어의 %와 _는 글자 그대로 찾는다")
		void escapesLikeWildcards() throws Exception {
			product(roundlab, "TONER", "100% 토너", 10000);
			product(roundlab, "TONER", "일반 토너", 10000);

			mockMvc.perform(get("/api/search").param("q", "100%"))
					.andExpect(jsonPath("$.products.items[*].name").value(contains("100% 토너")));
			mockMvc.perform(get("/api/search").param("q", "%"))
					.andExpect(jsonPath("$.products.items[*].name").value(contains("100% 토너")));
		}

		@Test
		@DisplayName("잘못된 입력은 400이다")
		void invalidInput() throws Exception {
			mockMvc.perform(get("/api/search").param("q", " "))
					.andExpect(status().isBadRequest());
			mockMvc.perform(get("/api/search").param("q", "토너").param("minPrice", "20000").param("maxPrice", "10000"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
			mockMvc.perform(get("/api/search").param("q", "토너").param("category", "NOPE"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("기피 성분 제외")
	class ExcludeAvoided {

		@Test
		@DisplayName("로그인 사용자는 기피 성분이 든 상품을 결과에서 뺄 수 있다")
		void excludesAvoidedIngredients() throws Exception {
			long withFragrance = product(roundlab, "TONER", "향 있는 토너", 10000);
			long plain = product(roundlab, "TONER", "무향 토너", 10000);
			line(withFragrance, 1, water);
			line(withFragrance, 2, fragrance);
			line(plain, 1, water);
			long userId = user();
			jdbc.update("INSERT INTO user_ingredient_preferences VALUES (?, ?, 'AVOIDED', now())", userId, fragrance);
			Cookie cookie = new Cookie(AuthCookies.ACCESS_TOKEN,
					accessTokenProvider.issue(userRepository.findById(userId).orElseThrow()));

			mockMvc.perform(get("/api/search").param("q", "토너").cookie(cookie))
					.andExpect(jsonPath("$.products.items", hasSize(2)));
			mockMvc.perform(get("/api/search").param("q", "토너").param("excludeAvoided", "true").cookie(cookie))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.products.items[*].name").value(contains("무향 토너")));
		}

		@Test
		@DisplayName("비회원이 기피 성분 제외를 요청하면 401이다")
		void requiresLogin() throws Exception {
			mockMvc.perform(get("/api/search").param("q", "토너").param("excludeAvoided", "true"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}
	}

	@Nested
	@DisplayName("자동완성·브랜드 목록")
	class AutocompleteAndBrands {

		@Test
		@DisplayName("상품·브랜드·성분 자동완성을 앞부분 일치 우선으로 준다")
		void autocomplete() throws Exception {
			product(roundlab, "TONER", "1025 독도 토너", 15000);
			product(roundlab, "CLEANSING", "독도 클렌저", 17000);

			mockMvc.perform(get("/api/search/autocomplete").param("q", "독도"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.products[*].name").value(contains("독도 클렌저", "1025 독도 토너")))
					.andExpect(jsonPath("$.products[0].brandName").value("라운드랩"));
			mockMvc.perform(get("/api/search/autocomplete").param("q", "라운"))
					.andExpect(jsonPath("$.brands[*].name").value(contains("라운드랩")));
			mockMvc.perform(get("/api/search/autocomplete").param("q", "나이아"))
					.andExpect(jsonPath("$.ingredients[*].standardName").value(contains("나이아신아마이드")));
		}

		@Test
		@DisplayName("브랜드 목록은 노출 상품이 있는 브랜드만 상품 수와 함께 준다")
		void brands() throws Exception {
			product(roundlab, "TONER", "토너", 10000);
			product(roundlab, "CREAM", "크림", 10000);
			brand("상품없는브랜드");

			mockMvc.perform(get("/api/brands"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].name").value(contains("라운드랩")))
					.andExpect(jsonPath("$[0].productCount").value(2));
		}
	}

	// --- 테스트 데이터 ---------------------------------------------------------

	private long brand(String name) {
		return jdbc.queryForObject("INSERT INTO brands (name) VALUES (?) RETURNING id", Long.class, name);
	}

	private long ingredient(String name) {
		return jdbc.queryForObject(
				"INSERT INTO ingredients (standard_name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
				Long.class, name);
	}

	private void alias(long ingredientId, String alias, String normalized) {
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, ?, ?)",
				ingredientId, alias, normalized);
	}

	private long product(long brandId, String categoryCode, String name, int price) {
		return jdbc.queryForObject("""
				INSERT INTO products (brand_id, category_id, data_source_id, name, price, ingredient_status,
				                      source_collected_at, created_at, updated_at)
				VALUES (?, (SELECT id FROM categories WHERE code = ?), ?, ?, ?, 'COMPLETE', current_date, now(), now())
				RETURNING id
				""", Long.class, brandId, categoryCode, sourceId, name, price);
	}

	private void line(long productId, int order, long ingredientId) {
		jdbc.update("INSERT INTO product_ingredients VALUES (?, ?, ?, (SELECT standard_name FROM ingredients WHERE id = ?))",
				productId, order, ingredientId, ingredientId);
	}

	private long user() {
		return jdbc.queryForObject("""
				INSERT INTO users (email, password_hash, nickname, actor_key, password_changed_at, created_at, updated_at)
				VALUES (?, 'hash', '테스터', ?, now(), now(), now()) RETURNING id
				""", Long.class, "search-" + UUID.randomUUID() + "@example.com", UUID.randomUUID());
	}
}
