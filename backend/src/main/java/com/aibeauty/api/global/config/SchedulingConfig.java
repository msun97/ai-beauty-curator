package com.aibeauty.api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(@Scheduled)을 켠다. 작업마다 cron 설정에 "-"를 넣으면 그 작업은 꺼진다(테스트 프로필).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
