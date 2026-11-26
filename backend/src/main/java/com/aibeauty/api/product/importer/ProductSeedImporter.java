package com.aibeauty.api.product.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.ingredient.domain.IngredientListParser;
import com.aibeauty.api.ingredient.domain.IngredientMatcher;
import com.aibeauty.api.ingredient.domain.IngredientNames;
import com.aibeauty.api.product.domain.SkinType;

/**
 * 직접 입력한 상품 파일(seed/products.tsv)을 반영한다.
 *
 * <ul>
 * <li>브랜드+상품명이 같으면 같은 상품으로 보고 갱신한다. 파일에 없는 상품은 건드리지 않는다.</li>
 * <li>전성분은 원문 표기를 raw_name에 그대로 두고, 표준 성분에 매칭되면 ingredient_id를 채운다.</li>
 * <li>피부 타입·고민 태그는 파일 값으로 바꾼다. 비어 있으면 태그가 없는 상태가 된다.</li>
 * <li>한 행이라도 형식이 틀리면 아무것도 반영하지 않는다.</li>
 * </ul>
 */
public class ProductSeedImporter {

	private static final Logger log = LoggerFactory.getLogger(ProductSeedImporter.class);
	private static final List<String> REQUIRED = List.of(
			"brand", "name", "category", "source_name", "source_url", "collected_at", "ingredients");
	private static final int REPORTED_UNMATCHED = 30;

	/**
	 * @param sourceTerms 출처의 이용 조건 (NFR-LEG-02). 비어 있으면 기존 값을 유지한다
	 */
	record Row(int lineNo, String brand, String name, String category, String volume, Integer price,
			String description, String sourceName, String sourceTerms, String sourceUrl, LocalDate collectedAt,
			List<String> ingredients, List<String> skinTypes, List<String> concerns) {
	}

	public record ProductMatch(String brand, String name, int total, int matched) {
	}

	/**
	 * @param unmatchedNames 매칭되지 않은 원문 이름과 등장 횟수 (많은 순)
	 */
	public record Result(List<ProductMatch> products, Map<String, Integer> unmatchedNames) {

		public int totalLines() {
			return products.stream().mapToInt(ProductMatch::total).sum();
		}

		public int matchedLines() {
			return products.stream().mapToInt(ProductMatch::matched).sum();
		}

		public double matchRate() {
			return totalLines() == 0 ? 0 : (double) matchedLines() / totalLines();
		}
	}

	private final JdbcTemplate jdbc;
	private final Resource resource;
	private final Clock clock;

	public ProductSeedImporter(JdbcTemplate jdbc, Resource resource, Clock clock) {
		this.jdbc = jdbc;
		this.resource = resource;
		this.clock = clock;
	}

	@Transactional
	public Result run() {
		List<Row> rows = parse(resource);
		Map<String, Long> categories = idsByCode("categories");
		Map<String, Long> concerns = idsByCode("skin_concerns");
		validateCodes(rows, categories, concerns);
		IngredientMatcher matcher = loadMatcher();
		Timestamp now = Timestamp.from(clock.instant());

		List<ProductMatch> matches = new ArrayList<>();
		Map<String, Integer> unmatched = new HashMap<>();
		for (Row row : rows) {
			long sourceId = upsertSource(row.sourceName(), row.sourceTerms());
			long brandId = upsertBrand(row.brand());
			long productId = upsertProduct(row, brandId, categories.get(row.category()), sourceId, now);

			jdbc.update("DELETE FROM product_ingredients WHERE product_id = ?", productId);
			int matched = 0;
			for (int i = 0; i < row.ingredients().size(); i++) {
				String rawName = row.ingredients().get(i);
				Long ingredientId = matcher.match(rawName).orElse(null);
				if (ingredientId != null) {
					matched++;
				} else {
					unmatched.merge(rawName, 1, Integer::sum);
				}
				jdbc.update("INSERT INTO product_ingredients (product_id, display_order, ingredient_id, raw_name) VALUES (?, ?, ?, ?)",
						productId, i + 1, ingredientId, rawName);
			}

			jdbc.update("DELETE FROM product_skin_types WHERE product_id = ?", productId);
			row.skinTypes().forEach(type -> jdbc.update(
					"INSERT INTO product_skin_types (product_id, skin_type) VALUES (?, ?)", productId, type));
			jdbc.update("DELETE FROM product_concern_tags WHERE product_id = ?", productId);
			row.concerns().forEach(code -> jdbc.update(
					"INSERT INTO product_concern_tags (product_id, concern_id) VALUES (?, ?)", productId,
					concerns.get(code)));

			matches.add(new ProductMatch(row.brand(), row.name(), row.ingredients().size(), matched));
		}

		Result result = new Result(matches, sortByCount(unmatched));
		report(result);
		return result;
	}

