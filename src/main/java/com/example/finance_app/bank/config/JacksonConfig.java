package com.example.finance_app.bank.config;

// Jackson 3.x (Spring Boot 4.x) serializes LocalDateTime as ISO-8601 strings
// ("2025-07-02T10:30:00") by default — no ObjectMapper customisation required.
// Spring Boot auto-configures the ObjectMapper bean via JacksonAutoConfiguration;
// that same bean is injected into IdempotencyService and the HTTP message converters.
//
// SerializationFeature.WRITE_DATES_AS_TIMESTAMPS was removed in Jackson 3.x:
// date/time behaviour is now controlled by the JavaTimeModule, which Spring Boot
// auto-registers.  Nothing to configure here.
