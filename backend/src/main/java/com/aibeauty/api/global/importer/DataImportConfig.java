package com.aibeauty.api.global.importer;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.aibeauty.api.ingredient.importer.CuratedAliasImporter;
import com.aibeauty.api.ingredient.importer.IngredientDictionaryWriter;
import com.aibeauty.api.ingredient.importer.MfdsIngredientClient;
import com.aibeauty.api.ingredient.importer.MfdsIngredientImporter;
import com.aibeauty.api.ingredient.importer.MfdsIngredientProperties;
import com.aibeauty.api.integrity.DataIntegrityChecker;
import com.aibeauty.api.integrity.IntegrityReport;
import com.aibeauty.api.product.importer.ProductSeedImporter;

import tools.jackson.databind.json.JsonMapper;

/**
 * 데이터 적재. {@code import} 프로필에서만 등록되고, 실행이 끝나면 애플리케이션을 종료한다.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,import
 * # 일부 작업만 실행 (예: 정합성 검사만)
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,import -Dspring-boot.run.arguments=--app.import.jobs=data-check
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@Profile("import")
@EnableConfigurationProperties(MfdsIngredientProperties.class)
class DataImportConfig {

	/** 실행 순서. 상품 전성분 매칭이 성분 사전과 별칭을 쓰고, 정합성 검사는 적재가 끝난 뒤 실행한다. */
	enum Job {
		MFDS_INGREDIENTS("mfds-ingredients"),
		INGREDIENT_ALIASES("ingredient-aliases"),
		PRODUCTS("products"),
		/** 오류 수준 위반이 있으면 실패(종료 코드 1)로 끝난다 */
		DATA_CHECK("data-check");

		final String key;

		Job(String key) {
			this.key = key;
		}
	}

	private static final Logger log = LoggerFactory.getLogger(DataImportConfig.class);

	@Bean
	MfdsIngredientClient mfdsIngredientClient(MfdsIngredientProperties properties, JsonMapper jsonMapper) {
		return new MfdsIngredientClient(properties, jsonMapper);
	}

	@Bean
	IngredientDictionaryWriter ingredientDictionaryWriter(JdbcTemplate jdbc) {
		return new IngredientDictionaryWriter(jdbc);
	}

	@Bean
	MfdsIngredientImporter mfdsIngredientImporter(MfdsIngredientProperties properties, MfdsIngredientClient client,
			IngredientDictionaryWriter writer, Clock clock) {
		return new MfdsIngredientImporter(properties, client, writer, clock);
	}

	@Bean
	CuratedAliasImporter curatedAliasImporter(JdbcTemplate jdbc) {
		return new CuratedAliasImporter(jdbc, new ClassPathResource("seed/ingredient-aliases.tsv"));
	}

	@Bean
	ProductSeedImporter productSeedImporter(JdbcTemplate jdbc, Clock clock) {
		return new ProductSeedImporter(jdbc, new ClassPathResource("seed/products.tsv"), clock);
	}

	@Bean
	DataIntegrityChecker dataIntegrityChecker(JdbcTemplate jdbc, Clock clock) {
		return new DataIntegrityChecker(jdbc, clock);
	}

	@Bean
	ApplicationRunner dataImportRunner(MfdsIngredientImporter mfdsImporter, CuratedAliasImporter aliasImporter,
			ProductSeedImporter productImporter, DataIntegrityChecker integrityChecker,
			@Value("${app.import.jobs:mfds-ingredients,ingredient-aliases,products,data-check}") List<String> jobs,
			ConfigurableApplicationContext context) {
		return args -> {
			int exitCode = 0;
			try {
				List<String> known = List.of(Job.values()).stream().map(job -> job.key).toList();
				List<String> unknown = jobs.stream().filter(job -> !known.contains(job)).toList();
				if (!unknown.isEmpty()) {
					throw new IllegalArgumentException("알 수 없는 적재 작업: " + unknown + " (가능: " + known + ")");
				}
				for (Job job : Job.values()) {
					if (!jobs.contains(job.key)) {
						continue;
					}
					switch (job) {
						case MFDS_INGREDIENTS -> mfdsImporter.run();
						case INGREDIENT_ALIASES -> aliasImporter.run();
						case PRODUCTS -> productImporter.run();
						case DATA_CHECK -> {
							IntegrityReport report = integrityChecker.check();
							if (report.hasErrors()) {
								throw new IllegalStateException("정합성 검사 오류 " + report.errorCount() + "종");
							}
						}
					}
				}
			} catch (RuntimeException e) {
				log.error("데이터 적재 실패: {}", e.getMessage(), e);
				exitCode = 1;
			}
			int code = exitCode;
			System.exit(SpringApplication.exit(context, () -> code));
		};
	}
}
