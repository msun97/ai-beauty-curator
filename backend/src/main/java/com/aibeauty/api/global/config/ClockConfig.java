package com.aibeauty.api.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	/** 시간 의존 로직(토큰 만료, 로그인 제한)을 테스트할 수 있도록 Clock을 주입받아 사용한다. */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
