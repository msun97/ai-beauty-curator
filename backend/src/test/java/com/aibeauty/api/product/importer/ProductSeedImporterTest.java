package com.aibeauty.api.product.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductSeedImporterTest {

	private static final String HEADER = "brand\tname\tcategory\tvolume\tprice\tsource_name\tsource_url\tcollected_at\tingredients\tskin_types\tconcerns\n";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	Clock clock;

	@BeforeEach
	void setUp() {
		long water = ingredient("정제수");
		ingredient("글리세린");
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, 'Water', 'water')", water);
	}

	private ProductSeedImporter importer(String rows) {
		return new ProductSeedImporter(jdbc, new ByteArrayResource((HEADER + rows).getBytes(StandardCharsets.UTF_8)),
				clock);
	}

	@Test
	@DisplayName("상품·전성분·태그를 넣고 매칭 결과를 보고한다")
	void importsProducts() {
		ProductSeedImporter.Result result = importer(
				"테스트브랜드\t수분 토너\tTONER\t200ml\t15000\t컬리 상품정보제공고시\thttps://example.com/1\t2026-11-24\t정제수, 글리세린, 미확인추출물\tDRY,NORMAL\tDRYNESS\n")
				.run();

		assertThat(result.totalLines()).isEqualTo(3);
		assertThat(result.matchedLines()).isEqualTo(2);
		assertThat(result.unmatchedNames()).containsOnlyKeys("미확인추출물");

		Long productId = jdbc.queryForObject("SELECT id FROM products WHERE name = '수분 토너'", Long.class);
		assertThat(jdbc.queryForList(
				"SELECT raw_name FROM product_ingredients WHERE product_id = ? ORDER BY display_order", String.class,
				productId)).containsExactly("정제수", "글리세린", "미확인추출물");
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM product_ingredients WHERE product_id = ? AND ingredient_id IS NULL", Long.class,
				productId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT ingredient_status FROM products WHERE id = ?", String.class, productId))
				.isEqualTo("COMPLETE");
		assertThat(jdbc.queryForList("SELECT skin_type FROM product_skin_types WHERE product_id = ? ORDER BY 1",
				String.class, productId)).containsExactly("DRY", "NORMAL");
		assertThat(jdbc.queryForObject("SELECT source_type FROM data_sources WHERE name = '컬리 상품정보제공고시'",
				String.class)).isEqualTo("MANUAL");
	}

	@Test
	@DisplayName("다시 실행하면 같은 상품을 갱신하고 전성분을 교체한다")
	void reimportReplaces() {
		importer("테스트브랜드\t수분 토너\tTONER\t200ml\t15000\t출처\thttps://example.com/1\t2026-11-24\t정제수, 글리세린\t\t\n").run();
		importer("테스트브랜드\t수분 토너\tTONER\t300ml\t20000\t출처\thttps://example.com/1\t2026-11-25\tWater\t\t\n").run();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE name = '수분 토너'", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT price FROM products WHERE name = '수분 토너'", Integer.class))
				.isEqualTo(20000);
		assertThat(jdbc.queryForList("""
				SELECT pi.raw_name FROM product_ingredients pi JOIN products p ON p.id = pi.product_id
				WHERE p.name = '수분 토너'""", String.class)).containsExactly("Water");
	}

	@Test
	@DisplayName("형식이 틀린 행이 있으면 아무것도 넣지 않는다")
	void rejectsInvalidFile() {
		assertThatThrownBy(() -> importer(
				"브랜드\t상품A\tTONER\t\tabc\t출처\thttps://example.com\t2026-11-24\t정제수\t\t\n"
						+ "브랜드\t상품B\tNOPE\t\t\t출처\thttps://example.com\t2026-11-24\t정제수\t\t\n").run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("price");
		assertThatThrownBy(() -> importer(
				"브랜드\t상품B\tNOPE\t\t\t출처\thttps://example.com\t2026-11-24\t정제수\t\t\n").run())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("NOPE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isZero();
	}

	@Test
	@DisplayName("저장소의 상품 파일은 형식이 맞다")
	void seedFileIsValid() {
		List<ProductSeedImporter.Row> rows = ProductSeedImporter.parse(new ClassPathResource("seed/products.tsv"));
		assertThat(rows).allSatisfy(row -> assertThat(row.ingredients()).isNotEmpty());
	}

	private long ingredient(String name) {
		return jdbc.queryForObject(
				"INSERT INTO ingredients (standard_name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
				Long.class, name);
	}
}
