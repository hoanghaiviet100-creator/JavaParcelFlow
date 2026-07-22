package com.parcelflow.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final ErrorBody error;
    private final Instant timestamp;
    private final String path;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> failure(String code, String message, List<String> details, String path) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .error(new ErrorBody(code, message, details))
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorBody {
        private final String code;
        private final String message;
        private final List<String> details;
    }
}
