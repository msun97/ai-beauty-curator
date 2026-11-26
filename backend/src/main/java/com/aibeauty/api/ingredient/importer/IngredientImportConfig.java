package com.aibeauty.api.ingredient.importer;

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

import tools.jackson.databind.json.JsonMapper;

/**
 * 성분 사전 적재. {@code import} 프로필에서만 등록되고, 실행이 끝나면 애플리케이션을 종료한다.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,import
 * # 직접 지정 별칭만 다시 반영
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,import -Dspring-boot.run.arguments=--app.import.jobs=ingredient-aliases
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@Profile("import")
@EnableConfigurationProperties(MfdsIngredientProperties.class)
class IngredientImportConfig {

	static final String MFDS_INGREDIENTS = "mfds-ingredients";
	static final String CURATED_ALIASES = "ingredient-aliases";

	private static final Logger log = LoggerFactory.getLogger(IngredientImportConfig.class);

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

	/** 직접 지정 별칭이 자동 별칭보다 우선하도록 식약처 사전 → 직접 지정 별칭 순서로 실행한다. */
	@Bean
	ApplicationRunner ingredientImportRunner(MfdsIngredientImporter mfdsImporter, CuratedAliasImporter aliasImporter,
			@Value("${app.import.jobs:" + MFDS_INGREDIENTS + "," + CURATED_ALIASES + "}") List<String> jobs,
			ConfigurableApplicationContext context) {
		return args -> {
			int exitCode = 0;
			try {
				List<String> unknown = jobs.stream()
						.filter(job -> !job.equals(MFDS_INGREDIENTS) && !job.equals(CURATED_ALIASES))
						.toList();
				if (!unknown.isEmpty()) {
					throw new IllegalArgumentException("알 수 없는 적재 작업: " + unknown);
				}
				if (jobs.contains(MFDS_INGREDIENTS)) {
					mfdsImporter.run();
				}
				if (jobs.contains(CURATED_ALIASES)) {
					aliasImporter.run();
				}
			} catch (RuntimeException e) {
				log.error("성분 사전 적재 실패: {}", e.getMessage(), e);
				exitCode = 1;
			}
			int code = exitCode;
			System.exit(SpringApplication.exit(context, () -> code));
		};
	}
}
