package com.aibeauty.api.ingredient.importer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성분 사전을 DB에 반영한다. 여러 번 실행해도 결과가 같다(표준명·비교용 별칭 기준 upsert).
 *
 * <ul>
 * <li>성분: 이 출처가 넣은 행(source_name이 같거나 비어 있음)만 갱신한다. 사람이 다른 출처로 고쳐 쓴 설명은 덮어쓰지 않는다.</li>
 * <li>별칭: 이미 있는 비교용 값은 건드리지 않는다. 직접 추가·수정한 별칭이 우선한다.</li>
 * <li>원천에서 사라진 성분은 지우지 않는다. 상품 전성분이 참조하고 있을 수 있다.</li>
 * </ul>
 */
public class IngredientDictionaryWriter {

	private static final int BATCH_SIZE = 1000;

	public record Source(String name, String url, String usageTerms) {
	}

	public record Result(int ingredientsUpserted, int ingredientsKeptManual, int aliasesInserted,
			int aliasesAlreadyPresent) {
	}

	private final JdbcTemplate jdbc;

	public IngredientDictionaryWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public Result write(IngredientDictionary dictionary, Source source, LocalDate collectedOn, Instant now) {
		upsertSource(source, collectedOn);
		Timestamp timestamp = Timestamp.from(now);

		int[][] ingredientCounts = jdbc.batchUpdate("""
				INSERT INTO ingredients (standard_name, inci_name, description, source_name, source_url, verified_at,
				                         created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (standard_name) DO UPDATE
				SET inci_name   = EXCLUDED.inci_name,
				    description = EXCLUDED.description,
				    source_url  = EXCLUDED.source_url,
				    verified_at = EXCLUDED.verified_at,
				    source_name = EXCLUDED.source_name,
				    updated_at  = EXCLUDED.updated_at
				WHERE ingredients.source_name IS NULL OR ingredients.source_name = EXCLUDED.source_name
				""", dictionary.entries(), BATCH_SIZE, (ps, entry) -> {
			ps.setString(1, entry.standardName());
			ps.setString(2, entry.inciName());
			ps.setString(3, entry.description());
			ps.setString(4, source.name());
			ps.setString(5, source.url());
			ps.setObject(6, collectedOn);
			ps.setTimestamp(7, timestamp);
			ps.setTimestamp(8, timestamp);
		});
		int upserted = sum(ingredientCounts);

		Map<String, Long> ids = ingredientIds();
		List<IngredientDictionary.Alias> aliases = dictionary.aliases().stream()
				.filter(alias -> ids.containsKey(alias.standardName()))
				.toList();
		int[][] aliasCounts = jdbc.batchUpdate("""
				INSERT INTO ingredient_aliases (ingredient_id, alias, normalized_alias)
				VALUES (?, ?, ?)
				ON CONFLICT (normalized_alias) DO NOTHING
				""", aliases, BATCH_SIZE, (ps, alias) -> {
			ps.setLong(1, ids.get(alias.standardName()));
			ps.setString(2, alias.alias());
			ps.setString(3, alias.normalizedAlias());
		});
		int inserted = sum(aliasCounts);

		return new Result(upserted, dictionary.entries().size() - upserted, inserted, aliases.size() - inserted);
	}

	private void upsertSource(Source source, LocalDate collectedOn) {
		jdbc.update("""
				INSERT INTO data_sources (name, source_type, url, usage_terms, collected_at)
				VALUES (?, 'PUBLIC_DATA', ?, ?, ?)
				ON CONFLICT (name) DO UPDATE
				SET url = EXCLUDED.url, usage_terms = EXCLUDED.usage_terms, collected_at = EXCLUDED.collected_at
				""", source.name(), source.url(), source.usageTerms(), collectedOn);
	}

	private Map<String, Long> ingredientIds() {
		Map<String, Long> ids = new HashMap<>();
		jdbc.query("SELECT id, standard_name FROM ingredients",
				rs -> {
					ids.put(rs.getString("standard_name"), rs.getLong("id"));
				});
		return ids;
	}

	/** PostgreSQL 드라이버는 batch 결과로 실제 영향 행 수를 돌려준다. ON CONFLICT DO NOTHING/WHERE 불일치는 0이다. */
	private static int sum(int[][] counts) {
		int total = 0;
		for (int[] batch : counts) {
			for (int count : batch) {
				total += Math.max(count, 0);
			}
		}
		return total;
	}
}
