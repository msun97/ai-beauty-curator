package com.aibeauty.api.ingredient.importer;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * 성분 사전 적재. {@code import} 프로필에서만 등록되고, 실행이 끝나면 애플리케이션을 종료한다.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,import
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@Profile("import")
@EnableConfigurationProperties(MfdsIngredientProperties.class)
class IngredientImportConfig {

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
	ApplicationRunner mfdsIngredientImportRunner(MfdsIngredientImporter importer,
			ConfigurableApplicationContext context) {
		return args -> {
			int exitCode = 0;
			try {
				importer.run();
			} catch (RuntimeException e) {
				log.error("성분 사전 적재 실패: {}", e.getMessage(), e);
				exitCode = 1;
			}
			int code = exitCode;
			System.exit(SpringApplication.exit(context, () -> code));
		};
	}
}