	private long upsertSource(String name, String usageTerms) {
		return jdbc.queryForObject("""
				INSERT INTO data_sources (name, source_type, usage_terms) VALUES (?, 'MANUAL', ?)
				ON CONFLICT (name) DO UPDATE SET usage_terms = COALESCE(EXCLUDED.usage_terms, data_sources.usage_terms)
				RETURNING id
				""", Long.class, name, usageTerms);
	}

	private long upsertBrand(String name) {
		return jdbc.queryForObject("""
				INSERT INTO brands (name) VALUES (?)
				ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
				RETURNING id
				""", Long.class, name);
	}

	/** 전성분이 있으면 COMPLETE. 매칭 실패는 상품 상세의 UNMATCHED_INGREDIENTS로 따로 드러난다. */
	private long upsertProduct(Row row, long brandId, long categoryId, long sourceId, Timestamp now) {
		String ingredientStatus = row.ingredients().isEmpty() ? "INCOMPLETE" : "COMPLETE";
		return jdbc.queryForObject("""
				INSERT INTO products (brand_id, category_id, data_source_id, name, description, volume, price,
				                      price_updated_at, ingredient_status, source_url, source_collected_at,
				                      created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (brand_id, name) DO UPDATE
				SET category_id = EXCLUDED.category_id,
				    data_source_id = EXCLUDED.data_source_id,
				    description = EXCLUDED.description,
				    volume = EXCLUDED.volume,
				    price = EXCLUDED.price,
				    price_updated_at = EXCLUDED.price_updated_at,
				    ingredient_status = EXCLUDED.ingredient_status,
				    source_url = EXCLUDED.source_url,
				    source_collected_at = EXCLUDED.source_collected_at,
				    updated_at = EXCLUDED.updated_at
				RETURNING id
				""", Long.class,
				brandId, categoryId, sourceId, row.name(), row.description(), row.volume(), row.price(),
				row.price() == null ? null : Timestamp.valueOf(row.collectedAt().atStartOfDay()),
				ingredientStatus, row.sourceUrl(), row.collectedAt(), now, now);
	}

	private IngredientMatcher loadMatcher() {
		Map<String, Long> standard = new HashMap<>();
		jdbc.query("SELECT id, standard_name FROM ingredients",
				rs -> {
					standard.put(rs.getString("standard_name"), rs.getLong("id"));
				});
		Map<String, Long> aliases = new HashMap<>();
		jdbc.query("SELECT ingredient_id, normalized_alias FROM ingredient_aliases",
				rs -> {
					aliases.put(rs.getString("normalized_alias"), rs.getLong("ingredient_id"));
				});
		return new IngredientMatcher(standard, aliases);
	}

	private Map<String, Long> idsByCode(String table) {
		Map<String, Long> ids = new HashMap<>();
		jdbc.query("SELECT id, code FROM " + table,
				rs -> {
					ids.put(rs.getString("code"), rs.getLong("id"));
				});
		return ids;
	}

	private static void validateCodes(List<Row> rows, Map<String, Long> categories, Map<String, Long> concerns) {
		List<String> errors = new ArrayList<>();
		for (Row row : rows) {
			if (!categories.containsKey(row.category())) {
				errors.add(row.lineNo() + "행: 없는 카테고리 " + row.category());
			}
			row.concerns().stream().filter(code -> !concerns.containsKey(code))
					.forEach(code -> errors.add(row.lineNo() + "행: 없는 피부 고민 " + code));
		}
		if (!errors.isEmpty()) {
			throw new IllegalStateException("상품 파일 오류: " + errors);
		}
	}

