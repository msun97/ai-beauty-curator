package com.aibeauty.api.ingredient.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직접 지정한 별칭(seed/ingredient-aliases.tsv)을 반영한다.
 * 자동으로 만든 별칭과 비교용 값이 같으면 이 파일의 연결로 바꾼다.
 * 파일에 없는 표준명이 하나라도 있으면 아무것도 반영하지 않는다.
 */
public class CuratedAliasImporter {

	private static final Logger log = LoggerFactory.getLogger(CuratedAliasImporter.class);
	private static final String HEADER = "alias";

	public record Row(int lineNo, String alias, String standardName) {
	}

	public record Result(int inserted, int reassigned, int unchanged) {
	}

	private final JdbcTemplate jdbc;
	private final Resource resource;

	public CuratedAliasImporter(JdbcTemplate jdbc, Resource resource) {
		this.jdbc = jdbc;
		this.resource = resource;
	}

	@Transactional
	public Result run() {
		List<Row> rows = parse(resource);
		Map<String, Long> ids = ingredientIds(rows);
		List<String> missing = rows.stream()
				.filter(row -> !ids.containsKey(row.standardName()))
				.map(row -> row.lineNo() + "행 " + row.standardName())
				.toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("성분 사전에 없는 표준명이 있습니다: " + missing);
		}

		int inserted = 0;
		int reassigned = 0;
		int unchanged = 0;
		for (Row row : rows) {
			long ingredientId = ids.get(row.standardName());
			String normalized = IngredientNames.normalize(row.alias());
			List<Long> current = jdbc.queryForList(
					"SELECT ingredient_id FROM ingredient_aliases WHERE normalized_alias = ?", Long.class, normalized);
			if (current.isEmpty()) {
				jdbc.update("INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias) VALUES (?, ?, ?)",
						ingredientId, row.alias(), normalized);
				inserted++;
			} else if (current.getFirst() != ingredientId) {
				jdbc.update("UPDATE ingredient_aliases SET ingredient_id = ?, alias = ? WHERE normalized_alias = ?",
						ingredientId, row.alias(), normalized);
				log.info("별칭 연결 변경: {} → {}", row.alias(), row.standardName());
				reassigned++;
			} else {
				unchanged++;
			}
		}
		Result result = new Result(inserted, reassigned, unchanged);
		log.info("직접 지정 별칭 반영 완료: 추가 {}, 연결 변경 {}, 변동 없음 {}", inserted, reassigned, unchanged);
		return result;
	}

	/** 탭 구분. '#'으로 시작하는 줄과 빈 줄, 머리글은 건너뛴다. 같은 별칭이 두 번 나오면 실패한다. */
	static List<Row> parse(Resource resource) {
		List<Row> rows = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			int lineNo = 0;
			while ((line = reader.readLine()) != null) {
				lineNo++;
				String trimmed = line.strip();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				String[] columns = line.split("\t", -1);
				String alias = IngredientNames.clean(columns[0]);
				if (alias.equals(HEADER)) {
					continue;
				}
				if (columns.length < 2 || IngredientNames.clean(columns[1]).isEmpty()) {
					throw new IllegalStateException(lineNo + "행: 별칭과 표준명을 탭으로 구분해 주세요");
				}
				String normalized = IngredientNames.normalize(alias);
				if (normalized.isEmpty()) {
					throw new IllegalStateException(lineNo + "행: 별칭이 비어 있습니다");
				}
				if (!seen.add(normalized)) {
					throw new IllegalStateException(lineNo + "행: 같은 별칭이 이미 있습니다 (" + alias + ")");
				}
				rows.add(new Row(lineNo, alias, IngredientNames.clean(columns[1])));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("별칭 파일을 읽지 못했습니다: " + resource.getDescription(), e);
		}
		return rows;
	}

	private Map<String, Long> ingredientIds(List<Row> rows) {
		Map<String, Long> ids = new HashMap<>();
		for (Row row : rows) {
			jdbc.query("SELECT id FROM ingredients WHERE standard_name = ?",
					rs -> {
						ids.put(row.standardName(), rs.getLong("id"));
					}, row.standardName());
		}
		return ids;
	}
}
