package com.example.finance_app.bank.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiErrorResponse {

    private ApiError error;

    @Getter
    @Builder
    public static class ApiError {
        private String code;
        private String message;
        private LocalDateTime timestamp;
    }

    public static ApiErrorResponse of(String code, String message) {
        return ApiErrorResponse.builder()
                .error(ApiError.builder()
                        .code(code)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build())
                .build();
    }
}
