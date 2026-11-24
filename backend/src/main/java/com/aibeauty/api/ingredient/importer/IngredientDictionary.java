package com.aibeauty.api.ingredient.importer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 원천 데이터를 성분 사전(표준명 + 별칭)으로 정리한다. DB에 의존하지 않는 순수 변환이다.
 *
 * <p>별칭 규칙
 * <ul>
 * <li>표준명, 영문명, 이명을 모두 별칭으로 등록한다. 표준명도 넣어 띄어쓰기·기호 차이를 흡수한다.</li>
 * <li>같은 비교용 값이 여러 성분에 걸리면 우선순위(표준명 &gt; 영문명 &gt; 이명)가 높은 쪽을 택한다.</li>
 * <li>최고 우선순위에서도 여러 성분이 겹치면 어느 쪽에도 등록하지 않는다. 잘못된 매칭이 매칭 실패보다 해롭다.</li>
 * </ul>
 */
public final class IngredientDictionary {

	static final int MAX_STANDARD_NAME = 200;
	static final int MAX_ALIAS = 300;

	public record Entry(String standardName, String inciName, String description) {
	}

	public record Alias(String standardName, String alias, String normalizedAlias) {
	}

	/** 비교용 값이 겹쳐 등록하지 않은 별칭 */
	public record AmbiguousAlias(String normalizedAlias, List<String> standardNames) {
	}

	private enum AliasKind {
		STANDARD_NAME, ENGLISH_NAME, SYNONYM
	}

	private record Candidate(String standardName, String alias, AliasKind kind) {
	}

	private final List<Entry> entries;
	private final List<Alias> aliases;
	private final List<AmbiguousAlias> ambiguousAliases;
	private final int skippedItems;

	private IngredientDictionary(List<Entry> entries, List<Alias> aliases, List<AmbiguousAlias> ambiguousAliases,
			int skippedItems) {
		this.entries = entries;
		this.aliases = aliases;
		this.ambiguousAliases = ambiguousAliases;
		this.skippedItems = skippedItems;
	}

	public static IngredientDictionary from(List<MfdsIngredientItem> items) {
		Map<String, Entry> entries = new LinkedHashMap<>();
		Map<String, List<Candidate>> candidates = new LinkedHashMap<>();
		int skipped = 0;

		for (MfdsIngredientItem item : items) {
			String standardName = IngredientNames.clean(item.koreanName());
			if (standardName.isEmpty() || standardName.length() > MAX_STANDARD_NAME
					|| IngredientNames.normalize(standardName).isEmpty()) {
				skipped++;
				continue;
			}
			List<String> englishNames = IngredientNames.split(item.englishName());
			String inciName = englishNames.isEmpty() ? null : truncate(englishNames.getFirst(), MAX_ALIAS);
			String description = blankToNull(IngredientNames.clean(item.origin()));

			// 원천에 같은 표준명이 두 번 오면 먼저 온 값을 유지하고 비어 있는 항목만 채운다
			entries.merge(standardName, new Entry(standardName, inciName, description), (kept, added) -> new Entry(
					standardName,
					kept.inciName() != null ? kept.inciName() : added.inciName(),
					kept.description() != null ? kept.description() : added.description()));

			addCandidate(candidates, standardName, standardName, AliasKind.STANDARD_NAME);
			englishNames.forEach(name -> addCandidate(candidates, standardName, name, AliasKind.ENGLISH_NAME));
			IngredientNames.split(item.synonyms())
					.forEach(name -> addCandidate(candidates, standardName, name, AliasKind.SYNONYM));
		}

		List<Alias> aliases = new ArrayList<>();
		List<AmbiguousAlias> ambiguous = new ArrayList<>();
		candidates.forEach((normalized, list) -> {
			AliasKind best = list.stream().map(Candidate::kind).min(Comparator.naturalOrder()).orElseThrow();
			List<Candidate> top = list.stream().filter(c -> c.kind() == best).toList();
			Set<String> owners = new LinkedHashSet<>();
			top.forEach(c -> owners.add(c.standardName()));
			if (owners.size() == 1) {
				Candidate chosen = top.getFirst();
				aliases.add(new Alias(chosen.standardName(), chosen.alias(), normalized));
			} else {
				ambiguous.add(new AmbiguousAlias(normalized, List.copyOf(owners)));
			}
		});

		return new IngredientDictionary(List.copyOf(entries.values()), List.copyOf(aliases), List.copyOf(ambiguous),
				skipped);
	}

	private static void addCandidate(Map<String, List<Candidate>> candidates, String standardName, String alias,
			AliasKind kind) {
		String trimmed = truncate(alias, MAX_ALIAS);
		String normalized = truncate(IngredientNames.normalize(trimmed), MAX_ALIAS);
		if (normalized.isEmpty()) {
			return;
		}
		candidates.computeIfAbsent(normalized, key -> new ArrayList<>()).add(new Candidate(standardName, trimmed, kind));
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String blankToNull(String value) {
		return value.isEmpty() ? null : value;
	}

	public List<Entry> entries() {
		return entries;
	}

	public List<Alias> aliases() {
		return aliases;
	}

	public List<AmbiguousAlias> ambiguousAliases() {
		return ambiguousAliases;
	}

	/** 표준명이 비어 있거나 너무 길어 건너뛴 원천 건수 */
	public int skippedItems() {
		return skippedItems;
	}
}