	private static void report(Result result) {
		log.info("상품 반영 완료: 상품 {}개, 전성분 {}줄 중 {}줄 매칭 ({}%)", result.products().size(), result.totalLines(),
				result.matchedLines(), Math.round(result.matchRate() * 1000) / 10.0);
		result.products().stream().filter(p -> p.matched() < p.total()).forEach(p ->
				log.info("  미매칭 있음: {} {} ({}/{})", p.brand(), p.name(), p.matched(), p.total()));
		result.unmatchedNames().entrySet().stream().limit(REPORTED_UNMATCHED).forEach(e ->
				log.info("  미매칭 이름: {} ({}회)", e.getKey(), e.getValue()));
	}

	private static Map<String, Integer> sortByCount(Map<String, Integer> counts) {
		Map<String, Integer> sorted = new LinkedHashMap<>();
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	// --- 파일 읽기 --------------------------------------------------------------

	/** 탭 구분, 첫 행(주석 제외)이 머리글. 머리글 이름으로 열을 찾으므로 열 순서는 자유다. */
	static List<Row> parse(Resource resource) {
		List<String> errors = new ArrayList<>();
		List<Row> rows = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		Map<String, Integer> columns = null;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			int lineNo = 0;
			while ((line = reader.readLine()) != null) {
				lineNo++;
				if (lineNo == 1 && line.startsWith("﻿")) {
					line = line.substring(1);
				}
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cells = line.split("\t", -1);
				if (columns == null) {
					columns = header(cells);
					continue;
				}
				try {
					Row row = toRow(lineNo, cells, columns);
					if (!keys.add(row.brand() + "\t" + row.name())) {
						throw new IllegalArgumentException("같은 브랜드·상품명이 이미 있습니다");
					}
					rows.add(row);
				} catch (IllegalArgumentException e) {
					errors.add(lineNo + "행: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("상품 파일을 읽지 못했습니다: " + resource.getDescription(), e);
		}
		if (!errors.isEmpty()) {
			throw new IllegalStateException("상품 파일 오류: " + errors);
		}
		return rows;
	}

	private static Map<String, Integer> header(String[] cells) {
		Map<String, Integer> columns = new TreeMap<>();
		for (int i = 0; i < cells.length; i++) {
			columns.put(cells[i].strip(), i);
		}
		List<String> missing = REQUIRED.stream().filter(name -> !columns.containsKey(name)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("상품 파일 머리글에 필수 열이 없습니다: " + missing);
		}
		return columns;
	}

	private static Row toRow(int lineNo, String[] cells, Map<String, Integer> columns) {
		String brand = required(cells, columns, "brand");
		String name = required(cells, columns, "name");
		String price = cell(cells, columns, "price");
		List<String> ingredients = IngredientListParser.parse(cell(cells, columns, "ingredients"));
		return new Row(lineNo, brand, name,
				required(cells, columns, "category"),
				blankToNull(cell(cells, columns, "volume")),
				price.isEmpty() ? null : parsePrice(price),
				blankToNull(cell(cells, columns, "description")),
				required(cells, columns, "source_name"),
				blankToNull(cell(cells, columns, "source_terms")),
				required(cells, columns, "source_url"),
				parseDate(required(cells, columns, "collected_at")),
				ingredients,
				skinTypes(cell(cells, columns, "skin_types")),
				list(cell(cells, columns, "concerns")));
	}

	private static String cell(String[] cells, Map<String, Integer> columns, String name) {
		Integer index = columns.get(name);
		return index == null || index >= cells.length ? "" : IngredientNames.clean(cells[index]);
	}

	private static String required(String[] cells, Map<String, Integer> columns, String name) {
		String value = cell(cells, columns, name);
		if (value.isEmpty()) {
			throw new IllegalArgumentException(name + " 값이 비어 있습니다");
		}
		return value;
	}

	private static Integer parsePrice(String value) {
		try {
			int price = Integer.parseInt(value.replace(",", ""));
			if (price < 0) {
				throw new NumberFormatException();
			}
			return price;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("price는 0 이상의 정수여야 합니다: " + value);
		}
	}

	private static LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("collected_at은 YYYY-MM-DD 형식이어야 합니다: " + value);
		}
	}

	private static List<String> skinTypes(String value) {
		List<String> types = list(value);
		for (String type : types) {
			if (Arrays.stream(SkinType.values()).noneMatch(t -> t.name().equals(type))) {
				throw new IllegalArgumentException("없는 피부 타입: " + type);
			}
		}
		return types;
	}

	private static List<String> list(String value) {
		return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
	}

	private static String blankToNull(String value) {
		return value.isEmpty() ? null : value;
	}
}
