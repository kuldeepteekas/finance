package com.example.finance_app.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // Override in application.properties to point at a real audit service.
    // In dev this will fail to connect — ExternalAuditService catches all errors
    // and records ExternalCallStatus.FAILED, so the money operation still proceeds.
    @Value("${audit.service.base-url:http://localhost:8081}")
    private String auditServiceBaseUrl;

    @Bean
    public RestClient auditRestClient() {
        return RestClient.builder()
                .baseUrl(auditServiceBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
