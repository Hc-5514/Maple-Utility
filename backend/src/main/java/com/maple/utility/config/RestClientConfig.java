package com.maple.utility.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient restClient() {
		return RestClient.builder().build();
	}

	@Bean
	public WebClient nexonWebClient() {
		return WebClient.builder().build();
	}
}
