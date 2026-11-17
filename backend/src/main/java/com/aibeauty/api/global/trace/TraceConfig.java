package com.aibeauty.api.global.trace;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.DispatcherType;

@Configuration
public class TraceConfig {

	/** Spring Security 필터 체인(-100)보다 먼저 실행되도록 가장 높은 우선순위로 등록한다. */
	@Bean
	public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
		FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		// 오류 페이지 디스패치(/error)에서도 추적 ID를 이어 쓰기 위해 ERROR도 포함한다
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
		return registration;
	}
}
