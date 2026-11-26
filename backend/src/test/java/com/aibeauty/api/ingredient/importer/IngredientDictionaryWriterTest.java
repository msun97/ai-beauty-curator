package com.aibeauty.api.ingredient.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IngredientDictionaryWriterTest {

	private static final IngredientDictionaryWriter.Source SOURCE = MfdsIngredientImporter.SOURCE;
	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	@Autowired
	JdbcTemplate jdbc;

	IngredientDictionaryWriter writer;

	@BeforeEach
	void setUp() {
		writer = new IngredientDictionaryWriter(jdbc);
	}

	private static IngredientDictionary dictionary(MfdsIngredientItem... items) {
		return IngredientDictionary.from(List.of(items));
	}

	@Test
	@DisplayName("성분·별칭·출처를 넣고, 다시 실행해도 중복되지 않는다")
	void writesIdempotently() {
		IngredientDictionary dictionary = dictionary(
				new MfdsIngredientItem("나이아신아마이드", "Niacinamide", null, "비타민 B3의 일종이다.", "니코틴아마이드"));

		IngredientDictionaryWriter.Result first = writer.write(dictionary, SOURCE, DAY, NOW);
		IngredientDictionaryWriter.Result second = writer.write(dictionary, SOURCE, DAY, NOW);

		assertThat(first.ingredientsUpserted()).isEqualTo(1);
		assertThat(first.aliasesInserted()).isEqualTo(3);
		assertThat(second.aliasesInserted()).isZero();
		assertThat(second.aliasesAlreadyPresent()).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ingredients", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ingredient_aliases", Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject(
				"SELECT source_type FROM data_sources WHERE name = ?", String.class, SOURCE.name()))
				.isEqualTo("PUBLIC_DATA");
		assertThat(jdbc.queryForObject(
				"SELECT inci_name FROM ingredients WHERE standard_name = '나이아신아마이드'", String.class))
				.isEqualTo("Niacinamide");
	}

	@Test
	@DisplayName("다시 수집하면 원천 값으로 갱신하지만, 사람이 다른 출처로 고친 성분은 그대로 둔다")
	void keepsManuallyCuratedIngredients() {
		writer.write(dictionary(
				new MfdsIngredientItem("레티놀", "Retinol", null, "옛 설명", null),
				new MfdsIngredientItem("판테놀", "Panthenol", null, "옛 설명", null)), SOURCE, DAY, NOW);
		jdbc.update("UPDATE ingredients SET description = '직접 쓴 설명', source_name = '운영자' WHERE standard_name = '레티놀'");

		IngredientDictionaryWriter.Result result = writer.write(dictionary(
				new MfdsIngredientItem("레티놀", "Retinol", null, "새 설명", null),
				new MfdsIngredientItem("판테놀", "Panthenol", null, "새 설명", null)), SOURCE, DAY, NOW);

		assertThat(result.ingredientsUpserted()).isEqualTo(1);
		assertThat(result.ingredientsKeptManual()).isEqualTo(1);
		assertThat(description("레티놀")).isEqualTo("직접 쓴 설명");
		assertThat(description("판테놀")).isEqualTo("새 설명");
	}

	@Test
	@DisplayName("이미 있는 별칭은 다른 성분을 가리키도록 바꾸지 않는다")
	void keepsExistingAliases() {
		long manualId = jdbc.queryForObject(
				"INSERT INTO ingredients (standard_name, created_at, updated_at) VALUES ('수동성분', now(), now()) RETURNING id",
				Long.class);
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, '물', '물')", manualId);

		writer.write(dictionary(new MfdsIngredientItem("정제수", "Water", null, null, "물")), SOURCE, DAY, NOW);

		assertThat(jdbc.queryForObject(
				"SELECT ingredient_id FROM ingredient_aliases WHERE normalized_alias = '물'", Long.class))
				.isEqualTo(manualId);
	}

	private String description(String standardName) {
		return jdbc.queryForObject("SELECT description FROM ingredients WHERE standard_name = ?", String.class,
				standardName);
	}
}
