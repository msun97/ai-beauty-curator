package com.aibeauty.api.ingredient.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 식약처 화장품 원료성분정보 전체를 받아 성분 사전에 반영한다.
 * 모든 페이지를 받은 뒤 한 트랜잭션으로 쓰므로, 중간에 실패하면 DB는 바뀌지 않는다.
 */
public class MfdsIngredientImporter {

	private static final Logger log = LoggerFactory.getLogger(MfdsIngredientImporter.class);
	/** 수집일은 서비스 기준 시간대로 기록한다 */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	static final IngredientDictionaryWriter.Source SOURCE = new IngredientDictionaryWriter.Source(
			"식약처 화장품 원료성분정보",
			"https://www.data.go.kr/data/15111774/openapi.do",
			"공공데이터포털 이용허락범위 제한 없음. 출처: 식품의약품안전처");

	public record Summary(int fetched, int skipped, int ambiguousAliases, IngredientDictionaryWriter.Result result) {
	}

	private final MfdsIngredientProperties properties;
	private final MfdsIngredientClient client;
	private final IngredientDictionaryWriter writer;
	private final Clock clock;

	public MfdsIngredientImporter(MfdsIngredientProperties properties, MfdsIngredientClient client,
			IngredientDictionaryWriter writer, Clock clock) {
		this.properties = properties;
		this.client = client;
		this.writer = writer;
		this.clock = clock;
	}

	public Summary run() {
		if (!properties.hasApiKey()) {
			throw new IllegalStateException("MFDS_API_KEY가 없습니다. backend/.env.local 또는 환경 변수로 넣어 주세요.");
		}
		Instant startedAt = clock.instant();
		LocalDate today = LocalDate.ofInstant(startedAt, SERVICE_ZONE);
		Path rawDir = rawDirFor(today);

		List<MfdsIngredientItem> items = fetchAll(rawDir);
		IngredientDictionary dictionary = IngredientDictionary.from(items);
		dictionary.ambiguousAliases().forEach(a ->
				log.info("겹치는 별칭이라 등록하지 않음: {} → {}", a.normalizedAlias(), a.standardNames()));

		IngredientDictionaryWriter.Result result = writer.write(dictionary, SOURCE, today, startedAt);
		Summary summary = new Summary(items.size(), dictionary.skippedItems(), dictionary.ambiguousAliases().size(),
				result);
		log.info("성분 사전 반영 완료: 원천 {}건(건너뜀 {}), 성분 반영 {} / 수동 수정 유지 {}, 별칭 추가 {} / 기존 유지 {}, 겹친 별칭 {}",
				summary.fetched(), summary.skipped(), result.ingredientsUpserted(), result.ingredientsKeptManual(),
				result.aliasesInserted(), result.aliasesAlreadyPresent(), summary.ambiguousAliases());
		return summary;
	}

	private List<MfdsIngredientItem> fetchAll(Path rawDir) {
		List<MfdsIngredientItem> items = new ArrayList<>();
		MfdsIngredientClient.Page first = client.fetch(1);
		saveRaw(rawDir, first);
		items.addAll(first.items());

		int totalPages = Math.ceilDiv(first.totalCount(), properties.pageSize());
		log.info("성분 API 전체 {}건, {}페이지", first.totalCount(), totalPages);
		for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
			MfdsIngredientClient.sleep(properties.requestInterval());
			MfdsIngredientClient.Page page = client.fetch(pageNo);
			saveRaw(rawDir, page);
			items.addAll(page.items());
			if (pageNo % 10 == 0) {
				log.info("성분 API {}/{}페이지 수신", pageNo, totalPages);
			}
		}
		if (items.size() != first.totalCount()) {
			// 수집 중 원천이 바뀌면 건수가 어긋날 수 있다. 일부만 반영되지 않도록 중단한다
			throw new IllegalStateException(
					"받은 건수(" + items.size() + ")가 전체 건수(" + first.totalCount() + ")와 다릅니다. 다시 실행해 주세요.");
		}
		return items;
	}

	private Path rawDirFor(LocalDate today) {
		if (properties.rawDir() == null || properties.rawDir().isBlank()) {
			return null;
		}
		return Path.of(properties.rawDir(), "mfds-ingredients", today.toString());
	}

	private static void saveRaw(Path rawDir, MfdsIngredientClient.Page page) {
		if (rawDir == null) {
			return;
		}
		try {
			Files.createDirectories(rawDir);
			Files.writeString(rawDir.resolve("page-%03d.json".formatted(page.pageNo())), page.rawJson(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("원본 응답 저장 실패: " + rawDir, e);
		}
	}
}
