package com.aibeauty.api.ingredient.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

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
class CuratedAliasImporterTest {

	@Autowired
	JdbcTemplate jdbc;

	private static ByteArrayResource tsv(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("저장소의 별칭 파일은 형식이 맞고 별칭이 중복되지 않는다")
	void seedFileIsValid() {
		assertThat(CuratedAliasImporter.parse(new ClassPathResource("seed/ingredient-aliases.tsv")))
				.isNotEmpty()
				.noneMatch(row -> row.alias().equalsIgnoreCase("BHA"));
	}

	@Test
	@DisplayName("새 별칭은 추가하고, 다른 성분을 가리키던 별칭은 지정한 성분으로 바꾼다")
	void insertsAndReassigns() {
		long purified = insertIngredient("정제수");
		long sparkling = insertIngredient("탄산수");
		long tocopherol = insertIngredient("토코페롤");
		jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, 'Water', 'water')",
				sparkling);

		CuratedAliasImporter importer = new CuratedAliasImporter(jdbc, tsv("""
				# 주석
				alias\tstandard_name\treason
				water\t정제수\t영문 표기
				비타민 E\t토코페롤\t통칭
				"""));
		CuratedAliasImporter.Result first = importer.run();
		CuratedAliasImporter.Result second = importer.run();

		assertThat(first).isEqualTo(new CuratedAliasImporter.Result(1, 1, 0));
		assertThat(second).isEqualTo(new CuratedAliasImporter.Result(0, 0, 2));
		assertThat(aliasTarget("water")).isEqualTo(purified);
		assertThat(aliasTarget("비타민e")).isEqualTo(tocopherol);
	}

	@Test
	@DisplayName("사전에 없는 표준명이 있으면 아무것도 반영하지 않는다")
	void failsOnUnknownStandardName() {
		insertIngredient("판테놀");
		CuratedAliasImporter importer = new CuratedAliasImporter(jdbc, tsv("""
				비타민B5\t판테놀\t통칭
				비타민Z\t없는성분\t오타
				"""));

		assertThatThrownBy(importer::run)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("없는성분");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ingredient_aliases", Long.class)).isZero();
	}

	@Test
	@DisplayName("같은 별칭이 두 번 나오면 파일 오류로 본다")
	void rejectsDuplicateAlias() {
		assertThatThrownBy(() -> CuratedAliasImporter.parse(tsv("시카\t병풀추출물\n시 카\t병풀잎추출물\n")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2행");
	}

	private long insertIngredient(String name) {
		return jdbc.queryForObject(
				"INSERT INTO ingredients (standard_name, created_at, updated_at) VALUES (?, now(), now()) RETURNING id",
				Long.class, name);
	}

	private long aliasTarget(String normalized) {
		return jdbc.queryForObject("SELECT ingredient_id FROM ingredient_aliases WHERE normalized_alias = ?",
				Long.class, normalized);
	}
}
